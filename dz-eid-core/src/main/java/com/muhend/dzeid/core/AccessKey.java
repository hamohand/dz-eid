package com.muhend.dzeid.core;

import com.muhend.dzeid.core.parse.DzDates;
import com.muhend.dzeid.core.parse.MrzData;
import com.muhend.dzeid.core.parse.MrzParser;

/**
 * Clé d'accès à la puce (BAC/PACE), dérivée de la MRZ imprimée sur la carte.
 *
 * @param documentNumber numéro du document (majuscules, sans {@code <})
 * @param dateOfBirth    {@code AAMMJJ}
 * @param dateOfExpiry   {@code AAMMJJ}
 */
public record AccessKey(String documentNumber, String dateOfBirth, String dateOfExpiry) {

    public AccessKey {
        if (documentNumber == null || !documentNumber.matches("[A-Z0-9]{1,22}")) {
            throw new IllegalArgumentException("Numéro de document invalide");
        }
        if (dateOfBirth == null || !dateOfBirth.matches("\\d{6}")) {
            throw new IllegalArgumentException("Date de naissance invalide (format AAMMJJ attendu)");
        }
        if (dateOfExpiry == null || !dateOfExpiry.matches("\\d{6}")) {
            throw new IllegalArgumentException("Date d'expiration invalide (format AAMMJJ attendu)");
        }
    }

    /**
     * Construit une clé en normalisant les saisies : espaces et {@code <} retirés, majuscules,
     * dates acceptées en {@code AAMMJJ} ou {@code AAAA-MM-JJ}.
     */
    public static AccessKey of(String documentNumber, String dateOfBirth, String dateOfExpiry) throws EidException {
        try {
            String doc = documentNumber == null ? null
                    : documentNumber.replace("<", "").replaceAll("\\s", "").toUpperCase();
            return new AccessKey(doc, normalizeDate(dateOfBirth), normalizeDate(dateOfExpiry));
        } catch (IllegalArgumentException e) {
            throw new EidException(ErrorCode.INVALID_INPUT, e.getMessage(), e);
        }
    }

    /** Construit une clé à partir du texte complet de la MRZ (2 ou 3 lignes). */
    public static AccessKey fromMrz(String mrzText) throws EidException {
        MrzData mrz;
        try {
            mrz = MrzParser.parse(mrzText);
        } catch (IllegalArgumentException e) {
            throw new EidException(ErrorCode.INVALID_INPUT, "MRZ illisible : " + e.getMessage(), e);
        }
        if (!mrz.checkDigitsValid()) {
            throw new EidException(ErrorCode.INVALID_INPUT,
                    "MRZ incorrecte (chiffres de contrôle invalides : " + String.join(", ", mrz.invalidChecks()) + ")");
        }
        return of(mrz.documentNumber(), mrz.dateOfBirth(), mrz.dateOfExpiry());
    }

    private static String normalizeDate(String s) {
        if (s == null) {
            return null;
        }
        String d = s.trim();
        if (d.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return DzDates.isoToYymmdd(d);
        }
        return d.replaceAll("[^0-9]", "");
    }

    @Override
    public String toString() {
        // Ne jamais journaliser la clé complète (donnée personnelle et secret d'accès)
        return "AccessKey[document=***" + documentNumber.substring(Math.max(0, documentNumber.length() - 3)) + "]";
    }
}
