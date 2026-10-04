package com.muhend.dzeid.core;

import com.muhend.dzeid.core.verify.CscaStore;

/**
 * Options de lecture.
 *
 * @param readPhoto            lire le DG2 (photo, ~15-25 Ko : allonge la lecture de 1 à 3 s)
 * @param includeRaw           inclure les octets bruts des DG en hexadécimal dans le résultat
 * @param verifyPassive        effectuer la Passive Authentication (recommandé)
 * @param cscaStore            certificats racines de confiance
 * @param readSignatureImage   lire le DG7 (image de la signature manuscrite) si la carte l'annonce
 * @param activeAuthentication effectuer l'Active Authentication (anti-clonage) si la carte a un DG15
 */
public record ReadOptions(boolean readPhoto, boolean includeRaw, boolean verifyPassive, CscaStore cscaStore,
                          boolean readSignatureImage, boolean activeAuthentication) {

    public ReadOptions {
        if (cscaStore == null) {
            cscaStore = CscaStore.empty();
        }
    }

    /** Compatibilité 1.0 : signature manuscrite et anti-clonage activés. */
    public ReadOptions(boolean readPhoto, boolean includeRaw, boolean verifyPassive, CscaStore cscaStore) {
        this(readPhoto, includeRaw, verifyPassive, cscaStore, true, true);
    }

    public static ReadOptions defaults() {
        return new ReadOptions(true, false, true, CscaStore.empty(), true, true);
    }

    public ReadOptions withReadPhoto(boolean value) {
        return new ReadOptions(value, includeRaw, verifyPassive, cscaStore, readSignatureImage, activeAuthentication);
    }

    public ReadOptions withIncludeRaw(boolean value) {
        return new ReadOptions(readPhoto, value, verifyPassive, cscaStore, readSignatureImage, activeAuthentication);
    }

    public ReadOptions withCscaStore(CscaStore value) {
        return new ReadOptions(readPhoto, includeRaw, verifyPassive, value, readSignatureImage, activeAuthentication);
    }

    public ReadOptions withReadSignatureImage(boolean value) {
        return new ReadOptions(readPhoto, includeRaw, verifyPassive, cscaStore, value, activeAuthentication);
    }

    public ReadOptions withActiveAuthentication(boolean value) {
        return new ReadOptions(readPhoto, includeRaw, verifyPassive, cscaStore, readSignatureImage, value);
    }
}
