import { DzEidSession } from 'dz-eid';

async function init() {
  try {
    // URL of our local dz-eid-relay
    const relayUrl = 'http://localhost:3000';
    const session = new DzEidSession(relayUrl);

    // 1. Init session and get QR Code
    const qrDataUrl = await session.initSessionAndGetQrCode();
    
    // Display it
    const qrContainer = document.getElementById('qr-container')!;
    qrContainer.innerHTML = `<img src="${qrDataUrl}" alt="QR Code de connexion" />`;

    // 2. Wait for the phone to send the encrypted data
    console.log("En attente de la lecture de la carte...");
    const record = await session.waitForCardData();
    console.log("Données déchiffrées avec succès :", record);

    // 3. Display results
    document.getElementById('loader')!.style.display = 'none';
    const resDiv = document.getElementById('result')!;
    resDiv.style.display = 'block';

    document.getElementById('res-nom')!.innerText = `${record.holder.lastNameLatin} / ${record.holder.lastNameArabic}`;
    document.getElementById('res-prenom')!.innerText = `${record.holder.firstNameLatin} / ${record.holder.firstNameArabic}`;
    document.getElementById('res-dob')!.innerText = record.holder.dateOfBirth;
    
    if (record.photo?.base64) {
      document.getElementById('res-photo')!.setAttribute('src', `data:image/jpeg;base64,${record.photo?.base64}`);
    }

  } catch (error) {
    console.error("Erreur lors de la session :", error);
    document.getElementById('loader')!.innerText = "Erreur de connexion au relais.";
  }
}

init();


