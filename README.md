# dz-eid

**Lecture et vérification souveraine des cartes d'identité biométriques algériennes (CNIe) et des documents ICAO 9303.**

Une solution complète et modulaire qui permet de lire et vérifier cryptographiquement la puce d'une CNIe algérienne selon 3 modes : **Guichet USB**, **Smartphone Mobile**, et **Relais Web à distance via QR Code**.

---

## 📦 Les 3 Outils / Modes d'usage

| Outil | Cas d'usage | Technologie | Module |
|---|---|---|---|
| **1. Guichet USB** | Postes de travail fixes équipés d'un lecteur sans contact PC/SC (ex: Identiv uTrust 3700 F). | Java 17/21, Javalin (REST + WS) | [`dz-eid-agent`](dz-eid-agent) |
| **2. Mobile Autonome** | Smartphones de terrain : scan de la MRZ par caméra et lecture directe par NFC. | Flutter, Kotlin, CameraX, ML Kit | [`flutter/dz_eid`](flutter/dz_eid) |
| **3. Relais Web (E2EE)** | Sites web distants (KYC, banques, onboarding) : scan d'un QR code pour lire la carte via un smartphone avec chiffrement de bout en bout. | Node.js, TypeScript, Web Crypto API | [`dz-eid-relay`](dz-eid-relay) & [`dz-eid-js`](dz-eid-js) |

---

## ⚡ Démarrage Rapide des 3 Outils

> 📖 Pour un guide détaillé pas à pas, consultez le [**Guide complet de démarrage**](docs/guide-demarrage.md).

### 🖥️ 1. Lancer l'Agent USB (Bureau)
```powershell
# Depuis la racine du dépôt
.\gradlew.bat :dz-eid-agent:run
```
Accès à l'interface de test : **http://127.0.0.1:8989/demo/**

---

### 📱 2. Déployer l'Application Mobile (Android)
```powershell
cd flutter/dz_eid/example
flutter run --release
```
L'application s'installe sur le smartphone connecté avec support du scan MRZ caméra et lecture NFC directe.

---

### 🌐 3. Lancer le Relais Web & SDK (QR Code)
```powershell
# Terminal 1 : Démarrer le serveur relais (port 3000)
cd dz-eid-relay
npm install && npm start

# Terminal 2 : Démarrer le site web de démo (port 5173)
cd dz-eid-js/demo
npm install && npm run dev
```
Accès au site web de démo : **http://localhost:5173/**  
*(En local USB, exécutez `adb reverse tcp:3000 tcp:3000` pour relier le smartphone au serveur local).*

---

## 🧩 Architecture des Modules

```
dz-eid/
├── dz-eid-core/            # Cœur Java pur : BAC/PACE, PA (Passive Auth), AA (Anti-clonage), ISO-8859-6
├── dz-eid-agent/           # Service local Windows/Linux pour lecteurs USB (REST + WebSockets)
├── flutter/
│   └── dz_eid/             # Plugin Flutter + application exemple (Android 8+, minSdk 26)
├── dz-eid-relay/           # Serveur relais léger en Node.js / WebSockets
└── dz-eid-js/              # SDK JavaScript pour intégration web avec déchiffrement E2EE
    └── demo/               # Démonstrateur web Vite / TypeScript
```

---

## 🔐 Sécurité & Confidentialité

- **Chiffrement de bout en bout (E2EE)** : En mode Web, le navigateur génère une paire de clés éphémères (ECDH P-256). Le smartphone chiffre les données en AES-GCM 256 bits. Le serveur relais n'a jamais accès aux données d'identité en clair.
- **Authenticité infalsifiable** : Contrôle de la signature de l'État algérien (Passive Authentication) et détection du clonage (Active Authentication).
- **Zéro fuite** : En mode guichet ou mobile autonome, aucune donnée ne quitte la machine ou le téléphone.

---

## 📚 Documentation détaillée

* [Guide de démarrage des 3 outils](docs/guide-demarrage.md)
* [Spécification du Contrat `IdentityRecord` (v1.1)](docs/contrat-identity-record-v1.md)
* [Documentation de l'API Agent Bureau](docs/api-agent-v1.md)
* [Guide d'intégration du Plugin Flutter](docs/integration-flutter.md)
* [Architecture de la Phase 3 (Relais Web)](phase3_plan.md)
