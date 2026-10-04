plugins {
    `java-library`
    `maven-publish`
}

description = "dz-eid core : lecture et vérification des documents d'identité électroniques (ICAO 9303), profil algérien."

java {
    withSourcesJar()
}

// Dépôt Maven local consommé par le plugin Flutter (flutter/dz_eid/android) :
//   .\gradlew.bat :dz-eid-core:publish
publishing {
    publications {
        create<MavenPublication>("core") {
            artifactId = "dz-eid-core"
            from(components["java"])
        }
    }
    repositories {
        maven {
            name = "local"
            url = uri(rootProject.layout.buildDirectory.dir("maven-repo"))
        }
    }
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
