plugins {
    `java-library`
}

description = "dz-eid core : lecture et vérification des documents d'identité électroniques (ICAO 9303), profil algérien."

java {
    withSourcesJar()
}

// Bytecode Java 17 : compatible avec Android (AGP 8+) pour la future réutilisation mobile.
tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

dependencies {
    // JMRTD (LGPL) : protocoles BAC/PACE et décodage DG2. Exposé car l'API prend un CardService SCUBA.
    api("org.jmrtd:jmrtd:0.8.9")
    implementation("org.bouncycastle:bcprov-jdk18on:1.86")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.86")
    // Sérialisation du contrat IdentityRecord.
    api("com.fasterxml.jackson.core:jackson-databind:2.18.2")
}
