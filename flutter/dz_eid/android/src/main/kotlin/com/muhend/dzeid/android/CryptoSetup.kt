package com.muhend.dzeid.android

import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

/**
 * Remplace le fournisseur « BC » tronqué d'Android par BouncyCastle complet.
 *
 * JMRTD demande certains algorithmes par nom de fournisseur (MAC ISO 9797-1, courbes EC, RSA ISO 9796-2) :
 * la version intégrée à Android ne les fournit pas tous. Le fournisseur est réinséré à la même position,
 * pour ne pas modifier les choix cryptographiques par défaut de l'application hôte (TLS, etc.).
 */
internal object CryptoSetup {

    @Volatile
    private var installed = false

    fun ensureBouncyCastle() {
        if (installed) return
        synchronized(this) {
            if (installed) return
            val current = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME)
            if (current == null || current.javaClass.name != BouncyCastleProvider::class.java.name) {
                val position = Security.getProviders().indexOf(current) + 1 // 0 si absent
                Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
                if (position > 0) {
                    Security.insertProviderAt(BouncyCastleProvider(), position)
                } else {
                    Security.addProvider(BouncyCastleProvider())
                }
            }
            installed = true
        }
    }
}
