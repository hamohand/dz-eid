package com.muhend.dzeid.core.verify;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.Provider;

/**
 * Fournisseur cryptographique BouncyCastle utilisé par instance (jamais par nom).
 *
 * <p>Sur Android, le nom « BC » désigne une version allégée intégrée au système ;
 * passer l'instance évite tout conflit.</p>
 */
public final class Crypto {

    public static final Provider BC = new BouncyCastleProvider();

    private Crypto() {
    }
}
