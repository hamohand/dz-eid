/// Modèles du contrat IdentityRecord (schéma 1.1), identiques au JSON de l'agent bureau dz-eid.
library;

import 'dart:convert';
import 'dart:typed_data';

/// Statut d'un contrôle de sécurité.
enum CheckStatus {
  /// Contrôle réussi.
  valid,

  /// Contrôle échoué : document altéré, cloné ou non authentique.
  invalid,

  /// Contrôle non effectué (donnée absente, certificat racine non installé…).
  notChecked;

  static CheckStatus parse(Object? value) {
    switch (value) {
      case 'VALID':
        return CheckStatus.valid;
      case 'INVALID':
        return CheckStatus.invalid;
      default:
        return CheckStatus.notChecked;
    }
  }
}

/// Résultat complet d'une lecture de carte.
class IdentityRecord {
  IdentityRecord._(this.json)
      : schemaVersion = json['schemaVersion'] as String?,
        readAt = json['readAt'] as String?,
        document = DocumentInfo._(_map(json['document'])),
        holder = HolderInfo._(_map(json['holder'])),
        mrz = json['mrz'] == null ? null : MrzSummary._(_map(json['mrz'])),
        photo = json['photo'] == null ? null : EidImage._(_map(json['photo'])),
        signatureImage = json['signatureImage'] == null ? null : EidImage._(_map(json['signatureImage'])),
        verification = Verification._(_map(json['verification'])),
        warnings = _strings(json['warnings']),
        raw = _map(json['raw']).map((k, v) => MapEntry(k, v as String));

  /// Construit le modèle à partir de la structure renvoyée par le canal Flutter ou du JSON de l'agent.
  factory IdentityRecord.fromJson(Map<Object?, Object?> json) => IdentityRecord._(_deepConvert(json) as Map<String, dynamic>);

  /// Structure brute (clés et valeurs JSON), par exemple pour un affichage ou un envoi au serveur.
  final Map<String, dynamic> json;

  final String? schemaVersion;

  /// Date et heure de lecture (ISO 8601, UTC).
  final String? readAt;
  final DocumentInfo document;
  final HolderInfo holder;

  /// MRZ enregistrée dans la puce (DG1).
  final MrzSummary? mrz;

  /// Photo du titulaire (DG2), convertie en JPEG.
  final EidImage? photo;

  /// Signature manuscrite (DG7), convertie en PNG.
  final EidImage? signatureImage;
  final Verification verification;
  final List<String> warnings;

  /// Octets bruts des DG en hexadécimal (seulement si `includeRaw`).
  final Map<String, String> raw;

  /// JSON indenté, identique à la sortie de l'agent bureau.
  String toPrettyJson() => const JsonEncoder.withIndent('  ').convert(json);
}

/// Informations sur le document.
class DocumentInfo {
  DocumentInfo._(Map<String, dynamic> m)
      : type = m['type'] as String?,
        code = m['code'] as String?,
        number = m['number'] as String?,
        issuingState = m['issuingState'] as String?,
        dateOfIssue = m['dateOfIssue'] as String?,
        dateOfExpiry = m['dateOfExpiry'] as String?,
        issuingAuthorityLatin = m['issuingAuthorityLatin'] as String?,
        issuingAuthorityArabic = m['issuingAuthorityArabic'] as String?,
        nameLatin = m['nameLatin'] as String?,
        nameArabic = m['nameArabic'] as String?;

  final String? type;
  final String? code;
  final String? number;
  final String? issuingState;

  /// Dates au format ISO 8601 (`AAAA-MM-JJ`).
  final String? dateOfIssue;
  final String? dateOfExpiry;
  final String? issuingAuthorityLatin;
  final String? issuingAuthorityArabic;
  final String? nameLatin;
  final String? nameArabic;
}

/// Informations sur le titulaire (latin et arabe).
class HolderInfo {
  HolderInfo._(Map<String, dynamic> m)
      : nin = m['nin'] as String?,
        lastNameLatin = m['lastNameLatin'] as String?,
        firstNameLatin = m['firstNameLatin'] as String?,
        lastNameArabic = m['lastNameArabic'] as String?,
        firstNameArabic = m['firstNameArabic'] as String?,
        sex = m['sex'] as String?,
        sexArabic = m['sexArabic'] as String?,
        dateOfBirth = m['dateOfBirth'] as String?,
        placeOfBirthLatin = m['placeOfBirthLatin'] as String?,
        placeOfBirthArabic = m['placeOfBirthArabic'] as String?,
        nationality = m['nationality'] as String?,
        bloodGroup = m['bloodGroup'] as String?,
        address = m['address'] as String?;

  /// Numéro d'identification national (NIN).
  final String? nin;
  final String? lastNameLatin;
  final String? firstNameLatin;
  final String? lastNameArabic;
  final String? firstNameArabic;
  final String? sex;
  final String? sexArabic;
  final String? dateOfBirth;
  final String? placeOfBirthLatin;
  final String? placeOfBirthArabic;
  final String? nationality;
  final String? bloodGroup;
  final String? address;
}

/// Résumé de la MRZ lue dans la puce.
class MrzSummary {
  MrzSummary._(Map<String, dynamic> m)
      : format = m['format'] as String?,
        lines = _strings(m['lines']),
        checkDigitsValid = m['checkDigitsValid'] as bool? ?? false;

  final String? format;
  final List<String> lines;
  final bool checkDigitsValid;
}

/// Image enregistrée sur la puce (photo ou signature).
class EidImage {
  EidImage._(Map<String, dynamic> m)
      : mimeType = m['mimeType'] as String? ?? 'application/octet-stream',
        base64 = m['base64'] as String? ?? '',
        originalMimeType = m['originalMimeType'] as String?;

  final String mimeType;
  final String base64;

  /// Format d'origine sur la puce si l'image a été convertie (souvent `image/jp2`).
  final String? originalMimeType;

  /// Vrai si l'image peut être affichée par Flutter (`Image.memory`).
  bool get isDisplayable => mimeType == 'image/jpeg' || mimeType == 'image/png';

  Uint8List get bytes => base64Decode(base64);
}

/// Résultat des contrôles de sécurité.
class Verification {
  Verification._(Map<String, dynamic> m)
      : accessMethod = m['accessMethod'] as String?,
        dataGroupsPresent = _ints(m['dataGroupsPresent']),
        dataGroupsRead = _ints(m['dataGroupsRead']),
        passiveAuthentication = PassiveAuthentication._(_map(m['passiveAuthentication'])),
        activeAuthentication = ActiveAuthentication._(_map(m['activeAuthentication']));

  /// `BAC` ou `PACE`.
  final String? accessMethod;
  final List<int> dataGroupsPresent;
  final List<int> dataGroupsRead;
  final PassiveAuthentication passiveAuthentication;
  final ActiveAuthentication activeAuthentication;
}

/// Passive Authentication : les données lues sont-elles intactes et signées par l'État émetteur ?
class PassiveAuthentication {
  PassiveAuthentication._(Map<String, dynamic> m)
      : dataIntegrity = CheckStatus.parse(m['dataIntegrity']),
        signature = CheckStatus.parse(m['signature']),
        certificateChain = CheckStatus.parse(m['certificateChain']),
        digestAlgorithm = m['digestAlgorithm'] as String?,
        documentSigner = m['documentSigner'] == null ? null : SignerInfo._(_map(m['documentSigner'])),
        summary = m['summary'] as String?,
        details = _strings(m['details']);

  /// Empreintes des données comparées à celles signées.
  final CheckStatus dataIntegrity;

  /// Signature électronique du document.
  final CheckStatus signature;

  /// Rattachement au certificat racine (CSCA) de l'État émetteur.
  final CheckStatus certificateChain;
  final String? digestAlgorithm;
  final SignerInfo? documentSigner;
  final String? summary;
  final List<String> details;
}

/// Certificat signataire du document.
class SignerInfo {
  SignerInfo._(Map<String, dynamic> m)
      : subject = m['subject'] as String?,
        issuer = m['issuer'] as String?,
        serialNumber = m['serialNumber'] as String?,
        notBefore = m['notBefore'] as String?,
        notAfter = m['notAfter'] as String?;

  final String? subject;
  final String? issuer;
  final String? serialNumber;
  final String? notBefore;
  final String? notAfter;
}

/// Active Authentication (anti-clonage) : la puce prouve qu'elle est l'originale.
class ActiveAuthentication {
  ActiveAuthentication._(Map<String, dynamic> m)
      : result = CheckStatus.parse(m['result']),
        algorithm = m['algorithm'] as String?,
        summary = m['summary'] as String?;

  final CheckStatus result;
  final String? algorithm;
  final String? summary;
}

Map<String, dynamic> _map(Object? v) => v is Map ? v.cast<String, dynamic>() : <String, dynamic>{};

List<String> _strings(Object? v) => v is List ? v.map((e) => e.toString()).toList(growable: false) : const [];

List<int> _ints(Object? v) => v is List ? v.map((e) => (e as num).toInt()).toList(growable: false) : const [];

/// Le canal Flutter renvoie des `Map<Object?, Object?>` imbriquées : conversion en types JSON.
Object? _deepConvert(Object? v) {
  if (v is Map) {
    return <String, dynamic>{for (final e in v.entries) e.key.toString(): _deepConvert(e.value)};
  }
  if (v is List) {
    return v.map(_deepConvert).toList();
  }
  return v;
}
