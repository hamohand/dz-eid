package com.muhend.dzeid.core.tlv;

import java.util.ArrayList;
import java.util.List;

/**
 * Parseur BER-TLV strict (ISO/IEC 8825-1), suffisant pour les structures LDS ICAO 9303.
 *
 * <ul>
 *   <li>Tags multi-octets ({@code xx1F...}) ;</li>
 *   <li>Longueurs courtes et longues ({@code 81}, {@code 82}, {@code 83}, {@code 84}) ;</li>
 *   <li>Descente récursive dans les nœuds construits (bit 6 du premier octet du tag).</li>
 * </ul>
 *
 * <p>Contrairement aux anciens parseurs « maison », un octet {@code A0} est bien traité comme un tag
 * construit d'un seul octet, ce qui permet de lire correctement la liste des prénoms du DG11.</p>
 */
public final class TlvParser {

    private TlvParser() {
    }

    /** Analyse une séquence de TLV consécutifs. */
    public static List<Tlv> parseAll(byte[] data) {
        return parseRange(data, 0, data.length, 0);
    }

    /** Analyse un unique TLV racine (par ex. un fichier DG complet). */
    public static Tlv parse(byte[] data) {
        List<Tlv> nodes = parseAll(data);
        if (nodes.isEmpty()) {
            throw new TlvException("Aucune structure TLV trouvée");
        }
        return nodes.get(0);
    }

    /**
     * Retourne la valeur du premier TLV (le contenu, sans tag ni longueur).
     * Pratique pour retirer l'enveloppe d'un fichier (par ex. 0x77 autour du SOD).
     */
    public static byte[] unwrap(byte[] data) {
        int[] header = readHeader(data, 0, data.length);
        int valueStart = header[1];
        int length = header[2];
        byte[] out = new byte[length];
        System.arraycopy(data, valueStart, out, 0, length);
        return out;
    }

    private static List<Tlv> parseRange(byte[] data, int start, int end, int depth) {
        if (depth > 16) {
            throw new TlvException("Imbrication TLV trop profonde");
        }
        List<Tlv> nodes = new ArrayList<>();
        int pos = start;
        while (pos < end) {
            // Octets de remplissage tolérés entre deux TLV
            if (data[pos] == 0x00 || data[pos] == (byte) 0xFF) {
                pos++;
                continue;
            }
            int[] header = readHeader(data, pos, end);
            int tag = header[0];
            int valueStart = header[1];
            int length = header[2];
            byte[] value = new byte[length];
            System.arraycopy(data, valueStart, value, 0, length);

            int firstByte = data[pos] & 0xFF;
            List<Tlv> children = null;
            if ((firstByte & 0x20) != 0) {
                children = parseRange(data, valueStart, valueStart + length, depth + 1);
            }
            nodes.add(new Tlv(tag, value, children));
            pos = valueStart + length;
        }
        return nodes;
    }

    /** @return {tag, début de la valeur, longueur} */
    private static int[] readHeader(byte[] data, int pos, int end) {
        if (pos >= end) {
            throw new TlvException("Fin de données inattendue (tag)");
        }
        int tag = data[pos] & 0xFF;
        pos++;
        if ((tag & 0x1F) == 0x1F) {
            int b;
            do {
                if (pos >= end) {
                    throw new TlvException("Fin de données inattendue (tag multi-octets)");
                }
                b = data[pos] & 0xFF;
                tag = (tag << 8) | b;
                pos++;
            } while ((b & 0x80) != 0);
        }
        if (pos >= end) {
            throw new TlvException(String.format("Fin de données inattendue (longueur du tag %X)", tag));
        }
        int length = data[pos] & 0xFF;
        pos++;
        if ((length & 0x80) != 0) {
            int count = length & 0x7F;
            if (count == 0 || count > 4) {
                throw new TlvException(String.format("Longueur BER non supportée pour le tag %X", tag));
            }
            if (pos + count > end) {
                throw new TlvException("Fin de données inattendue (longueur longue)");
            }
            length = 0;
            for (int i = 0; i < count; i++) {
                length = (length << 8) | (data[pos] & 0xFF);
                pos++;
            }
        }
        if (length < 0 || pos + length > end) {
            throw new TlvException(String.format("Longueur %d hors limites pour le tag %X", length, tag));
        }
        return new int[]{tag, pos, length};
    }
}
