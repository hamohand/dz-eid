import 'dart:typed_data';
import 'dart:convert';
import 'dart:math';
import 'package:dz_eid/dz_eid.dart';
import 'package:http/http.dart' as http;
import 'package:webcrypto/webcrypto.dart';

class WebRelayService {
  
  static Future<void> sendToWeb(String qrPayload, IdentityRecord record) async {
    // 1. Parse QR payload
    final Map<String, dynamic> data = jsonDecode(qrPayload);
    final String relayUrl = data['u'];
    final String sessionId = data['s'];
    final String webPubKeyB64 = data['k'];

    // 2. Import Web Public Key (Raw format)
    final webPubKeyBytes = base64Decode(webPubKeyB64);
    final webPublicKey = await EcdhPublicKey.importRawKey(
      webPubKeyBytes,
      EllipticCurve.p256,
    );

    // 3. Generate Mobile Ephemeral Key Pair
    final mobKeyPair = await EcdhPrivateKey.generateKey(EllipticCurve.p256);
    
    // Export mobile public key to raw
    final mobPubKeyBytes = await mobKeyPair.publicKey.exportRawKey();
    final mobPubKeyB64 = base64Encode(mobPubKeyBytes);

    // 4. Derive Shared Secret (Bits)
    final sharedSecretBits = await mobKeyPair.privateKey.deriveBits(
      256,
      webPublicKey,
    );
    
    // Hash the shared secret with SHA-256 to get AES key
    final hash = await Hash.sha256.digestBytes(sharedSecretBits);
    
    // Import AES-GCM key
    final aesKey = await AesGcmSecretKey.importRawKey(hash);

    // 5. Encrypt IdentityRecord
    final recordJson = record.json;
    final recordBytes = utf8.encode(jsonEncode(recordJson));
    
    // Generate random 12-byte IV (nonce)
    final random = Random.secure();
    final iv = Uint8List.fromList(List<int>.generate(12, (_) => random.nextInt(256)));

    final ciphertextBytes = await aesKey.encryptBytes(recordBytes, iv);

    final ivB64 = base64Encode(iv);
    final ciphertextB64 = base64Encode(ciphertextBytes);

    // 6. Send via HTTP POST
    final response = await http.post(
      Uri.parse('$relayUrl/api/send_card_data'),
      headers: {'Content-Type': 'application/json'},
      body: jsonEncode({
        'sessionId': sessionId,
        'encryptedData': {
          'mobPubKeyB64': mobPubKeyB64,
          'ivB64': ivB64,
          'ciphertextB64': ciphertextB64,
        }
      }),
    );

    if (response.statusCode != 200) {
      throw Exception('Erreur de transmission au relais web: ${response.statusCode}');
    }
  }
}

