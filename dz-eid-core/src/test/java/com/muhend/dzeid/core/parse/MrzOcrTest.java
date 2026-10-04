package com.muhend.dzeid.core.parse;

import com.muhend.dzeid.core.EidReader;
import com.muhend.dzeid.core.CardData;
import com.muhend.dzeid.core.ReadOptions;
import com.muhend.dzeid.core.model.IdentityRecord;
import com.muhend.dzeid.core.model.IdentityRecordJson;
import com.muhend.dzeid.core.model.IdentityRecordMaps;
import com.muhend.dzeid.core.testing.DzCardFixtures;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MrzOcrTest {

    private static final String ICAO_TD1 = "I<UTOD231458907<<<<<<<<<<<<<<<\n"
            + "7408122F1204159UTO<<<<<<<<<<<6\n"
            + "ERIKSSON<<ANNA<MARIA<<<<<<<<<<";
    private static final String ICAO_TD3 = "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<\n"
            + "L898902C36UTO7408122F1204159ZE184226B<<<<<10";
    /** MRZ au format des CNIe algériennes (identité fictive, chiffres de contrôle calculés). */
    private static final String DZ_TD1 = String.join("\n", MrzParser.parseDg1(DzCardFixtures.dg1()).lines());

    @Test
    void mrzPropreRenvoyeeTelleQuelle() {
        assertEquals(ICAO_TD1, MrzOcr.extract(ICAO_TD1));
        assertEquals(ICAO_TD3, MrzOcr.extract(ICAO_TD3));
    }

    @Test
    void textesParasitesEtEspacesIgnores() {
        String ocr = "REPUBLIQUE ALGERIENNE\nCARTE D'IDENTITE\n"
                + "I<UTO D23145890 7<<<<<<<<<<<<<<<\n"
                + "7408122F1204159UTO<<<<<<<<<<<6\n"
                + "ERIKSSON<<ANNA<MARIA<<<<<<<<<<\nsignature";
        assertEquals(ICAO_TD1, MrzOcr.extract(ocr));
    }

    @Test
    void confusionsOcrCorrigeesSelonLaPosition() {
        // O au lieu de 0 dans les dates, 5 au lieu de S dans le nom, chevrons typographiques
        String ocr = "I<UTOD231458907«««<<<<<<<<<<<<\n"
                + "74O8122F12O4159UTO<<<<<<<<<<<6\n"
                + "ERIK5SON<<ANNA<MARIA<<<<<<<<<<";
        assertEquals(ICAO_TD1, MrzOcr.extract(ocr));
    }

    @Test
    void chevronsDeFinPerdusCompletes() {
        String ocr = "I<UTOD231458907<<<<<<\n"
                + "7408122F1204159UTO<<<<<<<<<<<6\n"
                + "ERIKSSON<<ANNA<MARIA";
        assertEquals(ICAO_TD1, MrzOcr.extract(ocr));
    }

    @Test
    void numeroAlgerienNumeriqueCorrige() {
        String ocr = DZ_TD1.replace(DzCardFixtures.DOC_NUMBER, "12345G7B9");
        assertEquals(DZ_TD1, MrzOcr.extract(ocr));
    }

    @Test
    void chiffreDeControleFauxRefuse() {
        // Une erreur d'OCR non corrigeable (7 lu comme 1) : aucune MRZ ne doit être renvoyée
        String ocr = ICAO_TD1.replace("7408122F", "1408122F");
        assertNull(MrzOcr.extract(ocr));
        assertNull(MrzOcr.extract("texte sans MRZ"));
        assertNull(MrzOcr.extract(null));
    }

    @Test
    void conversionMapIdentiqueAuJson() throws Exception {
        Map<Integer, byte[]> dgs = new HashMap<>();
        dgs.put(1, DzCardFixtures.dg1());
        dgs.put(11, DzCardFixtures.dg11());
        dgs.put(12, DzCardFixtures.dg12());
        IdentityRecord r = EidReader.decode(new CardData(dgs, null, Arrays.asList(1, 11, 12), "OFFLINE"),
                ReadOptions.defaults());

        Map<String, Object> viaJson = IdentityRecordJson.mapper().readValue(IdentityRecordJson.toJson(r),
                new TypeReference<Map<String, Object>>() { });
        assertEquals(viaJson, IdentityRecordMaps.toMap(r));
    }
}
