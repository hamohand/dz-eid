package com.muhend.dzeid.core;

import com.muhend.dzeid.core.verify.CscaStore;

/**
 * Options de lecture.
 *
 * @param readPhoto     lire le DG2 (photo, ~15-25 Ko : allonge la lecture de 1 à 3 s)
 * @param includeRaw    inclure les octets bruts des DG en hexadécimal dans le résultat
 * @param verifyPassive effectuer la Passive Authentication (recommandé)
 * @param cscaStore     certificats racines de confiance
 */
public record ReadOptions(boolean readPhoto, boolean includeRaw, boolean verifyPassive, CscaStore cscaStore) {

    public ReadOptions {
        if (cscaStore == null) {
            cscaStore = CscaStore.empty();
        }
    }

    public static ReadOptions defaults() {
        return new ReadOptions(true, false, true, CscaStore.empty());
    }

    public ReadOptions withReadPhoto(boolean value) {
        return new ReadOptions(value, includeRaw, verifyPassive, cscaStore);
    }

    public ReadOptions withIncludeRaw(boolean value) {
        return new ReadOptions(readPhoto, value, verifyPassive, cscaStore);
    }

    public ReadOptions withCscaStore(CscaStore value) {
        return new ReadOptions(readPhoto, includeRaw, verifyPassive, value);
    }
}
