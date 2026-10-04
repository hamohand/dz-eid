package com.muhend.dzeid.core;

import com.muhend.dzeid.core.testing.DzCardFixtures;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AccessKeyTest {

    @Test
    void normalizesUserInput() throws EidException {
        AccessKey k = AccessKey.of(" 123 456 789<", "1985-03-12", "31/05/20".replace("/", ""));
        assertEquals("123456789", k.documentNumber());
        assertEquals("850312", k.dateOfBirth());
        assertEquals("310520", k.dateOfExpiry());
    }

    @Test
    void buildsFromMrz() throws EidException {
        AccessKey k = AccessKey.fromMrz(DzCardFixtures.mrzTd1());
        assertEquals(DzCardFixtures.DOC_NUMBER, k.documentNumber());
        assertEquals(DzCardFixtures.BIRTH_YYMMDD, k.dateOfBirth());
        assertEquals(DzCardFixtures.EXPIRY_YYMMDD, k.dateOfExpiry());
    }

    @Test
    void rejectsMrzWithBadCheckDigits() {
        String bad = DzCardFixtures.mrzTd1().replace("850312", "850313");
        EidException e = assertThrows(EidException.class, () -> AccessKey.fromMrz(bad));
        assertEquals(ErrorCode.INVALID_INPUT, e.code());
    }

    @Test
    void rejectsInvalidDate() {
        EidException e = assertThrows(EidException.class, () -> AccessKey.of("123456789", "8503", "310520"));
        assertEquals(ErrorCode.INVALID_INPUT, e.code());
    }

    @Test
    void toStringDoesNotLeakPersonalData() throws EidException {
        String s = AccessKey.of("123456789", "850312", "310520").toString();
        assertFalse(s.contains("850312"));
        assertFalse(s.contains("123456"));
    }
}
