# dz-eid

**Lecture et vérification des cartes d'identité biométriques algériennes (CNIe) et des documents ICAO 9303.**

Une bibliothèque Java unique (`dz-eid-core`) qui se branche sur un lecteur USB ou sur un téléphone Android. On pose la carte et on obtient un JSON propre et signé : noms latins et arabes, NIN, photo, dates, autorité de délivrance.

## Modules

| Module | Rôle |
|---|---|
| [`dz-eid-core`](dz-eid-core) | Bibliothèque portable (Java 17, compatible Android) : PACE/BAC, lecture des DG, décodage algérien (ISO-8859-6), Passive Authentication, contrat `IdentityRecord` |
| [`dz-eid-agent`](dz-eid-agent) | Agent Windows : pont sécurisé entre un lecteur PC/SC et les applications web (REST + WebSocket sur `127.0.0.1:8989`) et page de démo |
| [`flutter/dz_eid`](flutter/dz_eid) | Plugin Flutter (Android 8+) : lecture NFC par le téléphone, scan de la MRZ par la caméra, application de démo ([guide d'intégration](docs/integration-flutter.md)) |

## Démarrage rapide

Prérequis : JDK 21 et un lecteur sans contact PC/SC (testé avec l'**Identiv uTrust 3700 F**).

```powershell
.\gradlew.bat test                 # tous les tests
.\gradlew.bat :dz-eid-agent:run    # lance l'agent
```

Ouvrez ensuite **http://127.0.0.1:8989/demo/**, saisissez ou collez la MRZ, posez la carte et cliquez sur « Lire la carte ».

## Intégration dans une application web (3 étapes)

1. Ajoutez l'origine de votre site dans `%APPDATA%\dz-eid\config.json` → `allowedOrigins`.
2. Appelez l'agent :

```javascript
const res = await fetch('http://127.0.0.1:8989/v1/read', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ documentNumber: '123456789', dateOfBirth: '850312', dateOfExpiry: '310520' })
});
const identity = await res.json();   // contrat IdentityRecord v1
console.log(identity.holder.lastNameArabic, identity.holder.nin);
```

3. (Facultatif) Écoutez `ws://127.0.0.1:8989/v1/events` pour afficher la progression et la présence de la carte.

Détails : [API de l'agent](docs/api-agent-v1.md) · [Contrat JSON](docs/contrat-identity-record-v1.md).

## Utilisation de la bibliothèque (Java / Android)

```java
IdentityRecord id = new EidReader().read(
        cardService,                                    // PC/SC : TerminalCardService ; Android : IsoDep
        AccessKey.fromMrz(mrzText),
        ReadOptions.defaults(),
        (step, percent, message) -> System.out.println(percent + "% " + message));
String json = IdentityRecordJson.toJson(id);
```

Décodage hors ligne de données déjà lues (diagnostic, données transmises par un mobile) : `EidReader.decode(cardData, options)`.

## Sécurité

- L'agent n'écoute que sur `127.0.0.1`. Il n'accepte que les origines web configurées, sans joker. L'en-tête `Host` est vérifié, ce qui bloque le DNS rebinding.
- Les WebSocket sont aussi filtrés par origine.
- Les journaux ne contiennent **aucune donnée personnelle**.
- Passive Authentication : contrôle de l'intégrité des données et de la signature de l'État. La vérification de la chaîne CSCA s'active quand des certificats sont placés dans `cscaDirectory`.

## Données de test

- `dz-eid-core/src/test/.../DzCardFixtures.java` : jeux d'essai **fictifs**, qui reproduisent la structure exacte des CNIe.
- `private-fixtures/` (**ignoré par git**) : dumps de vraies cartes pour les tests de non-régression locaux. Ne jamais versionner ce dossier.

## Licences tierces (à valider avant commercialisation)

| Composant | Licence | Remarque |
|---|---|---|
| JMRTD | LGPL 3.0 | Bibliothèque séparée, non modifiée : à mentionner, et l'utilisateur doit pouvoir la remplacer |
| SCUBA | LGPL 3.0 | Idem |
| BouncyCastle | MIT | — |
| Jackson, Javalin | Apache 2.0 | — |
| jai-imageio-jpeg2000 | BSD + licence JJ2000 | Conversion de la photo : à faire vérifier par un juriste |
| JP2ForAndroid (Thales, OpenJPEG) | BSD-2 | Conversion JPEG2000 sur Android |
| ML Kit Text Recognition (Google) | Conditions ML Kit | Modèle embarqué, gratuit, hors ligne |
| CameraX, AndroidX | Apache 2.0 | — |
