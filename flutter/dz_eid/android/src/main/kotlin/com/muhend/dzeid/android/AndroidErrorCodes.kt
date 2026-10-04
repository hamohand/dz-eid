package com.muhend.dzeid.android

/**
 * Codes d'erreur propres à Android, en complément de [com.muhend.dzeid.core.ErrorCode]
 * (INVALID_INPUT, NO_CARD, ACCESS_DENIED, CARD_LOST, NOT_EMRTD, READ_ERROR, BUSY, INTERNAL).
 */
object AndroidErrorCodes {
    /** Le téléphone n'a pas de puce NFC. */
    const val NFC_UNAVAILABLE = "NFC_UNAVAILABLE"

    /** Le NFC est désactivé dans les paramètres. */
    const val NFC_DISABLED = "NFC_DISABLED"

    /** Opération annulée par l'application ou l'utilisateur. */
    const val CANCELLED = "CANCELLED"

    /** Permission caméra refusée. */
    const val CAMERA_PERMISSION_DENIED = "CAMERA_PERMISSION_DENIED"

    /** Caméra absente ou impossible à ouvrir. */
    const val CAMERA_UNAVAILABLE = "CAMERA_UNAVAILABLE"

    /** Aucune activité Android au premier plan (application en arrière-plan). */
    const val NO_ACTIVITY = "NO_ACTIVITY"
}
