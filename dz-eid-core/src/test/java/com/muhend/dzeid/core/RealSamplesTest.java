package com.muhend.dzeid.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.muhend.dzeid.core.model.IdentityRecord;
import com.muhend.dzeid.core.model.IdentityRecordJson;
import com.muhend.dzeid.core.text.DzText;
import com.muhend.dzeid.core.util.Hex;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Non-régression sur de vraies cartes, à partir de {@code private-fixtures/cni-reelles.json}
 * (dossier ignoré par git : ces données personnelles ne quittent jamais le poste de développement).
 *
 * <p>Le test est ignoré si le fichier est absent. Il ne contient aucune valeur personnelle :
 * il vérifie uniquement la cohérence et la complétude du décodage.</p>
 */
class RealSamplesTest {

    @Test
    void decodesEveryRealSample() throws Exception {
        File file = new File("../private-fixtures/cni-reelles.json");
        Assumptions.assumeTrue(file.exists(), "Pas d'échantillons réels : test ignoré");

        JsonNode samples = IdentityRecordJson.mapper().readTree(file);
        int index = 0;
        for (JsonNode s : samples) {
            index++;
            Map<Integer, byte[]> dgs = new HashMap<>();
            dgs.put(1, Hex.decode(s.path("dg1_hex").asText()));
            dgs.put(11, Hex.decode(s.path("dg11_hex").asText()));
            if (s.hasNonNull("dg12_hex")) {
                dgs.put(12, Hex.decode(s.path("dg12_hex").asText()));
            }
            IdentityRecord r = EidReader.decode(new CardData(dgs, null, null, "OFFLINE"), ReadOptions.defaults());
            String ctx = "échantillon " + index;

            assertTrue(r.mrz().checkDigitsValid(), ctx + " : MRZ");
            assertTrue(r.warnings().isEmpty(), ctx + " : avertissements " + r.warnings());
            assertNotNull(r.holder().nin(), ctx + " : NIN");
            assertTrue(r.holder().nin().matches("\\d{18}"), ctx + " : NIN sur 18 chiffres");
            assertTrue(DzText.containsArabic(r.holder().lastNameArabic()), ctx + " : nom arabe");
            assertTrue(DzText.containsArabic(r.holder().firstNameArabic()), ctx + " : prénom arabe");
            assertTrue(DzText.containsArabic(r.holder().placeOfBirthArabic()), ctx + " : lieu de naissance arabe");
            assertNotNull(r.holder().lastNameLatin(), ctx + " : nom latin");
            assertNotNull(r.holder().firstNameLatin(), ctx + " : prénom latin");
            assertNotNull(r.holder().sex(), ctx + " : sexe");
            assertNotNull(r.holder().bloodGroup(), ctx + " : groupe sanguin");
            assertTrue(r.holder().dateOfBirth().matches("\\d{4}-\\d{2}-\\d{2}"), ctx + " : date de naissance");
            if (dgs.containsKey(12)) {
                assertNotNull(r.document().dateOfIssue(), ctx + " : date de délivrance");
                assertTrue(DzText.containsArabic(r.document().issuingAuthorityArabic()), ctx + " : autorité arabe");
                assertEquals("بطاقة التعريف الوطنية", r.document().nameArabic(), ctx + " : intitulé");
            }
            // Affichage local uniquement (jamais versionné)
            System.out.println("=== " + ctx + " ===\n" + IdentityRecordJson.toPrettyJson(r));
        }
        assertTrue(index > 0, "Aucun échantillon dans le fichier");
    }
}
