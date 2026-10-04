package com.muhend.dzeid.agent;

import com.muhend.dzeid.core.EidException;
import com.muhend.dzeid.core.ErrorCode;

import javax.smartcardio.CardException;
import javax.smartcardio.CardTerminal;
import javax.smartcardio.TerminalFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
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
     * Lecteurs candidats, classés par ordre de préférence.
     * <ul>
     *   <li>si {@code preferred} est renseigné : uniquement les lecteurs dont le nom le contient
     *       (erreur NO_READER explicite si aucun ne correspond) ;</li>
     *   <li>sinon : tous les lecteurs, les lecteurs sans contact compatibles ICAO d'abord
     *       (Identiv/uTrust, HID/Omnikey…), les ACR122 (peu fiables avec les CNIe) en dernier.</li>
     * </ul>
     */
    List<CardTerminal> candidates(String preferred) throws EidException {
        List<CardTerminal> terminals = list();
        if (terminals.isEmpty()) {
            throw new EidException(ErrorCode.NO_READER, "Aucun lecteur de carte détecté. Branchez le lecteur USB.");
        }
        List<CardTerminal> result = new ArrayList<>();
        if (preferred != null && !preferred.isBlank()) {
            String p = preferred.toLowerCase(Locale.ROOT);
            for (CardTerminal t : terminals) {
                if (t.getName().toLowerCase(Locale.ROOT).contains(p)) {
                    result.add(t);
                }
            }
            if (result.isEmpty()) {
                throw new EidException(ErrorCode.NO_READER,
                        "Le lecteur configuré « " + preferred + " » est introuvable.");
            }
            return result;
        }
        result.addAll(terminals);
        result.sort(Comparator.comparingInt((CardTerminal t) -> rank(t.getName())));
        return result;
    }

    /** Rang d'un lecteur d'après son nom (plus petit = préféré). Fonction pure, testable. */
    static int rank(String name) {
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (n.contains("acr122")) {
            return 90;
        }
        boolean contactless = n.contains(" cl ") || n.endsWith(" cl") || n.contains("contactless")
                || n.contains("picc") || n.contains("nfc");
        if (n.contains("utrust") || n.contains("identiv") || n.contains("hid") || n.contains("omnikey")) {
            return contactless ? 0 : 20;
        }
        return contactless ? 10 : 30;
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
     * de la JVM. On remet ce contexte à zéro pour forcer sa recréation, et on vide le cache statique
     * des lecteurs : chaque objet lecteur mémorise le contexte de sa création, et répondrait sinon
     * « pas de carte » indéfiniment (constaté sur le uTrust 3700 F après un rebranchement).
     */
    static synchronized void resetPcscContext() {
        try {
            Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
            Field theUnsafe = unsafeClass.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            Object unsafe = theUnsafe.get(null);
            Class<?> pcscTerminals = Class.forName("sun.security.smartcardio.PCSCTerminals");
            Method base = unsafeClass.getMethod("staticFieldBase", Field.class);
            Method offset = unsafeClass.getMethod("staticFieldOffset", Field.class);

            Field contextId = pcscTerminals.getDeclaredField("contextId");
            Method putLong = unsafeClass.getMethod("putLong", Object.class, long.class, long.class);
            putLong.invoke(unsafe, base.invoke(unsafe, contextId), (long) offset.invoke(unsafe, contextId), 0L);

            try {
                Field cache = pcscTerminals.getDeclaredField("terminals");
                Method getObject = unsafeClass.getMethod("getObject", Object.class, long.class);
                Object map = getObject.invoke(unsafe, base.invoke(unsafe, cache), (long) offset.invoke(unsafe, cache));
                if (map instanceof java.util.Map) {
                    synchronized (pcscTerminals) {
                        ((java.util.Map<?, ?>) map).clear();
                    }
                }
            } catch (NoSuchFieldException ignored) {
                // JDK sans cache de lecteurs : rien à vider
            }
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
