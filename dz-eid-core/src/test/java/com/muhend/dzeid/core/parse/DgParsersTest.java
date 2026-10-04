package com.muhend.dzeid.core.parse;

import com.muhend.dzeid.core.testing.DzCardFixtures;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DgParsersTest {

    @Test
    void parsesAlgerianDg11() {
        Dg11Data d = Dg11Parser.parse(DzCardFixtures.dg11());
        assertEquals("BENALI", d.fullName().latin());
        assertEquals("بن علي", d.fullName().arabic());
        assertEquals(1, d.otherNames().size());
        assertEquals("AMINA", d.otherNames().get(0).latin());
        assertEquals("أمينة", d.otherNames().get(0).arabic());
        assertEquals(DzCardFixtures.NIN, d.personalNumber());
        assertEquals("1985-03-12", d.fullDateOfBirth());
        assertEquals("TIPAZA", d.placeOfBirth().latin());
        assertEquals("تيبازة", d.placeOfBirth().arabic());
        // Profil algérien du tag 5F42
        assertEquals("F", d.sexLatin());
        assertEquals("أنثى", d.sexArabic());
        assertEquals("B-", d.bloodGroup());
        assertNull(d.address());
    }

    @Test
    void dg11With5F42AsRealAddressIsKeptAsAddress() {
        byte[] dg11 = DzCardFixtures.tlv(0x6B, DzCardFixtures.tlv(0x5F42,
                DzCardFixtures.ascii("12 RUE DIDOUCHE<<ALGER")));
        Dg11Data d = Dg11Parser.parse(dg11);
        assertNull(d.sexLatin());
        assertEquals("12 RUE DIDOUCHE, ALGER", d.address());
    }

    @Test
    void parsesAlgerianDg12() {
        Dg12Data d = Dg12Parser.parse(DzCardFixtures.dg12());
        assertEquals("ALGER_COMMUNE BAB EL OUED-ALGER", d.issuingAuthority().latin());
        assertEquals("بلدية باب الواد-الجزائر", d.issuingAuthority().arabic()); // tatweel retiré
        assertEquals("2021-05-21", d.dateOfIssue());
        assertEquals("2031-05-20", d.dateOfExpiryFull());
        assertNull(d.endorsements());
        assertEquals("CARTE D'IDENTITE NATIONALE", d.documentName().latin());
        assertEquals("بطاقة التعريف الوطنية", d.documentName().arabic());
    }
}
