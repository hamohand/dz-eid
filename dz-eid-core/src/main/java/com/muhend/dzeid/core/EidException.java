package com.muhend.dzeid.core;

/** Erreur métier dz-eid, porteuse d'un {@link ErrorCode} et d'un message en français. */
public class EidException extends Exception {

    private final ErrorCode code;

    public EidException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public EidException(ErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }

    /**
     * Classe une exception technique (PC/SC, NFC Android, JMRTD) : carte retirée ou autre erreur.
     */
    public static EidException classify(Throwable t, ErrorCode fallback, String fallbackMessage) {
        if (t instanceof EidException) {
            return (EidException) t;
        }
        if (isCardLost(t)) {
            return new EidException(ErrorCode.CARD_LOST,
                    "La carte a été retirée ou la communication a été interrompue. Reposez la carte et réessayez.", t);
        }
        return new EidException(fallback, fallbackMessage, t);
    }

    static boolean isCardLost(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            String name = c.getClass().getSimpleName();
            String msg = String.valueOf(c.getMessage()).toUpperCase();
            if (name.contains("TagLost") || msg.contains("REMOVED") || msg.contains("TAG WAS LOST")
                    || msg.contains("SCARD_E_NO_SMARTCARD") || msg.contains("SCARD_W_RESET_CARD")
                    || msg.contains("SCARD_E_NOT_TRANSACTED") || msg.contains("CARD NOT PRESENT")) {
                return true;
            }
            if (c.getCause() == c) {
                break;
            }
        }
        return false;
    }
}
