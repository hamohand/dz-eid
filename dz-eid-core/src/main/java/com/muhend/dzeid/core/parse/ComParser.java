package com.muhend.dzeid.core.parse;

import com.muhend.dzeid.core.tlv.Tlv;
import com.muhend.dzeid.core.tlv.TlvParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Parseur du fichier EF.COM (tag 0x60) : liste des groupes de données présents sur la puce.
 */
public final class ComParser {

    private ComParser() {
    }

    /** Numéros des DG présents (1 = MRZ, 2 = photo, 11, 12…), triés. */
    public static List<Integer> parseDataGroups(byte[] com) {
        Tlv root = TlvParser.parse(com);
        Tlv tagList = root.find(0x5C);
        List<Integer> out = new ArrayList<>();
        if (tagList == null) {
            return out;
        }
        for (byte b : tagList.value()) {
            int dg = dataGroupNumber(b & 0xFF);
            if (dg > 0 && !out.contains(dg)) {
                out.add(dg);
            }
        }
        Collections.sort(out);
        return out;
    }

    /** Tag LDS → numéro de DG (0 si inconnu). */
    public static int dataGroupNumber(int tag) {
        switch (tag) {
            case 0x61: return 1;
            case 0x75: return 2;
            case 0x63: return 3;
            case 0x76: return 4;
            case 0x65: return 5;
            case 0x66: return 6;
            case 0x67: return 7;
            case 0x68: return 8;
            case 0x69: return 9;
            case 0x6A: return 10;
            case 0x6B: return 11;
            case 0x6C: return 12;
            case 0x6D: return 13;
            case 0x6E: return 14;
            case 0x6F: return 15;
            case 0x70: return 16;
            default: return 0;
        }
    }
}
