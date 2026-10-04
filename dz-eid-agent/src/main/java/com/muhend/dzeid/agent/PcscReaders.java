package com.muhend.dzeid.agent;

import com.muhend.dzeid.core.EidException;
import com.muhend.dzeid.core.ErrorCode;

import javax.smartcardio.CardException;
import javax.smartcardio.CardTerminal;
import javax.smartcardio.TerminalFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Accès aux lecteurs PC/SC (Windows Smart Card service).
 */
final class PcscReaders {

    private static final Logger LOG = Logger.getLogger(PcscReaders.class.getName());

    /** Lecteurs liste, avec réparation automatique du contexte PC/SC si le service Windows a redémarré. */
    List<CardTerminal> list() throws EidException {
        try {
            return TerminalFactory.getDefault().terminals().list();
        } catch (CardException e) {
            String reason = rootMessage(e);
            if (reason.contains("SCARD_E_NO_READERS_AVAILABLE")) {
                return new ArrayList<>();
            }
            if (reason.contains("SCARD_E_SERVICE_STOPPED") || reason.contains("SCARD_E_INVALID_HANDLE")
                    || reason.contains("SCARD_E_NO_SERVICE")) {
                resetPcscContext();
                try {
                    return TerminalFactory.getDefault().terminals().list();
                } catch (CardException retry) {
                    if (rootMessage(retry).contains("SCARD_E_NO_READERS_AVAILABLE")) {
                        return new ArrayList<>();
                    }
                    throw new EidException(ErrorCode.NO_READER, "Service carte à puce Windows indisponible.", retry);
                }
            }
            throw new EidException(ErrorCode.NO_READER, "Impossible d'énumérer les lecteurs : " + reason, e);
        } catch (RuntimeException e) {
            throw new EidException(ErrorCode.NO_READER, "PC/SC indisponible sur ce poste.", e);
        }
    }

    /**
     * Choisit le lecteur : celui dont le nom contient {@code preferred}, sinon le premier lecteur
     * sans contact (CL / Contactless / PICC), sinon le premier lecteur.
     */
    CardTerminal select(String preferred) throws EidException {
        List<CardTerminal> terminals = list();
        if (terminals.isEmpty()) {
            throw new EidException(ErrorCode.NO_READER, "Aucun lecteur de carte détecté. Branchez le lecteur USB.");
        }
        if (preferred != null && !preferred.isBlank()) {
            for (CardTerminal t : terminals) {
                if (t.getName().toLowerCase(Locale.ROOT).contains(preferred.toLowerCase(Locale.ROOT))) {
                    return t;
                }
            }
        }
        for (CardTerminal t : terminals) {
            String n = t.getName().toLowerCase(Locale.ROOT);
            if (n.contains(" cl ") || n.contains("contactless") || n.contains("picc") || n.endsWith(" cl")) {
                return t;
            }
        }
        return terminals.get(0);
    }

    static boolean isCardPresent(CardTerminal t) {
        try {
            return t.isCardPresent();
        } catch (CardException e) {
            return false;
        }
    }

    /**
     * Contournement d'un défaut connu de Java : quand le service Windows « Carte à puce » s'arrête
     * (dernier lecteur débranché), le contexte PC/SC mis en cache devient invalide jusqu'au redémarrage
     * de la JVM. On remet ce contexte à zéro pour forcer sa recréation.
     */
    private static void resetPcscContext() {
        try {
            Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
            Field theUnsafe = unsafeClass.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            Object unsafe = theUnsafe.get(null);
            Field contextId = Class.forName("sun.security.smartcardio.PCSCTerminals").getDeclaredField("contextId");
            Method base = unsafeClass.getMethod("staticFieldBase", Field.class);
            Method offset = unsafeClass.getMethod("staticFieldOffset", Field.class);
            Method putLong = unsafeClass.getMethod("putLong", Object.class, long.class, long.class);
            putLong.invoke(unsafe, base.invoke(unsafe, contextId), (long) offset.invoke(unsafe, contextId), 0L);
            LOG.info("Contexte PC/SC réinitialisé.");
        } catch (Throwable t) {
            LOG.log(Level.WARNING, "Réinitialisation du contexte PC/SC impossible", t);
        }
    }

    private static String rootMessage(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (Throwable c = t; c != null; c = c.getCause()) {
            sb.append(c.getMessage()).append(' ');
        }
        return sb.toString();
    }
}
