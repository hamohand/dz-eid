package com.muhend.dzeid.core;

import net.sf.scuba.smartcards.CardServiceException;
import org.jmrtd.BACKey;
import org.jmrtd.PACEKeySpec;
import org.jmrtd.PassportService;
import org.jmrtd.lds.CardAccessFile;
import org.jmrtd.lds.PACEInfo;
import org.jmrtd.lds.SecurityInfo;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Contrôle d'accès à la puce : PACE si la carte l'annonce (EF.CardAccess), sinon BAC.
 *
 * <p>Séquence unifiée, reprise de l'implémentation Android éprouvée sur la CNIe algérienne :
 * lecture de EF.CardAccess → PACE → sélection de l'application ICAO → BAC en repli.</p>
 */
final class AccessControl {

    private static final Logger LOG = Logger.getLogger(AccessControl.class.getName());

    private AccessControl() {
    }

    /** @return {@code "PACE"} ou {@code "BAC"} */
    static String authenticate(PassportService service, AccessKey key) throws EidException {
        BACKey bacKey = new BACKey(key.documentNumber(), key.dateOfBirth(), key.dateOfExpiry());

        boolean paceSucceeded = false;
        try {
            CardAccessFile cardAccess = new CardAccessFile(
                    service.getInputStream(PassportService.EF_CARD_ACCESS, PassportService.DEFAULT_MAX_BLOCKSIZE));
            for (SecurityInfo info : cardAccess.getSecurityInfos()) {
                if (info instanceof PACEInfo) {
                    PACEInfo pace = (PACEInfo) info;
                    service.doPACE(PACEKeySpec.createMRZKey(bacKey), pace.getObjectIdentifier(),
                            PACEInfo.toParameterSpec(pace.getParameterId()), pace.getParameterId());
                    paceSucceeded = true;
                    break;
                }
            }
        } catch (Exception e) {
            if (EidException.isCardLost(e)) {
                throw EidException.classify(e, ErrorCode.CARD_LOST, null);
            }
            LOG.log(Level.FINE, "PACE indisponible ou refusé, repli sur BAC", e);
        }

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
}
