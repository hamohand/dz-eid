# Intégration mobile : plugin Flutter `dz_eid` (Android)

Le plugin lit la CNIe algérienne avec le NFC du téléphone. Il scanne aussi la MRZ avec la caméra.
Tout le traitement se fait sur l'appareil. Le plugin ne demande pas la permission INTERNET.

- Android 8.0 (API 26) minimum. Le téléphone doit avoir le NFC pour lire la puce. Sans NFC, la saisie manuelle reste possible.
- La lecture de la MRZ utilise ML Kit avec un modèle **embarqué**. Elle fonctionne donc sans Google Play Services, par exemple sur Huawei.
- La photo et la signature, stockées en JPEG2000 sur la puce, sont converties en JPEG et en PNG sur le téléphone.
- Le résultat suit le même contrat `IdentityRecord` 1.1 que l'agent bureau ([contrat JSON](contrat-identity-record-v1.md)).

## 1. Dépendances

```yaml
# pubspec.yaml de l'application
dependencies:
  dz_eid:
    path: ../dz-eid/flutter/dz_eid   # plus tard : dépôt pub privé
```

Le cœur Java `dz-eid-core` est distribué par Maven. Pour l'instant, il est publié dans un dépôt local :

```powershell
.\gradlew.bat :dz-eid-core:publish   # à la racine de dz-eid → build/maven-repo
```

```kotlin
// android/build.gradle.kts de l'application
allprojects {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("C:/chemin/vers/dz-eid/build/maven-repo") } // plus tard : dépôt Maven privé
    }
}
```

```kotlin
// android/app/build.gradle.kts
android {
    defaultConfig { minSdk = 26 }
    packaging {
        resources {
            excludes += setOf("META-INF/versions/9/OSGI-INF/MANIFEST.MF")
            pickFirsts += setOf("META-INF/LICENSE.md", "META-INF/LICENSE.txt", "META-INF/NOTICE.md", "META-INF/NOTICE.txt")
        }
    }
}
```

Les règles R8 nécessaires (BouncyCastle, JMRTD, SCUBA, décodeur JP2) sont fournies par le plugin et appliquées automatiquement.
Les permissions `NFC` et `CAMERA` sont aussi déclarées par le plugin.

## 2. Utilisation

```dart
import 'package:dz_eid/dz_eid.dart';

// État du NFC : available / disabled / unavailable
if (await DzEid.getNfcStatus() == NfcStatus.disabled) {
  await DzEid.openNfcSettings();
}

// Clé d'accès : scan de la MRZ (null si l'utilisateur abandonne)…
final scan = await DzEid.scanMrz();
final key = scan?.toAccessKey()
    // …ou saisie manuelle
    ?? AccessKey.fromDates(documentNumber: '123456789',
        dateOfBirth: DateTime(1985, 3, 12), dateOfExpiry: DateTime(2031, 5, 20));

try {
  final id = await DzEid.readCard(
    accessKey: key,
    onProgress: (p) => print('${p.percent} % ${p.message}'), // messages en français
  );
  print(id.holder.lastNameArabic);
  final ok = id.verification.passiveAuthentication.dataIntegrity == CheckStatus.valid &&
      id.verification.activeAuthentication.result == CheckStatus.valid;
  Image.memory(id.photo!.bytes);                 // photo JPEG
  final json = id.toPrettyJson();                // même JSON que l'agent bureau
} on DzEidException catch (e) {
  print('${e.code} : ${e.message}');             // ex. ACCESS_DENIED, NO_CARD, CARD_LOST
}

await DzEid.cancel(); // interrompt l'attente ou la lecture (code CANCELLED)
```

### Codes d'erreur

| Code | Signification |
|---|---|
| `INVALID_INPUT` | MRZ ou dates invalides |
| `NO_CARD` | Aucune carte détectée dans le délai (60 s par défaut, `ReadOptions.timeout`) |
| `ACCESS_DENIED` | La MRZ ne correspond pas à la carte |
| `CARD_LOST` | Carte retirée pendant la lecture. Le plugin attend d'abord la carte à nouveau, jusqu'à 3 fois |
| `NOT_EMRTD` | Le support sans contact n'est pas une pièce d'identité électronique |
| `READ_ERROR`, `INTERNAL` | Erreur de lecture ou erreur interne |
| `BUSY` | Une lecture ou un scan est déjà en cours |
| `NFC_UNAVAILABLE`, `NFC_DISABLED` | Pas de NFC, ou NFC désactivé |
| `CANCELLED` | Annulation par `DzEid.cancel()` |
| `CAMERA_PERMISSION_DENIED`, `CAMERA_UNAVAILABLE` | Scan MRZ impossible : proposer la saisie manuelle |
| `NO_ACTIVITY` | Application en arrière-plan |
| `UNSUPPORTED_PLATFORM` | Plateforme autre qu'Android (iOS : plus tard) |

## 3. Architecture

```
flutter/dz_eid/
├── lib/                          API Dart (DzEid, AccessKey, IdentityRecord…)
└── android/src/main/kotlin/com/muhend/dzeid/
    ├── android/                  Code Android indépendant de Flutter (extractible en AAR)
    │   ├── NfcCardReader.kt      Mode lecteur NFC, IsoDep, EidReader, délai, annulation, reprise
    │   ├── MrzScannerActivity.kt CameraX + ML Kit + MrzOcr (confirmation sur 2 images)
    │   ├── ImageConverter.kt     JPEG2000 → JPEG / PNG (OpenJPEG)
    │   └── CryptoSetup.kt        BouncyCastle complet à la place du « BC » tronqué d'Android
    └── dz_eid/DzEidPlugin.kt     Pont Flutter (MethodChannel + EventChannel)
```

## 4. Application de démonstration

```powershell
cd flutter/dz_eid/example
flutter run                 # téléphone branché en USB, débogage USB activé
flutter build apk --release --split-per-abi   # APK par architecture (~30 Mo au lieu de ~80 Mo)
```
