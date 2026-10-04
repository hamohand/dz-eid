package com.muhend.dzeid.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.muhend.dzeid.core.AccessKey;
import com.muhend.dzeid.core.EidException;
import com.muhend.dzeid.core.ErrorCode;
import com.muhend.dzeid.core.model.IdentityRecord;
import com.muhend.dzeid.core.model.IdentityRecordJson;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.staticfiles.Location;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Serveur HTTP/WebSocket de l'agent (API v1).
 *
 * <ul>
 *   <li>{@code GET  /v1/status} — état de l'agent et des lecteurs ;</li>
 *   <li>{@code POST /v1/read}   — lecture d'une carte, renvoie un {@code IdentityRecord} ;</li>
 *   <li>{@code WS   /v1/events} — événements temps réel ;</li>
 *   <li>{@code GET  /demo/}     — page de démonstration.</li>
 * </ul>
 */
final class AgentServer {

    private static final Logger LOG = Logger.getLogger(AgentServer.class.getName());
    private static final String JSON = "application/json; charset=utf-8";
    private static final com.fasterxml.jackson.databind.ObjectMapper PLAIN = new com.fasterxml.jackson.databind.ObjectMapper();

    private AgentServer() {
    }

    static Javalin create(AgentConfig config, OriginPolicy policy, ReadService readService, ReaderMonitor monitor,
                          EventHub hub, int cscaCount, String version) {
        Javalin app = Javalin.create(cfg -> {
            cfg.showJavalinBanner = false;
            cfg.staticFiles.add(sf -> {
                sf.hostedPath = "/demo";
                sf.directory = "/public";
                sf.location = Location.CLASSPATH;
            });
        });

        // Sécurité : hôte local uniquement + origine autorisée, avant tout traitement
        app.before(ctx -> {
            if (!policy.isHostAllowed(ctx.header("Host"))) {
                throw new ForbiddenResponse("Hôte non autorisé");
            }
            String origin = ctx.header("Origin");
            if (!policy.isOriginAllowed(origin)) {
                LOG.warning("Requête refusée depuis l'origine " + origin);
                throw new ForbiddenResponse("Origine non autorisée : " + origin);
            }
            if (origin != null) {
                ctx.header("Access-Control-Allow-Origin", origin);
                ctx.header("Vary", "Origin");
                ctx.header("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
                ctx.header("Access-Control-Allow-Headers", "Content-Type");
                ctx.header("Access-Control-Max-Age", "600");
                // Chrome « Private Network Access » : un site public appelant localhost
                if ("true".equalsIgnoreCase(ctx.header("Access-Control-Request-Private-Network"))) {
                    ctx.header("Access-Control-Allow-Private-Network", "true");
                }
            }
            ctx.header("Cache-Control", "no-store");
        });

        app.options("/*", ctx -> ctx.status(204));
        app.get("/", ctx -> ctx.redirect("/demo/"));

        app.get("/v1/status", ctx -> {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("product", "dz-eid-agent");
            body.put("version", version);
            body.put("apiVersion", "1");
            body.put("busy", readService.isBusy());
            body.put("readers", monitor.snapshot());
            body.put("preferredReader", config.preferredReader());
            body.put("cscaCertificates", cscaCount);
            // Mapper standard : la liste « readers » reste présente même vide
            ctx.status(200).contentType(JSON).result(PLAIN.writeValueAsString(body));
        });

        app.post("/v1/read", ctx -> {
            JsonNode req;
            try {
                req = IdentityRecordJson.mapper().readTree(ctx.body());
            } catch (Exception e) {
                throw new EidException(ErrorCode.INVALID_INPUT, "Corps de requête JSON invalide.");
            }
            if (req == null || !req.isObject()) {
                throw new EidException(ErrorCode.INVALID_INPUT, "Corps de requête JSON attendu.");
            }
            AccessKey key = req.hasNonNull("mrz")
                    ? AccessKey.fromMrz(req.get("mrz").asText())
                    : AccessKey.of(text(req, "documentNumber"), text(req, "dateOfBirth"), text(req, "dateOfExpiry"));
            boolean readPhoto = req.path("readPhoto").asBoolean(true);
            boolean readSignature = req.path("readSignature").asBoolean(true);
            boolean includeRaw = req.path("includeRaw").asBoolean(false);
            IdentityRecord record = readService.read(key, readPhoto, readSignature, includeRaw);
            json(ctx, 200, record);
        });

        app.ws("/v1/events", ws -> {
            ws.onConnect(ctx -> {
                if (!policy.isHostAllowed(ctx.header("Host")) || !policy.isOriginAllowed(ctx.header("Origin"))) {
                    LOG.warning("WebSocket refusé depuis l'origine " + ctx.header("Origin"));
                    ctx.closeSession(4003, "Origine non autorisée");
                    return;
                }
                hub.add(ctx);
                ctx.send(IdentityRecordJson.toJson(EventHub.payload("type", "HELLO", "version", version,
                        "readers", monitor.snapshot())));
            });
            ws.onClose(hub::remove);
            ws.onError(hub::remove);
        });

        app.exception(EidException.class, (e, ctx) -> error(ctx, e.code(), e.getMessage()));
        app.exception(Exception.class, (e, ctx) -> {
            LOG.log(Level.SEVERE, "Erreur interne", e);
            error(ctx, ErrorCode.INTERNAL, "Erreur interne de l'agent.");
        });
        return app;
    }

    static int httpStatus(ErrorCode code) {
        switch (code) {
            case INVALID_INPUT: return 400;
            case NO_CARD: return 408;
            case BUSY: return 409;
            case ACCESS_DENIED:
            case NOT_EMRTD:
            case CARD_LOST:
            case READ_ERROR: return 422;
            case NO_READER: return 503;
            default: return 500;
        }
    }

    private static void error(Context ctx, ErrorCode code, String message) {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("code", code.name());
        err.put("message", message);
        json(ctx, httpStatus(code), EventHub.payload("error", err));
    }

    private static void json(Context ctx, int status, Object body) {
        ctx.status(status).contentType(JSON).result(IdentityRecordJson.toJson(body));
    }

    private static String text(JsonNode n, String field) {
        return n.hasNonNull(field) ? n.get(field).asText() : null;
    }
}
