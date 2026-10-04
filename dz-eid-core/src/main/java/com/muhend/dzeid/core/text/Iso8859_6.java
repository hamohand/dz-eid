package com.muhend.dzeid.core.text;

/**
 * Codec ISO-8859-6 (latin/arabe) implémenté à la main.
 *
 * <p>Les puces algériennes encodent la partie arabe des champs DG11/DG12 en ISO-8859-6.
 * On n'utilise pas {@code Charset.forName("ISO-8859-6")} car ce jeu de caractères n'est pas
 * garanti sur toutes les plateformes (Android, JRE allégés). La table ci-dessous est la table
 * normative ; les octets 0x00-0x7F sont identiques à l'ASCII.</p>
 */
public final class Iso8859_6 {

    private static final char UNDEFINED = '\uFFFD';
    private static final char[] HIGH = new char[128];

    static {
        for (int i = 0; i < 128; i++) {
            HIGH[i] = UNDEFINED;
        }
        HIGH[0xA0 - 0x80] = '\u00A0';
        HIGH[0xA4 - 0x80] = '\u00A4';
        HIGH[0xAC - 0x80] = '\u060C'; // virgule arabe
        HIGH[0xAD - 0x80] = '\u00AD';
        HIGH[0xBB - 0x80] = '\u061B'; // point-virgule arabe
        HIGH[0xBF - 0x80] = '\u061F'; // point d'interrogation arabe
        for (int b = 0xC1; b <= 0xDA; b++) {
            HIGH[b - 0x80] = (char) (0x0621 + (b - 0xC1)); // ء .. غ
        }
        for (int b = 0xE0; b <= 0xF2; b++) {
            HIGH[b - 0x80] = (char) (0x0640 + (b - 0xE0)); // ـ .. ْ
        }
    }

    private Iso8859_6() {
    }

    public static String decode(byte[] data) {
        if (data == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(data.length);
        for (byte value : data) {
            int b = value & 0xFF;
            sb.append(b < 0x80 ? (char) b : HIGH[b - 0x80]);
        }
        return sb.toString();
    }

    /**
     * Encode une chaîne en ISO-8859-6 (utilisé par les tests et les outils de génération de jeux d'essai).
     *
     * @throws IllegalArgumentException si un caractère n'est pas représentable
     */
    public static byte[] encode(String text) {
        byte[] out = new byte[text.length()];
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < 0x80) {
                out[i] = (byte) c;
                continue;
            }
            int found = -1;
            for (int j = 0; j < HIGH.length; j++) {
                if (HIGH[j] == c && c != UNDEFINED) {
                    found = j + 0x80;
                    break;
                }
            }
            if (found < 0) {
                throw new IllegalArgumentException("Caractère non représentable en ISO-8859-6 : U+"
                        + Integer.toHexString(c).toUpperCase());
            }
            out[i] = (byte) found;
        }
        return out;
    }
}
