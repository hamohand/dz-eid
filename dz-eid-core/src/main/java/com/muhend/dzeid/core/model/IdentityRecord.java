package com.muhend.dzeid.core.model;

import java.util.List;
import java.util.Map;

/**
 * Contrat de sortie unique de dz-eid (version de schéma {@value #SCHEMA_VERSION}).
 *
 * <p>Toutes les dates sont au format ISO 8601. Les champs absents sont omis du JSON.
 * Voir {@code docs/contrat-identity-record-v1.md}.</p>
 */
public record IdentityRecord(
        String schemaVersion,
        String readAt,
        DocumentInfo document,
        HolderInfo holder,
        MrzSummary mrz,
        Photo photo,
        Photo signatureImage,
        Verification verification,
        List<String> warnings,
        Map<String, String> raw) {

    /** 1.1 : ajout de {@code signatureImage} et de {@code verification.activeAuthentication}. */
    public static final String SCHEMA_VERSION = "1.1";

    /** Copie avec une autre photo (par ex. après conversion JPEG2000 → JPEG). */
    public IdentityRecord withPhoto(Photo newPhoto) {
        return new IdentityRecord(schemaVersion, readAt, document, holder, mrz, newPhoto, signatureImage,
                verification, warnings, raw);
    }

    /** Copie avec une autre image de signature (par ex. après conversion JPEG2000 → JPEG). */
    public IdentityRecord withSignatureImage(Photo newSignature) {
        return new IdentityRecord(schemaVersion, readAt, document, holder, mrz, photo, newSignature,
                verification, warnings, raw);
    }

    /** Informations sur le document. */
    public record DocumentInfo(
            String type,
            String code,
            String number,
            String issuingState,
            String dateOfIssue,
            String dateOfExpiry,
            String issuingAuthorityLatin,
            String issuingAuthorityArabic,
            String nameLatin,
            String nameArabic) {
    }

    /** Informations sur le titulaire. */
    public record HolderInfo(
            String nin,
            String lastNameLatin,
            String firstNameLatin,
            String lastNameArabic,
            String firstNameArabic,
            String sex,
            String sexArabic,
            String dateOfBirth,
            String placeOfBirthLatin,
            String placeOfBirthArabic,
            String nationality,
            String bloodGroup,
            String address) {
    }

    /** Résumé de la MRZ lue dans la puce. */
    public record MrzSummary(String format, List<String> lines, boolean checkDigitsValid) {
    }

    /**
     * Image enregistrée sur la puce : photo du titulaire (DG2) ou signature manuscrite (DG7).
     *
     * @param originalMimeType renseigné si l'image a été convertie (format d'origine sur la puce)
     */
    public record Photo(String mimeType, String base64, String originalMimeType) {
    }

    /** Résultat des contrôles de sécurité. */
    public record Verification(
            String accessMethod,
            List<Integer> dataGroupsPresent,
            List<Integer> dataGroupsRead,
            PassiveAuthentication passiveAuthentication,
            ActiveAuthentication activeAuthentication) {
    }

    /**
     * Active Authentication (ICAO 9303 partie 11) : la puce signe un défi aléatoire avec une clé privée
     * non extractible, ce qui prouve qu'elle est l'originale et non une copie (anti-clonage).
     *
     * @param result    VALID (puce authentique), INVALID (réponse incorrecte ou refusée), NOT_CHECKED
     * @param algorithm algorithme constaté, par ex. {@code RSA-1024 ISO 9796-2 / SHA-1}
     * @param summary   phrase de synthèse en français
     */
    public record ActiveAuthentication(Status result, String algorithm, String summary) {

        public static ActiveAuthentication notChecked(String reason) {
            return new ActiveAuthentication(Status.NOT_CHECKED, null, reason);
        }
    }

    /** Statut d'un contrôle. */
    public enum Status {
        VALID, INVALID, NOT_CHECKED
    }

    /**
     * Passive Authentication (ICAO 9303 partie 11).
     *
     * @param dataIntegrity     empreintes des DG lus comparées à celles signées dans le SOD
     * @param signature         signature du SOD par le certificat du signataire (DS)
     * @param certificateChain  certificat DS rattaché à un certificat racine CSCA de confiance
     * @param summary           phrase de synthèse en français
     */
    public record PassiveAuthentication(
            Status dataIntegrity,
            Status signature,
            Status certificateChain,
            String digestAlgorithm,
            SignerInfo documentSigner,
            String summary,
            List<String> details) {

        public static PassiveAuthentication notChecked(String reason) {
            return new PassiveAuthentication(Status.NOT_CHECKED, Status.NOT_CHECKED, Status.NOT_CHECKED,
                    null, null, reason, java.util.Collections.emptyList());
        }
    }

    /** Identité du certificat signataire du document (DS). */
    public record SignerInfo(String subject, String issuer, String serialNumber, String notBefore, String notAfter) {
    }
}
