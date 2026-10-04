package com.muhend.dzeid.agent;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReaderRankTest {

    @Test
    void utrustPasseAvantAcr122() {
        // Configuration réelle observée sur le poste de test
        List<String> names = new ArrayList<>(Arrays.asList(
                "ACS ACR122 0",
                "ACS ACR122U PICC Interface 0",
                "Identiv uTrust 3700 F CL Reader 0"));
        names.sort(Comparator.comparingInt(PcscReaders::rank));
        assertEquals("Identiv uTrust 3700 F CL Reader 0", names.get(0));
    }

    @Test
    void sansContactAvantContact() {
        assertTrue(PcscReaders.rank("HID OMNIKEY 5422 Contactless 0") < PcscReaders.rank("HID OMNIKEY 3x21 0"));
        assertTrue(PcscReaders.rank("Generic Contactless Reader 0") < PcscReaders.rank("Generic Smart Card Reader 0"));
        assertTrue(PcscReaders.rank("Generic Smart Card Reader 0") < PcscReaders.rank("ACS ACR122U PICC Interface 0"));
    }

    @Test
    void nomNulTolere() {
        assertEquals(30, PcscReaders.rank(null));
    }
}
