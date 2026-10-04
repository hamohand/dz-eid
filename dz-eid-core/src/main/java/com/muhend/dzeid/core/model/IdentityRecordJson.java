package com.muhend.dzeid.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Sérialisation JSON officielle du contrat {@link IdentityRecord}.
 * Utiliser cette classe garantit un JSON identique sur toutes les plateformes.
 */
public final class IdentityRecordJson {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

    private IdentityRecordJson() {
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    public static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Sérialisation JSON impossible", e);
        }
    }

    public static String toPrettyJson(Object value) {
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Sérialisation JSON impossible", e);
        }
    }

    public static IdentityRecord fromJson(String json) {
        try {
            return MAPPER.readValue(json, IdentityRecord.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("JSON IdentityRecord invalide", e);
        }
    }
}
