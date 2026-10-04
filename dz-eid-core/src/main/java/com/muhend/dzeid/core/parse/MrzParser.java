package com.muhend.dzeid.core.parse;

import com.muhend.dzeid.core.text.DzText;
import com.muhend.dzeid.core.tlv.Tlv;
import com.muhend.dzeid.core.tlv.TlvParser;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Parseur MRZ ICAO 9303 (formats TD1, TD2, TD3) avec vérification des chiffres de contrôle.
 */
public final class MrzParser {

    private MrzParser() {
    }

    /** Décode le fichier DG1 (tag 0x61 contenant 0x5F1F). */
    public static MrzData parseDg1(byte[] dg1) {
        Tlv root = TlvParser.parse(dg1);
        Tlv mrz = root.tag() == 0x5F1F ? root : root.find(0x5F1F);
        if (mrz == null) {
            throw new IllegalArgumentException("DG1 sans champ MRZ (5F1F)");
        }
        return parse(new String(mrz.value(), StandardCharsets.US_ASCII));
    }

    /** Décode une MRZ textuelle (avec ou sans retours à la ligne). */
    public static MrzData parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("MRZ absente");
        }
        String mrz = text.replaceAll("\\s", "").toUpperCase();
        switch (mrz.length()) {
            case 90:
                return parseTd1(mrz);
            case 72:
                return parseTd2(mrz);
            case 88:
                return parseTd3(mrz);
            default:
                throw new IllegalArgumentException("Longueur de MRZ non reconnue : " + mrz.length()
                        + " caractères (attendu 90, 72 ou 88)");
        }
    }

    private static MrzData parseTd1(String mrz) {
        String l1 = mrz.substring(0, 30);
        String l2 = mrz.substring(30, 60);
        String l3 = mrz.substring(60, 90);
        List<String> invalid = new ArrayList<>();

        String docNumber = l1.substring(5, 14);
        char docCheck = l1.charAt(14);
        String optional1 = l1.substring(15, 30);
        if (docCheck == '<') {
            // Numéro long : la suite est dans les données optionnelles, terminée par son chiffre de contrôle
            int end = optional1.indexOf('<');
            String rest = end < 0 ? optional1 : optional1.substring(0, end);
            if (!rest.isEmpty()) {
                docNumber = docNumber + rest.substring(0, rest.length() - 1);
                docCheck = rest.charAt(rest.length() - 1);
                optional1 = end < 0 ? "" : optional1.substring(end);
            }
        }
        check(invalid, "documentNumber", docNumber, docCheck);
        check(invalid, "dateOfBirth", l2.substring(0, 6), l2.charAt(6));
        check(invalid, "dateOfExpiry", l2.substring(8, 14), l2.charAt(14));
        String composite = l1.substring(5, 30) + l2.substring(0, 7) + l2.substring(8, 15) + l2.substring(18, 29);
        check(invalid, "composite", composite, l2.charAt(29));

        String[] names = splitNames(l3);
        return new MrzData("TD1", strip(l1.substring(0, 2)), strip(l1.substring(2, 5)), strip(docNumber),
                strip(optional1), l2.substring(0, 6), sex(l2.charAt(7)), l2.substring(8, 14),
                strip(l2.substring(15, 18)), strip(l2.substring(18, 29)), names[0], names[1],
                list(l1, l2, l3), Collections.unmodifiableList(invalid));
    }

    private static MrzData parseTd2(String mrz) {
        String l1 = mrz.substring(0, 36);
        String l2 = mrz.substring(36, 72);
        List<String> invalid = new ArrayList<>();
        check(invalid, "documentNumber", l2.substring(0, 9), l2.charAt(9));
        check(invalid, "dateOfBirth", l2.substring(13, 19), l2.charAt(19));
        check(invalid, "dateOfExpiry", l2.substring(21, 27), l2.charAt(27));
        check(invalid, "composite", l2.substring(0, 10) + l2.substring(13, 20) + l2.substring(21, 35), l2.charAt(35));
        String[] names = splitNames(l1.substring(5));
        return new MrzData("TD2", strip(l1.substring(0, 2)), strip(l1.substring(2, 5)), strip(l2.substring(0, 9)),
                null, l2.substring(13, 19), sex(l2.charAt(20)), l2.substring(21, 27), strip(l2.substring(10, 13)),
                strip(l2.substring(28, 35)), names[0], names[1], list(l1, l2), Collections.unmodifiableList(invalid));
    }

    private static MrzData parseTd3(String mrz) {
        String l1 = mrz.substring(0, 44);
        String l2 = mrz.substring(44, 88);
        List<String> invalid = new ArrayList<>();
        check(invalid, "documentNumber", l2.substring(0, 9), l2.charAt(9));
        check(invalid, "dateOfBirth", l2.substring(13, 19), l2.charAt(19));
        check(invalid, "dateOfExpiry", l2.substring(21, 27), l2.charAt(27));
        String personal = l2.substring(28, 42);
        char personalCheck = l2.charAt(42);
        if (!(personalCheck == '<' && personal.replace("<", "").isEmpty())) {
            check(invalid, "personalNumber", personal, personalCheck);
        }
        check(invalid, "composite", l2.substring(0, 10) + l2.substring(13, 20) + l2.substring(21, 43), l2.charAt(43));
        String[] names = splitNames(l1.substring(5));
        return new MrzData("TD3", strip(l1.substring(0, 2)), strip(l1.substring(2, 5)), strip(l2.substring(0, 9)),
                null, l2.substring(13, 19), sex(l2.charAt(20)), l2.substring(21, 27), strip(l2.substring(10, 13)),
                strip(personal), names[0], names[1], list(l1, l2), Collections.unmodifiableList(invalid));
    }

    /** Chiffre de contrôle ICAO 9303 (pondérations 7-3-1). */
    public static int checkDigit(String data) {
        final int[] weights = {7, 3, 1};
        int sum = 0;
        for (int i = 0; i < data.length(); i++) {
            sum += charValue(data.charAt(i)) * weights[i % 3];
        }
        return sum % 10;
    }

    private static int charValue(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'A' && c <= 'Z') {
            return c - 'A' + 10;
        }
        if (c == '<') {
            return 0;
        }
        throw new IllegalArgumentException("Caractère MRZ invalide : '" + c + "'");
    }

    private static void check(List<String> invalid, String name, String data, char expected) {
        try {
            if (expected < '0' || expected > '9' || checkDigit(data) != expected - '0') {
                invalid.add(name);
            }
        } catch (IllegalArgumentException e) {
            invalid.add(name);
        }
    }

    private static String[] splitNames(String field) {
        int sep = field.indexOf("<<");
        String primary = sep < 0 ? field : field.substring(0, sep);
        String secondary = sep < 0 ? "" : field.substring(sep + 2);
        return new String[]{DzText.clean(primary), DzText.clean(secondary)};
    }

    private static String sex(char c) {
        if (c == 'M' || c == 'F') {
            return String.valueOf(c);
        }
        return "X";
    }

    private static String strip(String s) {
        String out = s.replace("<", "").trim();
        return out.isEmpty() ? null : out;
    }

    private static List<String> list(String... lines) {
        List<String> out = new ArrayList<>();
        Collections.addAll(out, lines);
        return Collections.unmodifiableList(out);
    }
}
