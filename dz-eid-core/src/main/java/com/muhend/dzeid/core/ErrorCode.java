package com.muhend.dzeid.core;

/** Codes d'erreur stables, exposés tels quels aux applications clientes. */
public enum ErrorCode {
    /** Paramètres invalides (MRZ incomplète, date mal formée…). */
    INVALID_INPUT,
    /** Aucun lecteur détecté. */
    NO_READER,
    /** Aucune carte posée sur le lecteur dans le délai imparti. */
    NO_CARD,
    /** La puce refuse l'accès : les informations MRZ ne correspondent pas à la carte. */
    ACCESS_DENIED,
    /** La carte a été retirée ou la communication a été interrompue. */
    CARD_LOST,
    /** La carte n'est pas un document de voyage électronique ICAO. */
    NOT_EMRTD,
    /** Erreur de lecture d'un fichier de la puce. */
    READ_ERROR,
    /** Une lecture est déjà en cours. */
    BUSY,
    /** Erreur interne inattendue. */
    INTERNAL
}
