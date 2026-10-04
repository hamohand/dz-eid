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
            CardTerminal terminal = readers.select(config.preferredReader());
            waitForCard(terminal);

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
            LOG.info("Lecture échouée : " + e.code());
            hub.broadcast("READ_FAILED", EventHub.payload("code", e.code().name(), "message", e.getMessage()));
            throw e;
        } finally {
            lock.unlock();
        }
    }

    private void waitForCard(CardTerminal terminal) throws EidException {
        if (PcscReaders.isCardPresent(terminal)) {
            return;
        }
        hub.broadcast("WAITING_FOR_CARD", EventHub.payload("reader", terminal.getName(),
                "timeoutSeconds", config.cardWaitSeconds()));
        long deadline = System.currentTimeMillis() + config.cardWaitSeconds() * 1000L;
        while (System.currentTimeMillis() < deadline) {
            try {
                if (terminal.waitForCardPresent(500)) {
                    return;
                }
            } catch (CardException e) {
                throw new EidException(ErrorCode.NO_READER, "Le lecteur ne répond plus.", e);
            }
        }
        throw new EidException(ErrorCode.NO_CARD,
                "Aucune carte détectée. Posez la carte à plat sur le lecteur et réessayez.");
    }
}
