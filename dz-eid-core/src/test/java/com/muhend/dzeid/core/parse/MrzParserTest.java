package com.muhend.dzeid.core.parse;

import com.muhend.dzeid.core.testing.DzCardFixtures;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MrzParserTest {

    /** Exemple officiel ICAO 9303 partie 5 (TD1). */
    private static final String ICAO_TD1 = "I<UTOD231458907<<<<<<<<<<<<<<<\n"
            + "7408122F1204159UTO<<<<<<<<<<<6\n"
            + "ERIKSSON<<ANNA<MARIA<<<<<<<<<<";

    /** Exemple officiel ICAO 9303 partie 4 (TD3, passeport). */
    private static final String ICAO_TD3 = "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<\n"
            + "L898902C36UTO7408122F1204159ZE184226B<<<<<10";

    @Test
    void parsesIcaoTd1Example() {
        MrzData m = MrzParser.parse(ICAO_TD1);
        assertEquals("TD1", m.format());
        assertEquals("I", m.documentCode());
        assertEquals("UTO", m.issuingState());
        assertEquals("D23145890", m.documentNumber());
        assertEquals("740812", m.dateOfBirth());
        assertEquals("F", m.sex());
        assertEquals("120415", m.dateOfExpiry());
        assertEquals("ERIKSSON", m.primaryIdentifier());
        assertEquals("ANNA MARIA", m.secondaryIdentifier());
        assertTrue(m.checkDigitsValid(), "chiffres de contrôle : " + m.invalidChecks());
    }

    @Test
    void parsesIcaoTd3Example() {
        MrzData m = MrzParser.parse(ICAO_TD3);
        assertEquals("TD3", m.format());
        assertEquals("L898902C3", m.documentNumber());
        assertEquals("ZE184226B", m.optionalData2());
        assertTrue(m.checkDigitsValid(), "chiffres de contrôle : " + m.invalidChecks());
    }

    @Test
    void detectsWrongCheckDigit() {
        MrzData m = MrzParser.parse(ICAO_TD1.replace("D231458907", "D231458908"));
        assertFalse(m.checkDigitsValid());
        assertTrue(m.invalidChecks().contains("documentNumber"));
    }

    @Test
    void parsesAlgerianDg1() {
        MrzData m = MrzParser.parseDg1(DzCardFixtures.dg1());
        assertEquals("ID", m.documentCode());
        assertEquals("DZA", m.issuingState());
        assertEquals(DzCardFixtures.DOC_NUMBER, m.documentNumber());
        assertEquals("BENALI", m.primaryIdentifier());
        assertEquals("AMINA", m.secondaryIdentifier());
        assertTrue(m.checkDigitsValid());
    }

    @Test
    void convertsDates() {
        assertEquals("1945-08-08", DzDates.fromMrzBirthDate("450808", 2026));
        assertEquals("2005-01-01", DzDates.fromMrzBirthDate("050101", 2026));
        assertEquals("2031-05-20", DzDates.fromMrzExpiryDate("310520"));
        assertEquals("1985-03-12", DzDates.fromYyyymmdd("19850312"));
        assertEquals("850312", DzDates.isoToYymmdd("1985-03-12"));
    }
}
