# Contrat `IdentityRecord` — version 1.0

Format de sortie unique de dz-eid, identique quelle que soit la source (agent bureau, Android, décodage hors ligne).

- **Dates** : ISO 8601 (`AAAA-MM-JJ`), horodatages en UTC.
- **Champs absents** : omis du JSON. Ne jamais supposer qu'un champ est présent.
- **Compatibilité** : les versions `1.x` n'ajoutent que des champs. Ignorez les champs inconnus.

## Exemple (identité fictive)

```json
{
  "schemaVersion": "1.0",
  "readAt": "2026-10-04T01:51:24.880Z",
  "document": {
    "type": "ID_CARD",
    "code": "ID",
    "number": "123456789",
    "issuingState": "DZA",
    "dateOfIssue": "2021-05-21",
    "dateOfExpiry": "2031-05-20",
    "issuingAuthorityLatin": "ALGER_COMMUNE BAB EL OUED-ALGER",
    "issuingAuthorityArabic": "بلدية باب الواد-الجزائر",
    "nameLatin": "CARTE D'IDENTITE NATIONALE",
    "nameArabic": "بطاقة التعريف الوطنية"
  },
  "holder": {
    "nin": "119850312000123400",
    "lastNameLatin": "BENALI",
    "firstNameLatin": "AMINA",
    "lastNameArabic": "بن علي",
    "firstNameArabic": "أمينة",
    "sex": "F",
    "sexArabic": "أنثى",
    "dateOfBirth": "1985-03-12",
    "placeOfBirthLatin": "TIPAZA",
    "placeOfBirthArabic": "تيبازة",
    "nationality": "DZA",
    "bloodGroup": "B-"
  },
  "mrz": { "format": "TD1", "lines": ["IDDZA…", "8503127F…", "BENALI<<AMINA<<<…"], "checkDigitsValid": true },
  "photo": { "mimeType": "image/jpeg", "base64": "/9j/4AAQ…", "originalMimeType": "image/jp2" },
  "verification": {
    "accessMethod": "PACE",
    "dataGroupsPresent": [1, 2, 11, 12, 14],
    "dataGroupsRead": [1, 2, 11, 12],
    "passiveAuthentication": {
      "dataIntegrity": "VALID",
      "signature": "VALID",
      "certificateChain": "NOT_CHECKED",
      "digestAlgorithm": "SHA-256",
      "documentSigner": { "subject": "CN=…", "issuer": "CN=…", "serialNumber": "…", "notBefore": "…", "notAfter": "…" },
      "summary": "Données intactes et signature valide. Chaîne CSCA non vérifiée (certificat racine non installé).",
      "details": ["Aucun certificat CSCA installé : chaîne de confiance non vérifiée."]
    }
  },
  "warnings": []
}
```

## Référence des champs

### `document`
| Champ | Source | Description |
|---|---|---|
| `type` | MRZ | `ID_CARD`, `PASSPORT`, `OTHER` |
| `code` | MRZ | Code du document (`ID`, `P`…) |
| `number` | MRZ | Numéro du document |
| `issuingState` | MRZ | État émetteur (ISO 3166 alpha-3) |
| `dateOfIssue` | DG12 `5F26` | Date de délivrance |
| `dateOfExpiry` | DG12 `5F1B`, sinon MRZ | Date d'expiration. En cas de désaccord, la MRZ fait foi et un avertissement est ajouté. |
| `issuingAuthorityLatin` / `Arabic` | DG12 `5F19` | Autorité de délivrance (commune, consulat…) |
| `nameLatin` / `Arabic` | DG12 `5F1D` | Intitulé du document |

### `holder`
| Champ | Source | Description |
|---|---|---|
| `nin` | DG11 `5F10` | Numéro d'identification national (18 chiffres) |
| `lastNameLatin` / `firstNameLatin` | DG11, sinon MRZ | Noms complets. La MRZ peut être tronquée. |
| `lastNameArabic` / `firstNameArabic` | DG11 `5F0E` / `5F0F` | Décodés depuis l'ISO-8859-6 |
| `sex` | MRZ | `M`, `F` ou `X` |
| `sexArabic` | DG11 `5F42` | Profil algérien |
| `dateOfBirth` | DG11 `5F2B`, sinon MRZ | L'année sur 4 chiffres lève l'ambiguïté du siècle |
| `placeOfBirthLatin` / `Arabic` | DG11 `5F11` | Lieu de naissance |
| `nationality` | MRZ | Nationalité |
| `bloodGroup` | DG11 `5F42` | Profil algérien (`A+`, `O-`…) |
| `address` | DG11 `5F42` | Seulement si le tag contient une vraie adresse (documents non algériens) |

### `verification.passiveAuthentication`
| Statut | Signification |
|---|---|
| `dataIntegrity: VALID` | Les données lues sont identiques à celles signées par l'État |
| `signature: VALID` | La liste signée (SOD) a bien été signée par le certificat DS qu'elle contient |
| `certificateChain: VALID` | Le certificat DS est émis par une autorité racine (CSCA) de confiance installée |
| `INVALID` (un seul suffit) | **Fraude ou altération** : refuser le document |
| `NOT_CHECKED` | Contrôle impossible (SOD absent, CSCA non installé…) |

### `raw` (si `includeRaw: true`)
Octets bruts en hexadécimal : `dg1`, `dg2`, `dg11`, `dg12`, `dg13`, `sod`. Utile pour l'archivage probatoire ou le diagnostic.

> [!CAUTION]
> Ces données sont personnelles (loi 18-07). Ne les conservez que si c'est nécessaire et justifié.
