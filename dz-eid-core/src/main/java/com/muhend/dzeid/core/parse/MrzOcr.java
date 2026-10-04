package com.muhend.dzeid.core.parse;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Extraction d'une MRZ fiable depuis un texte issu d'un OCR (caméra du téléphone).
 *
 * <p>L'OCR confond régulièrement certains caractères ({@code O/0}, {@code I/1}, {@code S/5}, {@code B/8}…)
 * et perd parfois les {@code <} de fin de ligne. On corrige donc chaque caractère selon <b>la nature
 * attendue à sa position</b> (chiffre, lettre ou alphanumérique, d'après ICAO 9303), puis on ne retient
 * la MRZ <b>que si tous ses chiffres de contrôle sont valides</b> : une MRZ renvoyée est donc exacte.</p>
 *
 * <p>Fonction pure, sans dépendance : utilisée par le scanner MRZ Android, testable sur le bureau.</p>
 */
public final class MrzOcr {

    private MrzOcr() {
    }

    /** Nature attendue d'une position : chiffre, lettre (ou {@code <}), alphanumérique. */
    private static final char D = 'D';
    private static final char L = 'L';
    private static final char A = 'A';

    // Gabarits ICAO 9303 (D = chiffre, L = lettre ou <, A = alphanumérique ou <)
    private static final String TD1_L1 = "LL" + "LLL" + "AAAAAAAAA" + "D" + "AAAAAAAAAAAAAAA";
    private static final String TD1_L1_NUMERIC = "LL" + "LLL" + "DDDDDDDDD" + "D" + "AAAAAAAAAAAAAAA";
    private static final String TD1_L2 = "DDDDDDD" + "L" + "DDDDDDD" + "LLL" + "AAAAAAAAAAA" + "D";
    private static final String TD1_L3 = repeat(L, 30);
    private static final String TD2_L1 = repeat(L, 36);
    private static final String TD2_L2 = "AAAAAAAAA" + "D" + "LLL" + "DDDDDDD" + "L" + "DDDDDDD" + "AAAAAAA" + "D";
    private static final String TD3_L1 = repeat(L, 44);
    private static final String TD3_L2 = "AAAAAAAAA" + "D" + "LLL" + "DDDDDDD" + "L" + "DDDDDDD" + "AAAAAAAAAAAAAA" + "A" + "D";

    /**
     * @param ocrText texte brut reconnu (plusieurs lignes, éventuellement mêlé à d'autres textes de la carte)
     * @return la MRZ corrigée (lignes séparées par {@code \n}) dont tous les chiffres de contrôle sont valides,
     *         ou {@code null} si aucune MRZ fiable n'a été trouvée
     */
    public static String extract(String ocrText) {
        if (ocrText == null || ocrText.isEmpty()) {
            return null;
        }
        List<String> lines = new ArrayList<>();
        for (String raw : ocrText.split("\\r?\\n")) {
            String clean = clean(raw);
            if (clean.length() >= 5 && clean.indexOf('<') >= 0) {
                lines.add(clean);
            }
        }
        // TD1 (carte d'identité : 3 × 30), puis TD3 (passeport : 2 × 44), puis TD2 (2 × 36)
        for (int i = 0; i + 2 < lines.size(); i++) {
            String[] triple = {lines.get(i), lines.get(i + 1), lines.get(i + 2)};
            String r = tryFormat(triple, new String[]{TD1_L1, TD1_L2, TD1_L3});
            if (r == null) {
                // Numéro de document purement numérique (CNIe algérienne) : corrige aussi O/0, I/1 dans le numéro
                r = tryFormat(triple, new String[]{TD1_L1_NUMERIC, TD1_L2, TD1_L3});
            }
            if (r != null) {
                return r;
            }
        }
        for (int i = 0; i + 1 < lines.size(); i++) {
            String[] pair = {lines.get(i), lines.get(i + 1)};
            String r = tryFormat(pair, new String[]{TD3_L1, TD3_L2});
            if (r == null) {
                r = tryFormat(pair, new String[]{TD2_L1, TD2_L2});
            }
            if (r != null) {
                return r;
            }
        }
        return null;
    }

    private static String tryFormat(String[] lines, String[] templates) {
        String[] fixed = new String[lines.length];
        for (int i = 0; i < lines.length; i++) {
            fixed[i] = fit(lines[i], templates[i]);
            if (fixed[i] == null) {
                return null;
            }
        }
        String text = String.join("\n", fixed);
        try {
            MrzData data = MrzParser.parse(text);
            return data.checkDigitsValid() ? text : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Ajuste une ligne à la longueur du gabarit puis corrige chaque caractère selon sa position.
     * <ul>
     *   <li>trop longue : seuls des {@code <} en trop sont tolérés en fin de ligne ;</li>
     *   <li>trop courte : l'OCR a perdu des {@code <}. Si la ligne finit par un chiffre de contrôle, on les
     *       réinsère dans la plus longue série de {@code <} (sinon ce chiffre serait décalé) ; sinon on les
     *       ajoute en fin de ligne (noms, données facultatives).</li>
     * </ul>
     */
    private static String fit(String line, String template) {
        int n = template.length();
        String s = line;
        if (s.length() > n) {
            String extra = s.substring(n);
            if (!extra.replace("<", "").isEmpty()) {
                return null;
            }
            s = s.substring(0, n);
        }
        if (s.length() < n) {
            boolean endsWithCheck = template.charAt(n - 1) == D;
            int missing = n - s.length();
            if (endsWithCheck) {
                if (missing > 12) {
                    return null;
                }
                int[] run = longestFillerRun(s);
                if (run[1] == 0) {
                    return null;
                }
                StringBuilder pad = new StringBuilder();
                for (int i = 0; i < missing; i++) {
                    pad.append('<');
                }
                s = s.substring(0, run[0]) + pad + s.substring(run[0]);
            } else {
                StringBuilder sb = new StringBuilder(s);
                while (sb.length() < n) {
                    sb.append('<');
                }
                s = sb.toString();
            }
        }
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            sb.append(correct(s.charAt(i), template.charAt(i)));
        }
        return sb.toString();
    }

    /** @return {début, longueur} de la plus longue série de {@code <}. */
    private static int[] longestFillerRun(String s) {
        int bestStart = 0;
        int bestLen = 0;
        int i = 0;
        while (i < s.length()) {
            if (s.charAt(i) == '<') {
                int j = i;
                while (j < s.length() && s.charAt(j) == '<') {
                    j++;
                }
                if (j - i > bestLen) {
                    bestStart = i;
                    bestLen = j - i;
                }
                i = j;
            } else {
                i++;
            }
        }
        return new int[]{bestStart, bestLen};
    }

    private static char correct(char c, char kind) {
        if (c == '<') {
            return c;
        }
        if (kind == D) {
            switch (c) {
                case 'O': case 'Q': case 'D': case 'U': return '0';
                case 'I': case 'L': case 'J': return '1';
                case 'Z': return '2';
                case 'S': return '5';
                case 'G': return '6';
                case 'T': return '7';
                case 'B': return '8';
                default: return c;
            }
        }
        if (kind == L) {
            switch (c) {
                case '0': return 'O';
                case '1': return 'I';
                case '2': return 'Z';
                case '5': return 'S';
                case '6': return 'G';
                case '8': return 'B';
                default: return c;
            }
        }
        return c;
    }

    /** Majuscules, sans espaces, guillemets et chevrons typographiques ramenés à {@code <}. */
    static String clean(String raw) {
        String s = raw.toUpperCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '<') {
                sb.append(c);
            } else if (c == '«' || c == '‹' || c == '≤' || c == '〈' || c == '＜') {
                sb.append('<');
            }
            // espaces, ponctuation et autres parasites : ignorés
        }
        return sb.toString();
    }

    private static String repeat(char c, int n) {
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            sb.append(c);
        }
        return sb.toString();
    }
}
