# Règles R8/ProGuard du plugin dz_eid, appliquées automatiquement à l'application qui l'intègre.

# BouncyCastle charge ses algorithmes par réflexion (SPI) : rien ne doit être renommé ni supprimé.
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
-dontwarn javax.naming.**

# JMRTD et SCUBA : décodage ASN.1/LDS par réflexion et noms de classes.
-keep class org.jmrtd.** { *; }
-dontwarn org.jmrtd.**
-keep class net.sf.scuba.** { *; }
-dontwarn net.sf.scuba.**

# Décodeur JPEG2000 : méthodes natives (JNI).
-keep class com.gemalto.jp2.** { *; }

# Jackson est volontairement exclu sur Android (IdentityRecordJson n'y est pas utilisé).
-dontwarn com.fasterxml.jackson.**
