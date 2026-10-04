package com.muhend.dzeid.core;

import com.muhend.dzeid.core.ProgressListener.Step;
import com.muhend.dzeid.core.model.IdentityRecord;
import com.muhend.dzeid.core.model.IdentityRecord.PassiveAuthentication;
import com.muhend.dzeid.core.parse.ComParser;
import com.muhend.dzeid.core.util.IoUtil;
import com.muhend.dzeid.core.verify.PassiveAuthenticator;
import net.sf.scuba.smartcards.CardService;
import net.sf.scuba.smartcards.CardServiceException;
import org.jmrtd.PassportService;

import java.io.InputStream;
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

    public IdentityRecord read(CardService cardService, AccessKey key, ReadOptions options,
                               ProgressListener listener) throws EidException {
        ProgressListener progress = listener == null ? ProgressListener.NONE : listener;
        ReadOptions opts = options == null ? ReadOptions.defaults() : options;

        PassportService service;
        try {
            progress.onProgress(Step.CONNECTING, 5, "Connexion à la puce…");
            service = new PassportService(cardService, PassportService.NORMAL_MAX_TRANCEIVE_LENGTH,
                    PassportService.DEFAULT_MAX_BLOCKSIZE, false, false);
            service.open();
        } catch (CardServiceException e) {
            throw EidException.classify(e, ErrorCode.READ_ERROR, "Impossible d'ouvrir la communication avec la puce.");
        }

        try {
            progress.onProgress(Step.AUTHENTICATING, 15, "Authentification auprès de la puce…");
            String accessMethod = AccessControl.authenticate(service, key);

            progress.onProgress(Step.READING, 25, "Lecture du sommaire…");
            List<Integer> present = readPresentDataGroups(service);

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

            byte[] sod = null;
            if (opts.verifyPassive()) {
                progress.onProgress(Step.READING, 82, "Lecture de la signature électronique…");
                sod = readFile(service, EF_SOD, false);
            }

            PassiveAuthentication pa;
            if (opts.verifyPassive()) {
                progress.onProgress(Step.VERIFYING, 90, "Vérification de l'authenticité…");
                pa = PassiveAuthenticator.verify(sod, dataGroups, opts.cscaStore());
            } else {
                pa = PassiveAuthentication.notChecked("Vérification désactivée.");
            }

            CardData data = new CardData(dataGroups, sod, present, accessMethod);
            IdentityRecord record = IdentityAssembler.assemble(data, pa, opts.includeRaw());
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

    private static String label(int dg) {
        switch (dg) {
            case 1: return "Lecture des données MRZ…";
            case 2: return "Lecture de la photo…";
            case 11: return "Lecture de l'identité (noms arabes, NIN)…";
            case 12: return "Lecture des informations du document…";
            default: return "Lecture du groupe de données " + dg + "…";
        }
    }
}
