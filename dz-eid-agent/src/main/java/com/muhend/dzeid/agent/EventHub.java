package com.muhend.dzeid.agent;

import com.muhend.dzeid.core.model.IdentityRecordJson;
import io.javalin.websocket.WsContext;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Diffusion des événements temps réel aux clients WebSocket connectés.
 */
final class EventHub {

    private static final Logger LOG = Logger.getLogger(EventHub.class.getName());

    private final Set<WsContext> clients = ConcurrentHashMap.newKeySet();

    void add(WsContext ctx) {
        clients.add(ctx);
    }

    void remove(WsContext ctx) {
        clients.remove(ctx);
    }

    int size() {
        return clients.size();
    }

    void broadcast(String type, Map<String, Object> payload) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", type);
        event.put("timestamp", Instant.now().toString());
        if (payload != null) {
            event.putAll(payload);
        }
        String json = IdentityRecordJson.toJson(event);
        for (WsContext c : clients) {
            try {
                if (c.session.isOpen()) {
                    c.send(json);
                }
            } catch (Exception e) {
                LOG.log(Level.FINE, "Client WebSocket injoignable", e);
                clients.remove(c);
            }
        }
    }

    static Map<String, Object> payload(Object... keyValues) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            m.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return m;
    }
}
