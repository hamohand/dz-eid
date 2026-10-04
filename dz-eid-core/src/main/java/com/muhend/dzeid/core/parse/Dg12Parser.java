package com.muhend.dzeid.core.parse;

import com.muhend.dzeid.core.text.DzText;
import com.muhend.dzeid.core.text.DzText.Bilingual;
import com.muhend.dzeid.core.text.Iso8859_6;
import com.muhend.dzeid.core.tlv.Tlv;
import com.muhend.dzeid.core.tlv.TlvParser;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parseur du DG12 (tag 0x6C), profil algérien inclus.
 */
public final class Dg12Parser {

    private Dg12Parser() {
    }

    public static Dg12Data parse(byte[] dg12) {
        Tlv root = TlvParser.parse(dg12);
        if (root.tag() != 0x6C) {
            throw new IllegalArgumentException(String.format("Tag racine inattendu pour le DG12 : %X", root.tag()));
        }

        Bilingual authority = Bilingual.EMPTY;
        String dateOfIssue = null;
        String dateOfExpiryFull = null;
        String endorsements = null;
        Bilingual documentName = Bilingual.EMPTY;
        Map<String, String> others = new LinkedHashMap<>();

        for (Tlv node : root.children()) {
            switch (node.tag()) {
                case 0x5C:
                    break;
                case 0x5F19:
                    authority = DzText.splitBilingual(text(node));
                    break;
                case 0x5F26:
                    dateOfIssue = DzDates.fromYyyymmdd(DzText.clean(text(node)));
                    break;
                case 0x5F1B: {
                    String value = DzText.clean(text(node));
                    String asDate = DzDates.fromYyyymmdd(value);
                    if (asDate != null) {
                        dateOfExpiryFull = asDate;
                    } else {
                        endorsements = value;
                    }
                    break;
                }
                case 0x5F1D: {
                    byte[] raw = node.value();
                    if (isText(raw)) {
                        documentName = DzText.splitBilingual(Iso8859_6.decode(raw));
                    }
                    break;
                }
                default: {
                    byte[] raw = node.value();
                    if (!node.isConstructed() && isText(raw)) {
                        String value = DzText.clean(Iso8859_6.decode(raw));
                        if (value != null) {
                            others.put(String.format("%X", node.tag()), value);
                        }
                    }
                }
            }
        }
        return new Dg12Data(authority, dateOfIssue, dateOfExpiryFull, endorsements, documentName,
                Collections.unmodifiableMap(others));
    }

    /** Vrai si les octets ressemblent à du texte (et non à une image binaire). */
    private static boolean isText(byte[] raw) {
        if (raw.length == 0) {
            return false;
        }
        for (byte value : raw) {
            int b = value & 0xFF;
            if (b < 0x20 || b == 0x7F) {
                return false;
            }
        }
        return true;
    }

    private static String text(Tlv node) {
        return Iso8859_6.decode(node.value());
    }
}
