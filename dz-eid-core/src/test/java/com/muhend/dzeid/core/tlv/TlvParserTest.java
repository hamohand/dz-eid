package com.muhend.dzeid.core.tlv;

import com.muhend.dzeid.core.util.Hex;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TlvParserTest {

    @Test
    void parsesMultiByteTagsAndShortLength() {
        Tlv t = TlvParser.parse(Hex.decode("5F2B083139383530333132"));
        assertEquals(0x5F2B, t.tag());
        assertEquals("19850312", new String(t.value()));
    }

    @Test
    void parsesLongFormLength81() {
        byte[] value = new byte[0x83];
        byte[] data = new byte[3 + value.length];
        data[0] = 0x6B;
        data[1] = (byte) 0x81;
        data[2] = (byte) 0x83;
        Tlv t = TlvParser.parse(data);
        assertEquals(0x6B, t.tag());
        assertEquals(0x83, t.value().length);
    }

    @Test
    void entersA0ConstructedStructure() {
        // A0 { 02 01 01, 5F0F 03 'ABC' } : A0 est un tag construit d'UN SEUL octet
        Tlv root = TlvParser.parse(Hex.decode("6B0BA009020101" + "5F0F03414243"));
        Tlv a0 = root.children().get(0);
        assertEquals(0xA0, a0.tag());
        assertTrue(a0.isConstructed());
        assertEquals(2, a0.children().size());
        Tlv name = root.find(0x5F0F);
        assertNotNull(name);
        assertEquals("ABC", new String(name.value()));
    }

    @Test
    void findAllReturnsEveryOccurrence() {
        Tlv root = TlvParser.parse(Hex.decode("A0085F0F01415F0F0142"));
        List<Tlv> names = root.findAll(0x5F0F);
        assertEquals(2, names.size());
    }

    @Test
    void unwrapRemovesOuterEnvelope() {
        assertArrayEquals(Hex.decode("0102"), TlvParser.unwrap(Hex.decode("77020102")));
    }

    @Test
    void rejectsTruncatedData() {
        assertThrows(TlvException.class, () -> TlvParser.parse(Hex.decode("5F0E0A4142")));
    }
}
