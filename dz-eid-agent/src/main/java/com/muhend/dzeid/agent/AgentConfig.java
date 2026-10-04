package com.muhend.dzeid.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.muhend.dzeid.core.model.IdentityRecordJson;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/**
 * Configuration de l'agent ({@code config.json}).
 *
 * <p>Ordre de recherche : argument {@code --config <fichier>}, puis {@code ./config.json},
 * puis {@code %APPDATA%\dz-eid\config.json} (créé avec les valeurs par défaut s'il n'existe pas).</p>
 *
 * @param port               port d'écoute (sur 127.0.0.1 uniquement)
 * @param allowedOrigins     sites web autorisés à appeler l'agent, ex. {@code https://app.client.dz}
 * @param preferredReader    fragment du nom du lecteur à utiliser (vide = détection automatique)
 * @param cardWaitSeconds    délai d'attente de la carte lors d'une lecture
 * @param cscaDirectory      dossier des certificats CSCA de confiance (vide = aucun)
 * @param convertPhotoToJpeg convertir la photo JPEG2000 en JPEG (affichable par les navigateurs)
 */
public record AgentConfig(int port, List<String> allowedOrigins, String preferredReader, int cardWaitSeconds,
                          String cscaDirectory, boolean convertPhotoToJpeg, File source) {

    private static final Logger LOG = Logger.getLogger(AgentConfig.class.getName());

    public static final int DEFAULT_PORT = 8989;

    public static AgentConfig defaults() {
        return new AgentConfig(DEFAULT_PORT, Collections.emptyList(), "", 20, "", true, null);
    }

    public static AgentConfig load(String[] args) throws IOException {
        File explicit = null;
        for (int i = 0; i < args.length - 1; i++) {
            if ("--config".equals(args[i])) {
                explicit = new File(args[i + 1]);
            }
        }
        if (explicit != null) {
            if (!explicit.isFile()) {
                throw new IOException("Fichier de configuration introuvable : " + explicit.getAbsolutePath());
            }
            return read(explicit);
        }
        File local = new File("config.json");
        if (local.isFile()) {
            return read(local);
        }
        File user = userConfigFile();
        if (user.isFile()) {
            return read(user);
        }
        writeDefaults(user);
        LOG.info("Configuration par défaut créée : " + user.getAbsolutePath());
        return read(user);
    }

    static File userConfigFile() {
        String appData = System.getenv("APPDATA");
        File base = appData != null ? new File(appData) : new File(System.getProperty("user.home"), ".config");
        return new File(new File(base, "dz-eid"), "config.json");
    }

    static AgentConfig read(File file) throws IOException {
        JsonNode n = IdentityRecordJson.mapper().readTree(Files.readString(file.toPath(), StandardCharsets.UTF_8));
        AgentConfig d = defaults();
        List<String> origins = new ArrayList<>();
        for (JsonNode o : n.path("allowedOrigins")) {
            if (!o.asText().isBlank()) {
                origins.add(o.asText().trim());
            }
        }
        return new AgentConfig(
                n.path("port").asInt(d.port()),
                Collections.unmodifiableList(origins),
                n.path("preferredReader").asText(d.preferredReader()),
                Math.max(1, n.path("cardWaitSeconds").asInt(d.cardWaitSeconds())),
                n.path("cscaDirectory").asText(d.cscaDirectory()),
                n.path("convertPhotoToJpeg").asBoolean(d.convertPhotoToJpeg()),
                file);
    }

    static void writeDefaults(File file) throws IOException {
        ObjectNode n = IdentityRecordJson.mapper().createObjectNode();
        n.put("port", DEFAULT_PORT);
        ArrayNode origins = n.putArray("allowedOrigins");
        origins.add("http://localhost:4200");
        n.put("preferredReader", "");
        n.put("cardWaitSeconds", 20);
        n.put("cscaDirectory", "");
        n.put("convertPhotoToJpeg", true);
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Impossible de créer " + parent.getAbsolutePath());
        }
        Files.writeString(file.toPath(), IdentityRecordJson.toPrettyJson(n), StandardCharsets.UTF_8);
    }
}
