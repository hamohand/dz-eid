package com.muhend.dzeid.agent;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Politique de sécurité réseau de l'agent.
 *
 * <ul>
 *   <li><b>Origine</b> : seuls les sites explicitement autorisés peuvent appeler l'agent depuis un navigateur.
 *       Un site malveillant visité par l'utilisateur est refusé, même pour les WebSocket (non couverts par CORS).</li>
 *   <li><b>Hôte</b> : l'en-tête {@code Host} doit désigner la machine locale, ce qui bloque les attaques
 *       par « DNS rebinding ».</li>
 *   <li>Les clients non-navigateurs (sans en-tête {@code Origin}) sont des programmes déjà exécutés
 *       sur le poste : ils sont acceptés.</li>
 * </ul>
 */
public final class OriginPolicy {

    private final Set<String> allowedOrigins;
    private final Set<String> allowedHosts;

    public OriginPolicy(int port, Collection<String> extraOrigins) {
        Set<String> origins = new HashSet<>();
        origins.add("http://127.0.0.1:" + port);
        origins.add("http://localhost:" + port);
        for (String o : extraOrigins) {
            String n = normalize(o);
            if (n != null && !"*".equals(n)) { // le joker est volontairement interdit
                origins.add(n);
            }
        }
        this.allowedOrigins = Collections.unmodifiableSet(origins);

        Set<String> hosts = new HashSet<>();
        hosts.add("127.0.0.1:" + port);
        hosts.add("localhost:" + port);
        hosts.add("[::1]:" + port);
        this.allowedHosts = Collections.unmodifiableSet(hosts);
    }

    public boolean isOriginAllowed(String origin) {
        if (origin == null || origin.isEmpty()) {
            return true;
        }
        return allowedOrigins.contains(normalize(origin));
    }

    public boolean isHostAllowed(String host) {
        return host != null && allowedHosts.contains(host.trim().toLowerCase(Locale.ROOT));
    }

    public Set<String> allowedOrigins() {
        return allowedOrigins;
    }

    private static String normalize(String origin) {
        if (origin == null) {
            return null;
        }
        String o = origin.trim().toLowerCase(Locale.ROOT);
        while (o.endsWith("/")) {
            o = o.substring(0, o.length() - 1);
        }
        return o.isEmpty() ? null : o;
    }
}
