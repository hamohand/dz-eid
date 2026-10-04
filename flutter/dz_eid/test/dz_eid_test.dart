import 'package:dz_eid/dz_eid.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

/// Données fictives (aucune donnée réelle), au format renvoyé par le canal Flutter.
Map<Object?, Object?> _sample() => <Object?, Object?>{
      'schemaVersion': '1.1',
      'readAt': '2026-10-04T10:00:00Z',
      'document': <Object?, Object?>{'type': 'ID_CARD', 'number': '123456789', 'dateOfExpiry': '2031-05-20'},
      'holder': <Object?, Object?>{
        'lastNameLatin': 'EXEMPLE',
        'firstNameLatin': 'TEST',
        'lastNameArabic': 'مثال',
        'dateOfBirth': '1985-03-12',
      },
      'mrz': <Object?, Object?>{
        'format': 'TD1',
        'lines': <Object?>['L1', 'L2', 'L3'],
        'checkDigitsValid': true,
      },
      'photo': <Object?, Object?>{'mimeType': 'image/jpeg', 'base64': 'AQID', 'originalMimeType': 'image/jp2'},
      'verification': <Object?, Object?>{
        'accessMethod': 'BAC',
        'dataGroupsPresent': <Object?>[1, 2, 7, 11, 12, 15],
        'dataGroupsRead': <Object?>[1, 2, 7, 11, 12, 15],
        'passiveAuthentication': <Object?, Object?>{
          'dataIntegrity': 'VALID',
          'signature': 'VALID',
          'certificateChain': 'NOT_CHECKED',
          'details': <Object?>['d1'],
        },
        'activeAuthentication': <Object?, Object?>{'result': 'VALID', 'algorithm': 'RSA-1024 ISO 9796-2 / SHA-1'},
      },
      'warnings': <Object?>['w1'],
    };

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const channel = MethodChannel('com.muhend.dzeid/dz_eid');
  final messenger = TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;

  tearDown(() => messenger.setMockMethodCallHandler(channel, null));

  test('décodage du contrat IdentityRecord 1.1', () {
    final r = IdentityRecord.fromJson(_sample());
    expect(r.schemaVersion, '1.1');
    expect(r.document.number, '123456789');
    expect(r.holder.lastNameArabic, 'مثال');
    expect(r.mrz!.lines, ['L1', 'L2', 'L3']);
    expect(r.photo!.isDisplayable, isTrue);
    expect(r.photo!.bytes, [1, 2, 3]);
    expect(r.photo!.originalMimeType, 'image/jp2');
    expect(r.signatureImage, isNull);
    expect(r.verification.dataGroupsRead, [1, 2, 7, 11, 12, 15]);
    expect(r.verification.passiveAuthentication.dataIntegrity, CheckStatus.valid);
    expect(r.verification.passiveAuthentication.certificateChain, CheckStatus.notChecked);
    expect(r.verification.activeAuthentication.result, CheckStatus.valid);
    expect(r.warnings, ['w1']);
    expect(r.toPrettyJson(), contains('"accessMethod": "BAC"'));
  });

  test('champs absents : valeurs neutres', () {
    final r = IdentityRecord.fromJson(<Object?, Object?>{'schemaVersion': '1.1'});
    expect(r.holder.lastNameLatin, isNull);
    expect(r.photo, isNull);
    expect(r.verification.activeAuthentication.result, CheckStatus.notChecked);
    expect(r.raw, isEmpty);
  });

  test('AccessKey.fromDates : format AAMMJJ et toString masqué', () {
    final k = AccessKey.fromDates(
        documentNumber: '123456789', dateOfBirth: DateTime(1985, 3, 12), dateOfExpiry: DateTime(2031, 5, 20));
    expect(k.dateOfBirth, '850312');
    expect(k.dateOfExpiry, '310520');
    expect(k.toString(), isNot(contains('123456789')));
  });

  test('readCard : arguments transmis et résultat décodé', () async {
    MethodCall? received;
    messenger.setMockMethodCallHandler(channel, (call) async {
      received = call;
      return _sample();
    });
    final r = await DzEid.readCard(
      accessKey: const AccessKey(documentNumber: '123456789', dateOfBirth: '850312', dateOfExpiry: '310520'),
      options: const ReadOptions(readPhoto: false, timeout: Duration(seconds: 30)),
    );
    expect(received!.method, 'readCard');
    final args = received!.arguments as Map;
    expect(args['documentNumber'], '123456789');
    expect(args['readPhoto'], false);
    expect(args['timeoutSeconds'], 30);
    expect(r.holder.firstNameLatin, 'TEST');
  });

  test('erreur native convertie en DzEidException', () async {
    messenger.setMockMethodCallHandler(channel, (call) async {
      throw PlatformException(code: 'ACCESS_DENIED', message: 'Accès refusé');
    });
    expect(
      () => DzEid.readCard(accessKey: const AccessKey.fromMrz('X')),
      throwsA(isA<DzEidException>().having((e) => e.code, 'code', 'ACCESS_DENIED')),
    );
  });

  test('statut NFC', () async {
    messenger.setMockMethodCallHandler(channel, (call) async => 'DISABLED');
    expect(await DzEid.getNfcStatus(), NfcStatus.disabled);
  });
}
