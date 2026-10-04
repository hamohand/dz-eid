# Contrat `IdentityRecord` — version 1.1

Format de sortie unique de dz-eid, identique quelle que soit la source (agent bureau, Android, décodage hors ligne).

- **Dates** : ISO 8601 (`AAAA-MM-JJ`), horodatages en UTC.
- **Champs absents** : omis du JSON. Ne jamais supposer qu'un champ est présent.
- **Compatibilité** : les versions `1.x` n'ajoutent que des champs. Ignorez les champs inconnus.
- **Historique** : 1.1 ajoute `signatureImage` (DG7) et `verification.activeAuthentication` (anti-clonage).

## Exemple (identité fictive)

```json
{
  "schemaVersion": "1.1",
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
  "signatureImage": { "mimeType": "image/jpeg", "base64": "/9j/4AAQ…" },
  "verification": {
    "accessMethod": "BAC",
    "dataGroupsPresent": [1, 2, 7, 11, 12, 15],
    "dataGroupsRead": [1, 2, 7, 11, 12, 15],
    "passiveAuthentication": {
      "dataIntegrity": "VALID",
      "signature": "VALID",
      "certificateChain": "NOT_CHECKED",
      "digestAlgorithm": "SHA-256",
      "documentSigner": { "subject": "CN=…", "issuer": "CN=…", "serialNumber": "…", "notBefore": "…", "notAfter": "…" },
      "summary": "Données intactes et signature valide. Chaîne CSCA non vérifiée (certificat racine non installé).",
      "details": ["Aucun certificat CSCA installé : chaîne de confiance non vérifiée."]
    },
    "activeAuthentication": {
      "result": "VALID",
      "algorithm": "RSA-1024 ISO 9796-2 / SHA-1",
      "summary": "Puce authentique : elle a prouvé détenir sa clé secrète (la carte n'est pas un clone)."
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

### `verification.activeAuthentication` (anti-clonage, depuis 1.1)
La puce signe un défi aléatoire avec une clé privée non extractible ; la réponse est vérifiée avec la clé publique du DG15.

| `result` | Signification |
|---|---|
| `VALID` | **Puce authentique** : ce n'est pas une copie. Valable seulement si `dataIntegrity` est `VALID` (sinon le résultat est rétrogradé en `NOT_CHECKED`). |
| `INVALID` | Réponse incorrecte ou refusée alors que la carte annonce le contrôle : **clone possible**, refuser le document |
| `NOT_CHECKED` | Carte sans DG15, contrôle désactivé, ou décodage hors ligne (le défi ne peut pas être rejoué) |

### `signatureImage` (depuis 1.1)
Image de la signature manuscrite du titulaire (DG7, tag `5F43`), même structure que `photo`. Présente seulement si la carte possède un DG7 et que `readSignature` n'est pas désactivé.

### Constats sur les CNIe algériennes (2017-2019)
- Accès **BAC** uniquement (pas d'EF.CardAccess, donc pas de PACE).
- DG présents : 1, 2, 7, 11, 12, 15 (pas de DG14 : pas de Chip Authentication).
- Active Authentication : RSA-1024, ISO 9796-2, SHA-1.

### `raw` (si `includeRaw: true`)
Octets bruts en hexadécimal : `dg1`, `dg2`, `dg11`, `dg12`, `dg13`, `sod`. Utile pour l'archivage probatoire ou le diagnostic.

> [!CAUTION]
> Ces données sont personnelles (loi 18-07). Ne les conservez que si c'est nécessaire et justifié.
