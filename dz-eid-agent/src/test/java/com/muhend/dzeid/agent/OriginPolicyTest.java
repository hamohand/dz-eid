package com.muhend.dzeid.agent;

import com.muhend.dzeid.core.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OriginPolicyTest {

    private final OriginPolicy policy = new OriginPolicy(8989,
            Arrays.asList("https://app.client.dz/", "http://localhost:4200", "*"));

    @Test
    void acceptsSelfAndConfiguredOrigins() {
        assertTrue(policy.isOriginAllowed("http://127.0.0.1:8989"));
        assertTrue(policy.isOriginAllowed("http://localhost:8989"));
        assertTrue(policy.isOriginAllowed("https://app.client.dz"));
        assertTrue(policy.isOriginAllowed("HTTPS://APP.CLIENT.DZ"));
        assertTrue(policy.isOriginAllowed("http://localhost:4200"));
    }

    @Test
    void rejectsUnknownOriginsAndWildcard() {
        assertFalse(policy.isOriginAllowed("https://site-malveillant.example"));
        assertFalse(policy.isOriginAllowed("http://localhost:4201"));
        assertFalse(policy.isOriginAllowed("null"));
        assertFalse(policy.allowedOrigins().contains("*"));
    }

    @Test
    void acceptsNonBrowserClients() {
        assertTrue(policy.isOriginAllowed(null));
    }

    @Test
    void blocksDnsRebinding() {
        assertTrue(policy.isHostAllowed("127.0.0.1:8989"));
        assertTrue(policy.isHostAllowed("localhost:8989"));
        assertFalse(policy.isHostAllowed("attacker.example:8989"));
        assertFalse(policy.isHostAllowed(null));
    }

    @Test
    void mapsErrorCodesToHttpStatus() {
        assertEquals(400, AgentServer.httpStatus(ErrorCode.INVALID_INPUT));
        assertEquals(422, AgentServer.httpStatus(ErrorCode.ACCESS_DENIED));
        assertEquals(503, AgentServer.httpStatus(ErrorCode.NO_READER));
        assertEquals(409, AgentServer.httpStatus(ErrorCode.BUSY));
    }

    @Test
    void defaultPolicyOnlyAllowsSelf() {
        OriginPolicy p = new OriginPolicy(9000, Collections.emptyList());
        assertEquals(2, p.allowedOrigins().size());
    }
}
