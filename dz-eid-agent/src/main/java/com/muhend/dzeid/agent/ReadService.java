package com.muhend.dzeid.agent;

import com.muhend.dzeid.core.AccessKey;
import com.muhend.dzeid.core.EidException;
import com.muhend.dzeid.core.EidReader;
import com.muhend.dzeid.core.ErrorCode;
import com.muhend.dzeid.core.ReadOptions;
import com.muhend.dzeid.core.model.IdentityRecord;
import com.muhend.dzeid.core.model.IdentityRecord.PassiveAuthentication;
import com.muhend.dzeid.core.verify.CscaStore;
import net.sf.scuba.smartcards.TerminalCardService;

import javax.smartcardio.CardException;
import javax.smartcardio.CardTerminal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

/**
 * Orchestration d'une lecture : choix du lecteur, attente de la carte, lecture, conversion photo.
 * Une seule lecture à la fois.
 */
final class ReadService {

    private static final Logger LOG = Logger.getLogger(ReadService.class.getName());

    private final AgentConfig config;
    private final PcscReaders readers;
    private final EventHub hub;
    private final CscaStore cscaStore;
    private final ReentrantLock lock = new ReentrantLock();

    ReadService(AgentConfig config, PcscReaders readers, EventHub hub, CscaStore cscaStore) {
        this.config = config;
        this.readers = readers;
        this.hub = hub;
        this.cscaStore = cscaStore;
    }

    boolean isBusy() {
        return lock.isLocked();
    }

    IdentityRecord read(AccessKey key, boolean readPhoto, boolean includeRaw) throws EidException {
        if (!lock.tryLock()) {
            throw new EidException(ErrorCode.BUSY, "Une lecture est déjà en cours.");
        }
        long start = System.currentTimeMillis();
        try {
            CardTerminal terminal = waitForCard(readers.candidates(config.preferredReader()));
            LOG.info("Carte détectée sur le lecteur : " + terminal.getName());

            TerminalCardService card = new TerminalCardService(terminal);
            ReadOptions options = new ReadOptions(readPhoto, includeRaw, true, cscaStore);
            try {
                IdentityRecord record = new EidReader().read(card, key, options, (step, percent, message) ->
                        hub.broadcast("PROGRESS", EventHub.payload("step", step.name(), "percent", percent,
                                "message", message)));
                if (config.convertPhotoToJpeg() && record.photo() != null) {
                    record = record.withPhoto(PhotoConverter.toJpeg(record.photo()));
                }
                PassiveAuthentication pa = record.verification().passiveAuthentication();
                // Journal sans aucune donnée personnelle
                LOG.info(String.format("Lecture réussie en %d ms (%s, DG %s, PA intégrité=%s signature=%s chaîne=%s)",
                        System.currentTimeMillis() - start, record.verification().accessMethod(),
                        record.verification().dataGroupsRead(), pa.dataIntegrity(), pa.signature(), pa.certificateChain()));
                hub.broadcast("READ_COMPLETED", EventHub.payload("durationMs", System.currentTimeMillis() - start));
                return record;
            } finally {
                try {
                    card.close();
                } catch (Exception ignored) {
                    // fermeture best-effort
                }
            }
        } catch (EidException e) {
            Throwable cause = e.getCause();
            LOG.info("Lecture échouée : " + e.code() + " - " + e.getMessage()
                    + (cause != null ? " (cause : " + cause + ")" : ""));
            hub.broadcast("READ_FAILED", EventHub.payload("code", e.code().name(), "message", e.getMessage()));
            throw e;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Attend qu'une carte soit posée sur l'un des lecteurs candidats et renvoie ce lecteur.
     * On interroge chaque lecteur par {@code isCardPresent} (pas de {@code waitForCardPresent},
     * qui bloque sur un seul lecteur et lève une exception sur certains pilotes).
     */
    private CardTerminal waitForCard(List<CardTerminal> candidates) throws EidException {
        CardTerminal found = firstWithCard(candidates);
        if (found != null) {
            return found;
        }
        List<String> names = new ArrayList<>();
        for (CardTerminal t : candidates) {
            names.add(t.getName());
        }
        hub.broadcast("WAITING_FOR_CARD", EventHub.payload("readers", names,
                "timeoutSeconds", config.cardWaitSeconds()));
        long deadline = System.currentTimeMillis() + config.cardWaitSeconds() * 1000L;
        long nextRefresh = System.currentTimeMillis() + 5000L;
        while (System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new EidException(ErrorCode.NO_CARD, "Attente de la carte interrompue.", e);
            }
            if (System.currentTimeMillis() >= nextRefresh) {
                // Filet de sécurité : contexte PC/SC périmé (lecteur rebranché, service redémarré)
                PcscReaders.resetPcscContext();
                try {
                    candidates = readers.candidates(config.preferredReader());
                } catch (EidException e) {
                    // lecteur momentanément absent : on continue d'attendre
                }
                nextRefresh = System.currentTimeMillis() + 5000L;
            }
            found = firstWithCard(candidates);
            if (found != null) {
                return found;
            }
        }
        throw new EidException(ErrorCode.NO_CARD,
                "Aucune carte détectée. Posez la carte à plat sur le lecteur et réessayez.");
    }

    private static CardTerminal firstWithCard(List<CardTerminal> candidates) {
        for (CardTerminal t : candidates) {
            try {
                if (t.isCardPresent()) {
                    return t;
                }
            } catch (CardException | RuntimeException e) {
                // Lecteur défaillant ou débranché : on l'ignore et on continue avec les autres
                LOG.fine("Lecteur ignoré (" + t.getName() + ") : " + e);
            }
        }
        return null;
    }
}
