package com.muhend.dzeid.android

/**
 * Valide une MRZ lue par la caméra seulement si elle est reconnue à l'identique sur
 * [requiredMatches] images consécutives (les chiffres de contrôle sont déjà vérifiés par MrzOcr).
 * Élimine les rares lectures erronées qui passeraient malgré tout les chiffres de contrôle.
 */
class MrzStabilizer(private val requiredMatches: Int = 2) {

    private var last: String? = null
    private var count = 0

    /** @return la MRZ confirmée, ou null s'il faut encore des images */
    @Synchronized
    fun offer(mrz: String?): String? {
        if (mrz == null) return null // image sans MRZ lisible : on ne remet pas le compteur à zéro
        if (mrz == last) {
            count++
        } else {
            last = mrz
            count = 1
        }
        return if (count >= requiredMatches) mrz else null
    }

    @Synchronized
    fun reset() {
        last = null
        count = 0
    }
}
