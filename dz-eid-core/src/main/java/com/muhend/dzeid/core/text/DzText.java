package com.muhend.dzeid.core.text;

import java.util.ArrayList;
import java.util.List;

/**
 * Règles de texte propres aux documents algériens.
 *
 * <p>Les champs bilingues de la puce suivent le format {@code PARTIE_LATINE<<PARTIE_ARABE},
 * par exemple {@code HAMROUN<<حمرون} ou {@code M<<ذكر<<A+}. Le caractère {@code <} isolé
 * sert d'espace (convention MRZ).</p>
 */
public final class DzText {

    private static final char TATWEEL = '\u0640';

    private DzText() {
    }

    /** Partie latine / partie arabe d'un champ bilingue. */
    public record Bilingual(String latin, String arabic) {
        public static final Bilingual EMPTY = new Bilingual(null, null);

        public boolean isEmpty() {
            return latin == null && arabic == null;
        }
    }

    /** Vrai si la chaîne contient au moins une lettre de l'alphabet arabe. */
    public static boolean containsArabic(String s) {
        if (s == null) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '\u0600' && c <= '\u06FF' && c != TATWEEL) {
                return true;
            }
        }
        return false;
    }

    /**
     * Découpe un champ sur le séparateur {@code <<} et nettoie chaque partie.
     * Les parties vides sont supprimées.
     */
    public static List<String> splitParts(String raw) {
        List<String> parts = new ArrayList<>();
        if (raw == null) {
            return parts;
        }
        for (String part : raw.split("<<")) {
            String clean = clean(part);
            if (clean != null) {
                parts.add(clean);
            }
        }
        return parts;
    }

    /**
     * Sépare un champ bilingue : les parties contenant de l'arabe forment la partie arabe,
     * les autres la partie latine.
     */
    public static Bilingual splitBilingual(String raw) {
        List<String> latin = new ArrayList<>();
        List<String> arabic = new ArrayList<>();
        for (String part : splitParts(raw)) {
            if (containsArabic(part)) {
                arabic.add(part);
            } else {
                latin.add(part);
            }
        }
        return new Bilingual(
                latin.isEmpty() ? null : String.join(" ", latin),
                arabic.isEmpty() ? null : String.join(" ", arabic));
    }

    /**
     * Nettoyage : {@code <} devient espace, suppression du tatweel (ـ, purement typographique),
     * espaces multiples réduits. Retourne {@code null} pour une chaîne vide.
     */
    public static String clean(String s) {
        if (s == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == TATWEEL || c == '\u0000') {
                continue;
            }
            sb.append(c == '<' || c == '\u00A0' ? ' ' : c);
        }
        String out = sb.toString().replaceAll("\\s+", " ").trim();
        return out.isEmpty() ? null : out;
    }
}
