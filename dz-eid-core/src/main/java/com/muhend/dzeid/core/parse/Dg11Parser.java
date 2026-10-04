package com.muhend.dzeid.core.parse;

import com.muhend.dzeid.core.text.DzText;
import com.muhend.dzeid.core.text.DzText.Bilingual;
import com.muhend.dzeid.core.text.Iso8859_6;
import com.muhend.dzeid.core.tlv.Tlv;
import com.muhend.dzeid.core.tlv.TlvParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Parseur du DG11 (tag 0x6B), profil algérien inclus.
 */
public final class Dg11Parser {

    private static final Pattern SEX = Pattern.compile("[MFX]");
    private static final Pattern BLOOD_GROUP = Pattern.compile("(A|B|AB|O)[+-]");

    private Dg11Parser() {
    }

    public static Dg11Data parse(byte[] dg11) {
        Tlv root = TlvParser.parse(dg11);
        if (root.tag() != 0x6B) {
            throw new IllegalArgumentException(String.format("Tag racine inattendu pour le DG11 : %X", root.tag()));
        }

        Bilingual fullName = Bilingual.EMPTY;
        List<Bilingual> otherNames = new ArrayList<>();
        String personalNumber = null;
        String fullDateOfBirth = null;
        Bilingual placeOfBirth = Bilingual.EMPTY;
        String sexLatin = null;
        String sexArabic = null;
        String bloodGroup = null;
        String address = null;
        String telephone = null;
        String profession = null;
        Map<String, String> others = new LinkedHashMap<>();

        for (Tlv node : root.children()) {
            switch (node.tag()) {
                case 0x5C: // liste des tags présents
                    break;
                case 0x5F0E:
                    fullName = DzText.splitBilingual(text(node));
                    break;
                case 0xA0: // structure contenant le nombre de prénoms (02) puis les 5F0F
                    for (Tlv inner : node.findAll(0x5F0F)) {
                        addName(otherNames, text(inner));
                    }
                    break;
                case 0x5F0F: // certains émetteurs placent 5F0F hors de A0
                    addName(otherNames, text(node));
                    break;
                case 0x5F10:
                    personalNumber = digitsOrClean(text(node));
                    break;
                case 0x5F2B:
                    fullDateOfBirth = DzDates.fromYyyymmdd(DzText.clean(text(node)));
                    break;
                case 0x5F11:
                    placeOfBirth = DzText.splitBilingual(text(node));
                    break;
                case 0x5F42: {
                    String raw = text(node);
                    List<String> parts = DzText.splitParts(raw);
                    if (!parts.isEmpty() && SEX.matcher(parts.get(0)).matches()) {
                        // Profil algérien : SEXE<<SEXE_ARABE<<GROUPE_SANGUIN
                        sexLatin = parts.get(0);
                        for (int i = 1; i < parts.size(); i++) {
                            String p = parts.get(i);
                            if (DzText.containsArabic(p)) {
                                sexArabic = p;
                            } else if (BLOOD_GROUP.matcher(p).matches()) {
                                bloodGroup = p;
                            }
                        }
                    } else if (!parts.isEmpty()) {
                        address = String.join(", ", parts);
                    }
                    break;
                }
                case 0x5F12:
                    telephone = DzText.clean(text(node));
                    break;
                case 0x5F13:
                    profession = DzText.clean(text(node));
                    break;
                default: {
                    String value = DzText.clean(text(node));
                    if (value != null) {
                        others.put(String.format("%X", node.tag()), value);
                    }
                }
            }
        }

        return new Dg11Data(fullName, Collections.unmodifiableList(otherNames), personalNumber, fullDateOfBirth,
                placeOfBirth, sexLatin, sexArabic, bloodGroup, address, telephone, profession,
                Collections.unmodifiableMap(others));
    }

    private static void addName(List<Bilingual> names, String raw) {
        Bilingual b = DzText.splitBilingual(raw);
        if (!b.isEmpty()) {
            names.add(b);
        }
    }

    private static String digitsOrClean(String raw) {
        String clean = DzText.clean(raw);
        if (clean == null) {
            return null;
        }
        String compact = clean.replace(" ", "");
        return compact.matches("\\d+") ? compact : clean;
    }

    private static String text(Tlv node) {
        return Iso8859_6.decode(node.value());
    }
}
