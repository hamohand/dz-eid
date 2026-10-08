# Guide de démarrage rapide des 3 modes de lecture `dz-eid`

Le projet **dz-eid** offre 3 façons complémentaires d'exploiter la lecture de la carte d'identité électronique algérienne (CNIe). Voici le récapitulatif des commandes de démarrage pour chacune d'elles.

---

## 🖥️ Mode 1 : Guichet USB (Poste fixe / PC/SC)

Idéal pour les guichets d'agences, études notariales ou postes de travail équipés d'un lecteur de carte sans contact USB (ex: Identiv uTrust 3700 F).

### Prérequis
* JDK 17 ou 21 installé.
* Lecteur sans contact branché en USB.

### Démarrage
Depuis la racine du projet `dz-eid` :

```powershell
# 1. (Optionnel) Exécuter la suite de tests
.\gradlew.bat test

# 2. Lancer l'agent local d'écoute
.\gradlew.bat :dz-eid-agent:run
```

* **Interface de test :** Ouvrez votre navigateur sur [http://127.0.0.1:8989/demo/](http://127.0.0.1:8989/demo/)
* **Endpoints API :**
  * `POST http://127.0.0.1:8989/v1/read`
  * `WS ws://127.0.0.1:8989/v1/events`

---

## 📱 Mode 2 : Mobile Autonome (Smartphone Android NFC)

Idéal pour les agents de terrain ou applications mobiles dédiées. Le smartphone scanne la MRZ avec son appareil photo et lit la puce NFC directement.

### Prérequis
* Flutter SDK (3.47+) & Android SDK configuré.
* Smartphone Android 8.0+ avec NFC activé, connecté en débogage USB.

### Démarrage / Installation
Depuis le dossier de l'application exemple :

```powershell
cd flutter/dz_eid/example

# Lancer directement sur le téléphone connecté (en mode Release optimisé)
flutter run --release

# Ou générer le fichier APK d'installation autonome
flutter build apk --release
# L'APK produit se trouve dans : build/app/outputs/flutter-apk/app-release.apk
```

---

## 🌐 Mode 3 : Relais Web (Scan QR Code & Chiffrement E2EE)

Idéal pour les portails web (onboarding en ligne, banques, plateformes web) : l'utilisateur ouvre le site sur son PC, flashe le QR Code avec son smartphone `dz-eid`, et les données d'identité sont transmises au navigateur de façon sécurisée (chiffrement de bout en bout P-256 + AES-GCM, le serveur relais ne voit jamais les données en clair).

Ce mode s'appuie sur 3 composants :

### 1. Démarrer le Serveur Relais (`dz-eid-relay`)
```powershell
cd dz-eid-relay

# Installation des dépendances (la première fois)
npm install

# Démarrage du serveur (écoute sur le port 3000)
npm start
```

### 2. Connecter le téléphone au serveur local (uniquement pour les tests USB locaux)
Si le serveur relais tourne en local sur `localhost:3000` et que votre téléphone est branché en USB :
```powershell
adb reverse tcp:3000 tcp:3000
```
*(En production, le serveur relais possède une adresse publique HTTPS/WSS accessible directement par le smartphone en 4G/Wi-Fi).*

### 3. Démarrer le site web / démonstrateur (`dz-eid-js/demo`)
Dans un second terminal :
```powershell
cd dz-eid-js/demo

# Installation des dépendances (la première fois)
npm install

# Lancement du serveur web de démo (Vite)
npm run dev
```

* **Interface web :** Ouvrez votre navigateur sur [http://localhost:5173/](http://localhost:5173/)
* **Action :** Ouvrez l'application mobile `dz_eid`, appuyez sur **"Relais Web (Scan QR Code)"**, scannez le QR Code affiché sur votre écran, et posez la carte contre le téléphone. Les données s'affichent instantanément sur le site web.

---

## 📋 Tableau Récapitulatif

| Mode | Composant principal | Commande clé | Port / URL par défaut |
| :--- | :--- | :--- | :--- |
| **USB Guichet** | `dz-eid-agent` | `.\gradlew.bat :dz-eid-agent:run` | `http://127.0.0.1:8989/demo/` |
| **Mobile Android** | `flutter/dz_eid/example` | `flutter run --release` | Application installée sur le téléphone |
| **Relais Web (Serveur)** | `dz-eid-relay` | `npm start` (dans `dz-eid-relay`) | `http://localhost:3000` |
| **Relais Web (Client Démo)**| `dz-eid-js/demo` | `npm run dev` (dans `dz-eid-js/demo`) | `http://localhost:5173/` |
