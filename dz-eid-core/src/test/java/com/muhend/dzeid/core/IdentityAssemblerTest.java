package com.muhend.dzeid.core;

import com.muhend.dzeid.core.model.IdentityRecord;
import com.muhend.dzeid.core.model.IdentityRecordJson;
import com.muhend.dzeid.core.testing.DzCardFixtures;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdentityAssemblerTest {

    private static CardData fakeCard() {
        Map<Integer, byte[]> dgs = new HashMap<>();
        dgs.put(1, DzCardFixtures.dg1());
        dgs.put(11, DzCardFixtures.dg11());
        dgs.put(12, DzCardFixtures.dg12());
        return new CardData(dgs, null, Arrays.asList(1, 2, 11, 12), "OFFLINE");
    }

    @Test
    void assemblesCompleteAlgerianIdentity() {
        IdentityRecord r = EidReader.decode(fakeCard(), ReadOptions.defaults());

        assertEquals("1.0", r.schemaVersion());
        assertEquals("ID_CARD", r.document().type());
        assertEquals(DzCardFixtures.DOC_NUMBER, r.document().number());
        assertEquals("DZA", r.document().issuingState());
        assertEquals("2021-05-21", r.document().dateOfIssue());
        assertEquals("2031-05-20", r.document().dateOfExpiry());
        assertEquals("بلدية باب الواد-الجزائر", r.document().issuingAuthorityArabic());
        assertEquals("بطاقة التعريف الوطنية", r.document().nameArabic());

        assertEquals(DzCardFixtures.NIN, r.holder().nin());
        assertEquals("BENALI", r.holder().lastNameLatin());
        assertEquals("AMINA", r.holder().firstNameLatin());
        assertEquals("بن علي", r.holder().lastNameArabic());
        assertEquals("أمينة", r.holder().firstNameArabic());
        assertEquals("F", r.holder().sex());
        assertEquals("أنثى", r.holder().sexArabic());
        assertEquals("1985-03-12", r.holder().dateOfBirth());
        assertEquals("TIPAZA", r.holder().placeOfBirthLatin());
        assertEquals("تيبازة", r.holder().placeOfBirthArabic());
        assertEquals("DZA", r.holder().nationality());
        assertEquals("B-", r.holder().bloodGroup());

        assertTrue(r.mrz().checkDigitsValid());
        assertTrue(r.warnings().isEmpty(), "avertissements : " + r.warnings());
        assertEquals(IdentityRecord.Status.NOT_CHECKED, r.verification().passiveAuthentication().signature());
        assertNull(r.raw());
    }

    @Test
    void includesRawDataOnRequest() {
        IdentityRecord r = EidReader.decode(fakeCard(), ReadOptions.defaults().withIncludeRaw(true));
        assertNotNull(r.raw());
        assertTrue(r.raw().get("dg11").startsWith("6b"));
    }

    @Test
    void warnsWhenMrzAndChipDisagree() {
        Map<Integer, byte[]> dgs = new HashMap<>(fakeCard().dataGroups());
        dgs.put(12, DzCardFixtures.tlv(0x6C, DzCardFixtures.tlv(0x5F1B, DzCardFixtures.ascii("20350101"))));
        IdentityRecord r = EidReader.decode(new CardData(dgs, null, null, "OFFLINE"), ReadOptions.defaults());
        assertEquals("2031-05-20", r.document().dateOfExpiry()); // la MRZ fait foi
        assertEquals(1, r.warnings().size());
    }

    @Test
    void jsonKeepsArabicAndRoundTrips() {
        IdentityRecord r = EidReader.decode(fakeCard(), ReadOptions.defaults());
        String json = IdentityRecordJson.toJson(r);
        assertTrue(json.contains("\"lastNameArabic\":\"بن علي\""));
        IdentityRecord back = IdentityRecordJson.fromJson(json);
        assertEquals(r.holder(), back.holder());
        assertEquals(r.document(), back.document());
    }
}
