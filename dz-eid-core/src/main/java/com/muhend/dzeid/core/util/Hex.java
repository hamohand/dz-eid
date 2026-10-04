package com.muhend.dzeid.core.util;

/**
 * Conversion hexadécimale portable (HexFormat n'existe pas sur Android).
 */
public final class Hex {

    private static final char[] DIGITS = "0123456789abcdef".toCharArray();

    private Hex() {
    }

    public static String encode(byte[] data) {
        if (data == null) {
            return null;
        }
        char[] out = new char[data.length * 2];
        for (int i = 0; i < data.length; i++) {
            int v = data[i] & 0xFF;
            out[i * 2] = DIGITS[v >>> 4];
            out[i * 2 + 1] = DIGITS[v & 0x0F];
        }
        return new String(out);
    }

    public static byte[] decode(String hex) {
        if (hex == null) {
            return null;
        }
        String s = hex.replaceAll("\\s", "");
        if (s.length() % 2 != 0) {
            throw new IllegalArgumentException("Chaîne hexadécimale de longueur impaire");
        }
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(s.charAt(i * 2), 16);
            int lo = Character.digit(s.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) {
                throw new IllegalArgumentException("Caractère hexadécimal invalide à la position " + (i * 2));
            }
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }
}
