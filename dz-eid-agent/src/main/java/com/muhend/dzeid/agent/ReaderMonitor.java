package com.muhend.dzeid.agent;

import com.muhend.dzeid.core.EidException;

import javax.smartcardio.CardTerminal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Surveillance des lecteurs et de la présence de carte, diffusée en temps réel.
 *
 * <p>Événements : {@code READERS_CHANGED}, {@code CARD_INSERTED}, {@code CARD_REMOVED}, {@code HEARTBEAT}.
 * La surveillance est suspendue pendant une lecture pour ne pas perturber la communication.</p>
 */
final class ReaderMonitor implements Runnable {

    private static final Logger LOG = Logger.getLogger(ReaderMonitor.class.getName());
    private static final long PERIOD_MS = 1000;
    private static final long HEARTBEAT_MS = 20_000;

    private final PcscReaders readers;
    private final EventHub hub;
    private final BooleanSupplier paused;

    private volatile List<Map<String, Object>> snapshot = new ArrayList<>();
    private volatile boolean running = true;

    ReaderMonitor(PcscReaders readers, EventHub hub, BooleanSupplier paused) {
        this.readers = readers;
        this.hub = hub;
        this.paused = paused;
    }

    /** Dernier état connu : liste de {@code {name, cardPresent}}. */
    List<Map<String, Object>> snapshot() {
        return snapshot;
    }

    void stop() {
        running = false;
    }

    @Override
    public void run() {
        Map<String, Boolean> previous = new HashMap<>();
        long lastHeartbeat = System.currentTimeMillis();
        while (running) {
            try {
                if (!paused.getAsBoolean()) {
                    Map<String, Boolean> current = poll();
                    if (!Objects.equals(previous.keySet(), current.keySet())) {
                        hub.broadcast("READERS_CHANGED", EventHub.payload("readers", snapshot));
                    }
                    for (Map.Entry<String, Boolean> e : current.entrySet()) {
                        Boolean before = previous.get(e.getKey());
                        if (Boolean.TRUE.equals(e.getValue()) && !Boolean.TRUE.equals(before)) {
                            hub.broadcast("CARD_INSERTED", EventHub.payload("reader", e.getKey()));
                        } else if (!e.getValue() && Boolean.TRUE.equals(before)) {
                            hub.broadcast("CARD_REMOVED", EventHub.payload("reader", e.getKey()));
                        }
                    }
                    previous = current;
                }
                long now = System.currentTimeMillis();
                if (now - lastHeartbeat >= HEARTBEAT_MS) {
                    hub.broadcast("HEARTBEAT", null);
                    lastHeartbeat = now;
                }
                Thread.sleep(PERIOD_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                LOG.log(Level.FINE, "Erreur de surveillance des lecteurs", e);
            }
        }
    }

    private Map<String, Boolean> poll() {
        Map<String, Boolean> state = new LinkedHashMap<>();
        List<Map<String, Object>> list = new ArrayList<>();
        try {
            for (CardTerminal t : readers.list()) {
                boolean present = PcscReaders.isCardPresent(t);
                state.put(t.getName(), present);
                list.add(EventHub.payload("name", t.getName(), "cardPresent", present));
            }
        } catch (EidException e) {
            // aucun lecteur ou service indisponible : liste vide
        }
        snapshot = list;
        return state;
    }
}
