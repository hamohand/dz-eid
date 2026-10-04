group = "com.muhend.dzeid.dz_eid"
version = "0.1.0-SNAPSHOT"

// Dépôt Maven local alimenté par : .\gradlew.bat :dz-eid-core:publish (à la racine de dz-eid).
// Plus tard : dépôt Maven privé.
val dzEidMavenRepo = projectDir.resolve("../../../build/maven-repo")

buildscript {
    val kotlinVersion = "2.4.0"
    repositories {
        google()
        mavenCentral()
    }

    dependencies {
        classpath("com.android.tools.build:gradle:9.1.0")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven { url = uri(dzEidMavenRepo) }
    }
}

plugins {
    id("com.android.library")
}

android {
    namespace = "com.muhend.dzeid.dz_eid"

    compileSdk = 36

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/kotlin")
        }
        getByName("test") {
            java.srcDirs("src/test/kotlin")
        }
    }

    defaultConfig {
        // Android 8.0 : java.time et java.util.Base64 (utilisés par le core) sont natifs à partir de l'API 26.
        minSdk = 26
        // Règles R8 appliquées automatiquement aux applications qui intègrent le plugin.
        consumerProguardFiles("consumer-rules.pro")
    }

    // Remarque : les règles « packaging » (fichiers META-INF en double des jars BouncyCastle) doivent être
    // déclarées dans l'application intégratrice ; voir example/android/app/build.gradle.kts.

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.useJUnitPlatform()

                it.outputs.upToDateWhen { false }

                it.testLogging {
                    events("passed", "skipped", "failed", "standardOut", "standardError")
                    showStandardStreams = true
                }
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

val cameraxVersion = "1.6.2"

dependencies {
    // Cœur portable dz-eid (BAC/PACE, DG, Passive/Active Authentication).
    // Jackson est exclu : sur Android, le résultat est converti par IdentityRecordMaps (sans réflexion).
    implementation("com.muhend.dzeid:dz-eid-core:0.1.0-SNAPSHOT") {
        exclude(group = "com.fasterxml.jackson.core")
    }
    // BouncyCastle complet : le fournisseur « BC » intégré à Android est tronqué.
    implementation("org.bouncycastle:bcprov-jdk18on:1.86")
    // CardService SCUBA au-dessus d'IsoDep (NFC Android).
    implementation("net.sf.scuba:scuba-sc-android:0.0.27")
    // Décodeur JPEG2000 (OpenJPEG, licence BSD-2) : la photo et la signature de la puce sont en JP2.
    // 1.0.5 et non 1.1.0 : la 1.1.0 impose compileSdk 37 aux applications. Bibliothèques natives alignées 16 Ko.
    implementation("io.github.michaldvorak-gemalto:jp2-android:1.0.5")

    // Lecture de la MRZ par la caméra : modèle ML Kit embarqué (fonctionne sans Google Play Services).
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")
    implementation("androidx.activity:activity:1.11.0")

    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.mockito:mockito-core:5.0.0")
}
