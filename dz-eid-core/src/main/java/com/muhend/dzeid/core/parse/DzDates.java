package com.muhend.dzeid.core.parse;

import java.time.DateTimeException;
import java.time.LocalDate;

/**
 * Conversion des dates de la puce vers le format ISO 8601 ({@code AAAA-MM-JJ}).
 */
public final class DzDates {

    private DzDates() {
    }

    /** {@code 19450808} → {@code 1945-08-08}. Retourne {@code null} si invalide. */
    public static String fromYyyymmdd(String s) {
        if (s == null) {
            return null;
        }
        String d = s.trim();
        if (!d.matches("\\d{8}")) {
            return null;
        }
        return safeIso(Integer.parseInt(d.substring(0, 4)), Integer.parseInt(d.substring(4, 6)),
                Integer.parseInt(d.substring(6, 8)));
    }

    /**
     * Date de naissance MRZ {@code AAMMJJ} : le siècle est déduit par rapport à l'année de référence
     * (une naissance ne peut pas être dans le futur).
     */
    public static String fromMrzBirthDate(String yymmdd, int referenceYear) {
        if (yymmdd == null || !yymmdd.matches("\\d{6}")) {
            return null;
        }
        int yy = Integer.parseInt(yymmdd.substring(0, 2));
        int century = yy > referenceYear % 100 ? 1900 : 2000;
        return safeIso(century + yy, Integer.parseInt(yymmdd.substring(2, 4)), Integer.parseInt(yymmdd.substring(4, 6)));
    }

    public static String fromMrzBirthDate(String yymmdd) {
        return fromMrzBirthDate(yymmdd, LocalDate.now().getYear());
    }

    /** Date d'expiration MRZ {@code AAMMJJ} : toujours au XXIe siècle pour les documents biométriques. */
    public static String fromMrzExpiryDate(String yymmdd) {
        if (yymmdd == null || !yymmdd.matches("\\d{6}")) {
            return null;
        }
        int yy = Integer.parseInt(yymmdd.substring(0, 2));
        int year = yy >= 70 ? 1900 + yy : 2000 + yy;
        return safeIso(year, Integer.parseInt(yymmdd.substring(2, 4)), Integer.parseInt(yymmdd.substring(4, 6)));
    }

    /** {@code 1945-08-08} → {@code 450808} (format attendu par BAC/PACE). */
    public static String isoToYymmdd(String iso) {
        if (iso == null || !iso.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return null;
        }
        return iso.substring(2, 4) + iso.substring(5, 7) + iso.substring(8, 10);
    }

    private static String safeIso(int year, int month, int day) {
        try {
            return LocalDate.of(year, month, day).toString();
        } catch (DateTimeException e) {
            return null;
        }
    }
}
