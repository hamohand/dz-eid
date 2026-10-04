package com.muhend.dzeid.core.text;

import com.muhend.dzeid.core.util.Hex;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextTest {

    @Test
    void decodesArabicIso8859_6() {
        // "بطاقة" tel qu'encodé dans le 5F1D des CNIe
        assertEquals("بطاقة", Iso8859_6.decode(Hex.decode("c8d7c7e2c9")));
    }

    @Test
    void asciiIsUnchanged() {
        assertEquals("CARTE<<", Iso8859_6.decode("CARTE<<".getBytes()));
    }

    @Test
    void roundTrip() {
        String s = "بطاقة التعريف الوطنية";
        assertArrayEquals(Iso8859_6.encode(s), Iso8859_6.encode(Iso8859_6.decode(Iso8859_6.encode(s))));
        assertEquals(s, Iso8859_6.decode(Iso8859_6.encode(s)));
    }

    @Test
    void splitsBilingualField() {
        DzText.Bilingual b = DzText.splitBilingual("MOHAMED AMINE<<محمد أمين");
        assertEquals("MOHAMED AMINE", b.latin());
        assertEquals("محمد أمين", b.arabic());
    }

    @Test
    void cleanRemovesFillersAndTatweel() {
        assertEquals("باب الواد", DzText.clean("باب الــواد"));
        assertEquals("BAB EL OUED", DzText.clean("BAB<EL<OUED<<<<"));
        assertNull(DzText.clean("<<<<"));
    }

    @Test
    void detectsArabic() {
        assertTrue(DzText.containsArabic("ذكر"));
        assertFalse(DzText.containsArabic("A+"));
        assertFalse(DzText.containsArabic("ـ"));
    }
}
