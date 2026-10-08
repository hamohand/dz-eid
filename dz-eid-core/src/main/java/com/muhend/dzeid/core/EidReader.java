package com.muhend.dzeid.core;

import com.muhend.dzeid.core.ProgressListener.Step;
import com.muhend.dzeid.core.model.IdentityRecord;
import com.muhend.dzeid.core.model.IdentityRecord.ActiveAuthentication;
import com.muhend.dzeid.core.model.IdentityRecord.PassiveAuthentication;
import com.muhend.dzeid.core.model.IdentityRecord.Status;
import com.muhend.dzeid.core.parse.ComParser;
import com.muhend.dzeid.core.util.IoUtil;
import com.muhend.dzeid.core.verify.ActiveAuthenticator;
import com.muhend.dzeid.core.verify.PassiveAuthenticator;
import net.sf.scuba.smartcards.CardService;
import net.sf.scuba.smartcards.CardServiceException;
import org.jmrtd.PassportService;
import org.jmrtd.lds.icao.DG15File;
import org.jmrtd.protocol.AAResult;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Point d'entrée de la lecture d'un document d'identité électronique.
 *
 * <p>Indépendant du matériel : le bureau fournit un {@code TerminalCardService} (PC/SC),
 * Android un {@code CardService} construit sur {@code IsoDep}.</p>
 *
 * <pre>{@code
 * IdentityRecord id = new EidReader().read(cardService, AccessKey.of("123456789", "850312", "310520"),
 *         ReadOptions.defaults(), (step, pct, msg) -> System.out.println(pct + "% " + msg));
 * }</pre>
 */
public final class EidReader {

    private static final Logger LOG = Logger.getLogger(EidReader.class.getName());

    private static final short EF_SOD = 0x011D;
    private static final short EF_COM = 0x011E;
    private static final SecureRandom RANDOM = new SecureRandom();

    public IdentityRecord read(CardService cardService, AccessKey key, ReadOptions options,
                               ProgressListener listener) throws EidException {
        ProgressListener progress = listener == null ? ProgressListener.NONE : listener;
        ReadOptions opts = options == null ? ReadOptions.defaults() : options;

        PassportService service;
        try {
            progress.onProgress(Step.CONNECTING, 5, "Connexion à la puce…");
            service = new PassportService(cardService, PassportService.NORMAL_MAX_TRANCEIVE_LENGTH,
                    PassportService.EXTENDED_MAX_TRANCEIVE_LENGTH, false, false);
            service.open();
        } catch (CardServiceException e) {
            throw EidException.classify(e, ErrorCode.READ_ERROR, "Impossible d'ouvrir la communication avec la puce.");
        }

        try {
            progress.onProgress(Step.AUTHENTICATING, 15, "Authentification auprès de la puce…");
            String accessMethod = AccessControl.authenticate(service, key);

            progress.onProgress(Step.READING, 25, "Lecture du sommaire…");
            List<Integer> present = readPresentDataGroups(service);
            // Numéros de DG uniquement : indique notamment si DG14/DG15 (anti-clonage) existent
            LOG.info("EF.COM annonce les DG " + present);

            List<Integer> toRead = new ArrayList<>();
            toRead.add(1);
            for (int dg : new int[]{11, 12, 13}) {
                if (present.isEmpty() || present.contains(dg)) {
                    toRead.add(dg);
                }
            }
            if (opts.readPhoto() && (present.isEmpty() || present.contains(2))) {
                toRead.add(2);
            }
            // DG7 et DG15 sont facultatifs : lus seulement si EF.COM les annonce
            if (opts.readSignatureImage() && present.contains(7)) {
                toRead.add(7);
            }
            if (opts.activeAuthentication() && present.contains(15)) {
                toRead.add(15);
            }

            Map<Integer, byte[]> dataGroups = new LinkedHashMap<>();
            int done = 0;
            for (int dg : toRead) {
                int pct = 30 + (50 * done) / toRead.size();
                progress.onProgress(Step.READING, pct, label(dg));
                byte[] bytes = readFile(service, (short) (0x0100 + dg), dg == 1);
                if (bytes != null) {
                    dataGroups.put(dg, bytes);
                }
                done++;
            }

            ActiveAuthentication aa = activeAuthentication(service, dataGroups.get(15), opts, progress);

            byte[] sod = null;
            if (opts.verifyPassive()) {
                progress.onProgress(Step.READING, 85, "Lecture de la signature électronique…");
                sod = readFile(service, EF_SOD, false);
            }

            PassiveAuthentication pa;
            if (opts.verifyPassive()) {
                progress.onProgress(Step.VERIFYING, 92, "Vérification de l'authenticité…");
                pa = PassiveAuthenticator.verify(sod, dataGroups, opts.cscaStore());
            } else {
                pa = PassiveAuthentication.notChecked("Vérification désactivée.");
            }

            CardData data = new CardData(dataGroups, sod, present, accessMethod);
            IdentityRecord record = IdentityAssembler.assemble(data, pa, aa, opts.includeRaw());
            progress.onProgress(Step.DONE, 100, "Lecture terminée.");
            return record;
        } finally {
            try {
                service.close();
            } catch (Exception ignored) {
                // fermeture best-effort
            }
        }
    }

    /** Décodage hors ligne de données déjà lues (même résultat qu'une lecture directe). */
    public static IdentityRecord decode(CardData data, ReadOptions options) {
        ReadOptions opts = options == null ? ReadOptions.defaults() : options;
        PassiveAuthentication pa = opts.verifyPassive() && data.sod() != null
                ? PassiveAuthenticator.verify(data.sod(), data.dataGroups(), opts.cscaStore())
                : PassiveAuthentication.notChecked("SOD non fourni : authenticité non vérifiée.");
        return IdentityAssembler.assemble(data, pa, opts.includeRaw());
    }

    private List<Integer> readPresentDataGroups(PassportService service) throws EidException {
        byte[] com = readFile(service, EF_COM, false);
        if (com == null) {
            return new ArrayList<>();
        }
        try {
            return ComParser.parseDataGroups(com);
        } catch (RuntimeException e) {
            LOG.log(Level.FINE, "EF.COM illisible", e);
            return new ArrayList<>();
        }
    }

    private byte[] readFile(PassportService service, short fid, boolean required) throws EidException {
        try (InputStream in = service.getInputStream(fid, PassportService.DEFAULT_MAX_BLOCKSIZE)) {
            return IoUtil.readFully(in);
        } catch (Exception e) {
            if (EidException.isCardLost(e)) {
                throw EidException.classify(e, ErrorCode.CARD_LOST, null);
            }
            if (required) {
                throw EidException.classify(e, ErrorCode.READ_ERROR,
                        String.format("Lecture impossible du fichier %04X de la puce.", fid));
            }
            LOG.log(Level.FINE, String.format("Fichier %04X absent ou illisible", fid), e);
            return null;
        }
    }

    /**
     * Active Authentication : envoie un défi aléatoire de 8 octets (INTERNAL AUTHENTICATE, sous secure
     * messaging) et vérifie la réponse avec la clé publique du DG15.
     */
    private static ActiveAuthentication activeAuthentication(PassportService service, byte[] dg15,
                                                             ReadOptions opts, ProgressListener progress)
            throws EidException {
        if (!opts.activeAuthentication()) {
            return ActiveAuthentication.notChecked("Contrôle anti-clonage désactivé.");
        }
        if (dg15 == null) {
            return ActiveAuthentication.notChecked(
                    "Cette carte ne propose pas le contrôle anti-clonage (pas de DG15).");
        }
        PublicKey key;
        try {
            key = new DG15File(new ByteArrayInputStream(dg15)).getPublicKey();
        } catch (Exception e) {
            LOG.info("Active Authentication : DG15 illisible (" + e + ")");
            return new ActiveAuthentication(Status.INVALID, null, "Clé anti-clonage (DG15) illisible.");
        }
        progress.onProgress(Step.VERIFYING, 82, "Contrôle anti-clonage de la puce…");
        byte[] challenge = new byte[8];
        RANDOM.nextBytes(challenge);
        try {
            boolean rsa = "RSA".equalsIgnoreCase(key.getAlgorithm());
            AAResult result = service.doAA(key, rsa ? "SHA-1" : "SHA-256",
                    rsa ? "SHA1WithRSA/ISO9796-2" : "SHA256withECDSA", challenge);
            ActiveAuthentication aa = ActiveAuthenticator.verify(key, challenge, result.getResponse());
            LOG.info("Active Authentication : " + aa.result() + " (" + aa.algorithm() + ")");
            return aa;
        } catch (Exception e) {
            if (EidException.isCardLost(e)) {
                throw EidException.classify(e, ErrorCode.CARD_LOST, null);
            }
            LOG.info("Active Authentication refusée par la puce : " + e);
            return new ActiveAuthentication(Status.INVALID, key.getAlgorithm(),
                    "La puce n'a pas répondu au défi anti-clonage alors qu'elle annonce ce contrôle (clone possible).");
        }
    }

    private static String label(int dg) {
        switch (dg) {
            case 1: return "Lecture des données MRZ…";
            case 2: return "Lecture de la photo…";
            case 7: return "Lecture de la signature manuscrite…";
            case 11: return "Lecture de l'identité (noms arabes, NIN)…";
            case 12: return "Lecture des informations du document…";
            case 15: return "Lecture de la clé anti-clonage…";
            default: return "Lecture du groupe de données " + dg + "…";
        }
    }
}
