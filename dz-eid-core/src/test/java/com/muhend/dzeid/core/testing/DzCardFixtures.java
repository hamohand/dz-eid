package com.muhend.dzeid.core.testing;

import com.muhend.dzeid.core.parse.MrzParser;
import com.muhend.dzeid.core.text.Iso8859_6;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Jeux d'essai FICTIFS reproduisant fidèlement la structure des CNIe algériennes réelles
 * (ordre des tags, structure A0 des prénoms, 5F42 sexe/groupe sanguin, tatweel dans l'autorité…).
 * Aucune donnée personnelle réelle.
 */
public final class DzCardFixtures {

    public static final String DOC_NUMBER = "123456789";
    public static final String BIRTH_YYMMDD = "850312";
    public static final String EXPIRY_YYMMDD = "310520";
    public static final String NIN = "119850312000123400";

    private DzCardFixtures() {
    }

    public static String mrzTd1() {
        String l1 = pad("IDDZA" + DOC_NUMBER + MrzParser.checkDigit(DOC_NUMBER), 30);
        String l2Start = BIRTH_YYMMDD + MrzParser.checkDigit(BIRTH_YYMMDD) + "F"
                + EXPIRY_YYMMDD + MrzParser.checkDigit(EXPIRY_YYMMDD) + "DZA";
        String l2NoComposite = pad(l2Start, 29);
        String composite = l1.substring(5, 30) + l2NoComposite.substring(0, 7) + l2NoComposite.substring(8, 15)
                + l2NoComposite.substring(18, 29);
        String l2 = l2NoComposite + MrzParser.checkDigit(composite);
        String l3 = pad("BENALI<<AMINA", 30);
        return l1 + l2 + l3;
    }

    public static byte[] dg1() {
        return tlv(0x61, tlv(0x5F1F, mrzTd1().getBytes(StandardCharsets.US_ASCII)));
    }

    public static byte[] dg11() {
        byte[] tagList = concat(tag(0x5F0E), tag(0x5F0F), tag(0x5F10), tag(0x5F2B), tag(0x5F11), tag(0x5F42), tag(0x5F16));
        return tlv(0x6B, concat(
                tlv(0x5C, tagList),
                tlv(0x5F0E, iso("BENALI<<بن علي")),
                tlv(0xA0, concat(tlv(0x02, new byte[]{0x01}), tlv(0x5F0F, iso("AMINA<<أمينة")))),
                tlv(0x5F10, ascii(NIN)),
                tlv(0x5F2B, ascii("19850312")),
                tlv(0x5F11, iso("TIPAZA<<تيبازة")),
                tlv(0x5F42, iso("F<<أنثى<<B-")),
                tlv(0x5F16, ascii("<<"))));
    }

    public static byte[] dg12() {
        byte[] tagList = concat(tag(0x5F19), tag(0x5F26), tag(0x5F1B), tag(0x5F1D));
        return tlv(0x6C, concat(
                tlv(0x5C, tagList),
                tlv(0x5F19, iso("ALGER_COMMUNE BAB EL OUED-ALGER<<بلدية باب الــواد-الجزائر")),
                tlv(0x5F26, ascii("20210521")),
                tlv(0x5F1B, ascii("20310520")),
                tlv(0x5F1D, iso("CARTE D'IDENTITE NATIONALE<<بطاقة التعريف الوطنية"))));
    }

    // ---------- Encodage TLV ----------

    public static byte[] tlv(int tag, byte[] value) {
        return concat(tag(tag), length(value.length), value);
    }

    public static byte[] tag(int tag) {
        if (tag > 0xFFFF) {
            return new byte[]{(byte) (tag >>> 16), (byte) (tag >>> 8), (byte) tag};
        }
        if (tag > 0xFF) {
            return new byte[]{(byte) (tag >>> 8), (byte) tag};
        }
        return new byte[]{(byte) tag};
    }

    public static byte[] length(int len) {
        if (len < 0x80) {
            return new byte[]{(byte) len};
        }
        if (len <= 0xFF) {
            return new byte[]{(byte) 0x81, (byte) len};
        }
        return new byte[]{(byte) 0x82, (byte) (len >>> 8), (byte) len};
    }

    public static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] p : parts) {
            out.write(p, 0, p.length);
        }
        return out.toByteArray();
    }

    public static byte[] iso(String s) {
        return Iso8859_6.encode(s);
    }

    public static byte[] ascii(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }

    private static String pad(String s, int len) {
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < len) {
            sb.append('<');
        }
        return sb.toString();
    }
}
