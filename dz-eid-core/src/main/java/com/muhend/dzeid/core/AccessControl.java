package com.muhend.dzeid.core;

import net.sf.scuba.smartcards.CardServiceException;
import org.jmrtd.BACKey;
import org.jmrtd.PACEKeySpec;
import org.jmrtd.PassportService;
import org.jmrtd.lds.CardAccessFile;
import org.jmrtd.lds.PACEInfo;
import org.jmrtd.lds.SecurityInfo;

import java.util.logging.Logger;

/**
 * Contrôle d'accès à la puce : PACE si la carte l'annonce (EF.CardAccess), sinon BAC.
 *
 * <p>Séquence unifiée, reprise de l'implémentation Android éprouvée sur la CNIe algérienne :
 * lecture de EF.CardAccess → PACE → sélection de l'application ICAO → BAC en repli.</p>
 *
 * <p>Constat terrain (uTrust 3700 F, CNIe 2017/2019) : ces cartes n'ont pas d'EF.CardAccess
 * (SW 6A82 sous le MF comme sous l'application ICAO, y compris par SFI). Elles ne proposent donc
 * que BAC ; le repli est le chemin normal pour elles, PACE reste tenté pour les séries futures.</p>
 */
final class AccessControl {

    private static final Logger LOG = Logger.getLogger(AccessControl.class.getName());
    private static final int SW_FILE_NOT_FOUND = 0x6A82;

    private AccessControl() {
    }

    /** @return {@code "PACE"} ou {@code "BAC"} */
    static String authenticate(PassportService service, AccessKey key) throws EidException {
        BACKey bacKey = new BACKey(key.documentNumber(), key.dateOfBirth(), key.dateOfExpiry());

        boolean paceSucceeded = false;
        // Journal technique uniquement (OID, mots d'état) : jamais de donnée personnelle
        String paceStatus;
        try {
            CardAccessFile cardAccess = new CardAccessFile(
                    service.getInputStream(PassportService.EF_CARD_ACCESS, PassportService.DEFAULT_MAX_BLOCKSIZE));
            PACEInfo pace = null;
            for (SecurityInfo info : cardAccess.getSecurityInfos()) {
                if (info instanceof PACEInfo) {
                    pace = (PACEInfo) info;
                    break;
                }
            }
            if (pace == null) {
                paceStatus = "EF.CardAccess sans PACEInfo";
            } else {
                try {
                    service.doPACE(PACEKeySpec.createMRZKey(bacKey), pace.getObjectIdentifier(),
                            PACEInfo.toParameterSpec(pace.getParameterId()), pace.getParameterId());
                    paceSucceeded = true;
                    paceStatus = "réussi (" + pace.getObjectIdentifier() + ", param=" + pace.getParameterId() + ")";
                } catch (Exception e) {
                    if (EidException.isCardLost(e)) {
                        throw EidException.classify(e, ErrorCode.CARD_LOST, null);
                    }
                    paceStatus = "échec (" + pace.getObjectIdentifier() + ") : " + describe(e);
                }
            }
        } catch (EidException e) {
            throw e;
        } catch (Exception e) {
            if (EidException.isCardLost(e)) {
                throw EidException.classify(e, ErrorCode.CARD_LOST, null);
            }
            paceStatus = hasSw(e, SW_FILE_NOT_FOUND)
                    ? "non proposé par la carte (EF.CardAccess absent)"
                    : "EF.CardAccess illisible : " + describe(e);
        }
        LOG.info("PACE " + paceStatus + (paceSucceeded ? "" : " → BAC"));

        try {
            service.sendSelectApplet(paceSucceeded);
        } catch (CardServiceException e) {
            throw EidException.classify(e, ErrorCode.NOT_EMRTD,
                    "Cette carte n'est pas un document d'identité électronique (application ICAO introuvable).");
        }

        if (paceSucceeded) {
            return "PACE";
        }
        try {
            service.doBAC(bacKey);
            return "BAC";
        } catch (CardServiceException e) {
            throw EidException.classify(e, ErrorCode.ACCESS_DENIED,
                    "Accès refusé par la puce : vérifiez le numéro du document, la date de naissance "
                            + "et la date d'expiration.");
        }
    }

    private static boolean hasSw(Throwable t, int sw) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof CardServiceException && (((CardServiceException) c).getSW() & 0xFFFF) == sw) {
                return true;
            }
        }
        return false;
    }

    /** Chaîne des causes d'une exception, avec le mot d'état (SW) de la puce si disponible. */
    static String describe(Throwable t) {
        StringBuilder sb = new StringBuilder();
        int depth = 0;
        for (Throwable c = t; c != null && depth < 5; c = c.getCause(), depth++) {
            if (depth > 0) {
                sb.append(" <- ");
            }
            sb.append(c.getClass().getSimpleName());
            if (c.getMessage() != null) {
                sb.append(": ").append(c.getMessage());
            }
            if (c instanceof CardServiceException) {
                int sw = ((CardServiceException) c).getSW();
                if (sw != -1) {
                    sb.append(String.format(" [SW=%04X]", sw & 0xFFFF));
                }
            }
        }
        return sb.toString();
    }
}
