import 'package:flutter/services.dart';

import 'identity_record.dart';

/// Erreur dz-eid : [code] stable (à tester dans le code) et [message] en français, affichable tel quel.
///
/// Codes : `INVALID_INPUT`, `NO_CARD`, `ACCESS_DENIED`, `CARD_LOST`, `NOT_EMRTD`, `READ_ERROR`, `BUSY`,
/// `INTERNAL`, `NFC_UNAVAILABLE`, `NFC_DISABLED`, `CANCELLED`, `CAMERA_PERMISSION_DENIED`,
/// `CAMERA_UNAVAILABLE`, `NO_ACTIVITY`, `UNSUPPORTED_PLATFORM`.
class DzEidException implements Exception {
  const DzEidException(this.code, this.message);

  final String code;
  final String message;

  @override
  String toString() => 'DzEidException($code): $message';
}

/// État du NFC du téléphone.
enum NfcStatus {
  /// NFC présent et activé.
  available,

  /// NFC présent mais désactivé dans les paramètres.
  disabled,

  /// Pas de NFC sur ce téléphone.
  unavailable,
}

/// Clé d'accès à la puce, issue de la MRZ imprimée au dos de la carte.
///
/// Ne jamais l'enregistrer ni la journaliser : elle permet de lire la puce.
class AccessKey {
  /// Saisie manuelle : numéro de document et dates au format `AAMMJJ` ou `AAAA-MM-JJ`.
  const AccessKey({required this.documentNumber, required this.dateOfBirth, required this.dateOfExpiry}) : mrz = null;

  /// Texte complet de la MRZ (3 lignes pour la carte d'identité), par exemple issu de [DzEid.scanMrz].
  const AccessKey.fromMrz(String this.mrz)
      : documentNumber = null,
        dateOfBirth = null,
        dateOfExpiry = null;

  /// Saisie manuelle avec des dates.
  factory AccessKey.fromDates({required String documentNumber, required DateTime dateOfBirth, required DateTime dateOfExpiry}) =>
      AccessKey(documentNumber: documentNumber, dateOfBirth: _yymmdd(dateOfBirth), dateOfExpiry: _yymmdd(dateOfExpiry));

  final String? mrz;
  final String? documentNumber;
  final String? dateOfBirth;
  final String? dateOfExpiry;

  Map<String, Object?> _toArguments() => mrz != null
      ? {'mrz': mrz}
      : {'documentNumber': documentNumber, 'dateOfBirth': dateOfBirth, 'dateOfExpiry': dateOfExpiry};

  static String _yymmdd(DateTime d) =>
      '${(d.year % 100).toString().padLeft(2, '0')}${d.month.toString().padLeft(2, '0')}${d.day.toString().padLeft(2, '0')}';

  @override
  String toString() => 'AccessKey(***)'; // jamais la clé en clair
}

/// MRZ reconnue par la caméra (chiffres de contrôle vérifiés).
class MrzScanResult {
  const MrzScanResult({required this.mrz, required this.documentNumber, required this.dateOfBirth, required this.dateOfExpiry});

  /// Lignes de la MRZ séparées par `\n`.
  final String mrz;
  final String documentNumber;

  /// `AAMMJJ`.
  final String dateOfBirth;

  /// `AAMMJJ`.
  final String dateOfExpiry;

  AccessKey toAccessKey() => AccessKey.fromMrz(mrz);
}

/// Options de lecture.
class ReadOptions {
  const ReadOptions({
    this.readPhoto = true,
    this.readSignature = true,
    this.activeAuthentication = true,
    this.includeRaw = false,
    this.timeout = const Duration(seconds: 60),
  });

  /// Lire la photo (DG2, environ 1 s de plus).
  final bool readPhoto;

  /// Lire l'image de la signature manuscrite (DG7).
  final bool readSignature;

  /// Vérifier que la puce n'est pas un clone (Active Authentication).
  final bool activeAuthentication;

  /// Inclure les octets bruts des DG (diagnostic).
  final bool includeRaw;

  /// Délai d'attente de la carte.
  final Duration timeout;
}

/// Étape d'une lecture en cours.
class ReadProgress {
  const ReadProgress({required this.waitingForCard, required this.step, required this.percent, required this.message});

  factory ReadProgress._fromEvent(Map<Object?, Object?> e) => ReadProgress(
        waitingForCard: e['type'] == 'waiting',
        step: e['step'] as String? ?? 'WAITING',
        percent: (e['percent'] as num?)?.toInt() ?? 0,
        message: e['message'] as String? ?? '',
      );

  /// Vrai tant que la carte n'est pas détectée (ou après une perte de connexion).
  final bool waitingForCard;

  /// `WAITING`, `CONNECTING`, `AUTHENTICATING`, `READING`, `VERIFYING`, `DONE`.
  final String step;

  /// Avancement estimé 0-100.
  final int percent;

  /// Message en français, affichable tel quel.
  final String message;
}

/// Lecture des cartes d'identité électroniques algériennes (CNIe) par NFC.
///
/// ```dart
/// final scan = await DzEid.scanMrz();           // caméra, ou saisie manuelle
/// if (scan == null) return;                       // abandon
/// final id = await DzEid.readCard(
///   accessKey: scan.toAccessKey(),
///   onProgress: (p) => print('${p.percent} % ${p.message}'),
/// );
/// print(id.holder.lastNameLatin);
/// ```
class DzEid {
  DzEid._();

  static const MethodChannel _channel = MethodChannel('com.muhend.dzeid/dz_eid');
  static const EventChannel _events = EventChannel('com.muhend.dzeid/dz_eid/events');

  /// État du NFC du téléphone.
  static Future<NfcStatus> getNfcStatus() async {
    final status = await _invoke<String>('getNfcStatus');
    switch (status) {
      case 'AVAILABLE':
        return NfcStatus.available;
      case 'DISABLED':
        return NfcStatus.disabled;
      default:
        return NfcStatus.unavailable;
    }
  }

  /// Ouvre les paramètres NFC du téléphone.
  static Future<bool> openNfcSettings() async => await _invoke<bool>('openNfcSettings') ?? false;

  /// Lit la MRZ au dos de la carte avec la caméra (hors ligne, rien n'est enregistré).
  ///
  /// Renvoie `null` si l'utilisateur abandonne.
  static Future<MrzScanResult?> scanMrz() async {
    final r = await _invoke<Map<Object?, Object?>>('scanMrz');
    if (r == null) return null;
    return MrzScanResult(
      mrz: r['mrz'] as String,
      documentNumber: r['documentNumber'] as String,
      dateOfBirth: r['dateOfBirth'] as String,
      dateOfExpiry: r['dateOfExpiry'] as String,
    );
  }

  /// Attend la carte au dos du téléphone, puis la lit et vérifie son authenticité.
  ///
  /// Lève [DzEidException] en cas d'échec (`NO_CARD`, `ACCESS_DENIED`, `CANCELLED`…).
  static Future<IdentityRecord> readCard({
    required AccessKey accessKey,
    ReadOptions options = const ReadOptions(),
    void Function(ReadProgress progress)? onProgress,
  }) async {
    final subscription = onProgress == null
        ? null
        : _events.receiveBroadcastStream().listen(
              (e) => onProgress(ReadProgress._fromEvent(e as Map<Object?, Object?>)),
              onError: (Object _) {},
            );
    try {
      final r = await _invoke<Map<Object?, Object?>>('readCard', {
        ...accessKey._toArguments(),
        'readPhoto': options.readPhoto,
        'readSignature': options.readSignature,
        'activeAuthentication': options.activeAuthentication,
        'includeRaw': options.includeRaw,
        'timeoutSeconds': options.timeout.inSeconds,
      });
      if (r == null) {
        throw const DzEidException('INTERNAL', 'Réponse vide du lecteur.');
      }
      return IdentityRecord.fromJson(r);
    } finally {
      await subscription?.cancel();
    }
  }

  /// Annule la lecture en cours : [readCard] se termine avec le code `CANCELLED`.
  static Future<void> cancel() => _invoke<void>('cancel');

  static Future<T?> _invoke<T>(String method, [Object? arguments]) async {
    try {
      return await _channel.invokeMethod<T>(method, arguments);
    } on PlatformException catch (e) {
      throw DzEidException(e.code, e.message ?? e.code);
    } on MissingPluginException {
      throw const DzEidException('UNSUPPORTED_PLATFORM', "dz_eid n'est disponible que sur Android pour le moment.");
    }
  }
}
