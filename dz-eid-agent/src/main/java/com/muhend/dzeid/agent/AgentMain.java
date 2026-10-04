package com.muhend.dzeid.agent;

import com.muhend.dzeid.core.verify.CscaStore;
import io.javalin.Javalin;

import java.io.File;
import java.util.logging.Logger;

/**
 * Point d'entrée de l'agent dz-eid.
 *
 * <pre>
 *   gradlew :dz-eid-agent:run
 *   gradlew :dz-eid-agent:run --args="--config C:\chemin\config.json"
 * </pre>
 */
public final class AgentMain {

    private static final Logger LOG = Logger.getLogger(AgentMain.class.getName());

    private AgentMain() {
    }

    public static void main(String[] args) {
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "warn");
        try {
            AgentConfig config = AgentConfig.load(args);
            String version = AgentMain.class.getPackage().getImplementationVersion();
            if (version == null) {
                version = "dev";
            }

            CscaStore csca = config.cscaDirectory().isBlank()
                    ? CscaStore.empty() : CscaStore.fromDirectory(new File(config.cscaDirectory()));

            OriginPolicy policy = new OriginPolicy(config.port(), config.allowedOrigins());
            PcscReaders readers = new PcscReaders();
            EventHub hub = new EventHub();
            ReadService readService = new ReadService(config, readers, hub, csca);
            ReaderMonitor monitor = new ReaderMonitor(readers, hub, readService::isBusy);

            Thread monitorThread = new Thread(monitor, "dz-eid-reader-monitor");
            monitorThread.setDaemon(true);
            monitorThread.start();

            Javalin app = AgentServer.create(config, policy, readService, monitor, hub, csca.size(), version);
            app.start("127.0.0.1", config.port());

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                monitor.stop();
                app.stop();
            }, "dz-eid-shutdown"));

            String source = config.source() == null ? "valeurs par défaut" : config.source().getAbsolutePath();
            System.out.println();
            System.out.println("  dz-eid agent " + version + " démarré");
            System.out.println("  API      : http://127.0.0.1:" + config.port() + "/v1/status");
            System.out.println("  Démo     : http://127.0.0.1:" + config.port() + "/demo/");
            System.out.println("  Config   : " + source);
            System.out.println("  Origines : " + policy.allowedOrigins());
            System.out.println("  CSCA     : " + csca.size() + " certificat(s) de confiance");
            System.out.println();
        } catch (Exception e) {
            String msg = String.valueOf(e.getMessage());
            if (msg.contains("Address already in use") || msg.contains("Failed to bind")
                    || (e.getCause() != null && String.valueOf(e.getCause().getMessage()).contains("Address already in use"))) {
                System.err.println("Le port est déjà utilisé : un autre agent dz-eid est peut-être déjà lancé.");
            } else {
                System.err.println("Démarrage de l'agent impossible : " + msg);
            }
            LOG.fine(String.valueOf(e));
            System.exit(1);
        }
    }
}
