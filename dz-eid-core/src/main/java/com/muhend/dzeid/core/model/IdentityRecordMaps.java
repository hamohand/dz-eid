package com.muhend.dzeid.core.model;

import com.muhend.dzeid.core.model.IdentityRecord.ActiveAuthentication;
import com.muhend.dzeid.core.model.IdentityRecord.DocumentInfo;
import com.muhend.dzeid.core.model.IdentityRecord.HolderInfo;
import com.muhend.dzeid.core.model.IdentityRecord.MrzSummary;
import com.muhend.dzeid.core.model.IdentityRecord.PassiveAuthentication;
import com.muhend.dzeid.core.model.IdentityRecord.Photo;
import com.muhend.dzeid.core.model.IdentityRecord.SignerInfo;
import com.muhend.dzeid.core.model.IdentityRecord.Verification;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;

import java.util.Map;

/**
 * Conversion d'un {@link IdentityRecord} en arbre {@code Map}/{@code List}/{@code String}/{@code Boolean}/
 * {@code Integer}, <b>sans Jackson ni réflexion</b>.
 *
 * <p>Utilisée pour le transport vers Dart (codec standard des canaux Flutter) et partout où Jackson est
 * indésirable (Android : l'introspection des {@code record} n'y est pas fiable). Même règle que le JSON :
 * les valeurs nulles et les collections ou chaînes vides sont omises.</p>
 */
public final class IdentityRecordMaps {

    private IdentityRecordMaps() {
    }

    public static Map<String, Object> toMap(IdentityRecord r) {
        Map<String, Object> m = new LinkedHashMap<>();
        put(m, "schemaVersion", r.schemaVersion());
        put(m, "readAt", r.readAt());
        put(m, "document", document(r.document()));
        put(m, "holder", holder(r.holder()));
        put(m, "mrz", mrz(r.mrz()));
        put(m, "photo", photo(r.photo()));
        put(m, "signatureImage", photo(r.signatureImage()));
        put(m, "verification", verification(r.verification()));
        put(m, "warnings", r.warnings());
        put(m, "raw", r.raw() == null ? null : new LinkedHashMap<String, Object>(r.raw()));
        return m;
    }

    private static Map<String, Object> document(DocumentInfo d) {
        if (d == null) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        put(m, "type", d.type());
        put(m, "code", d.code());
        put(m, "number", d.number());
        put(m, "issuingState", d.issuingState());
        put(m, "dateOfIssue", d.dateOfIssue());
        put(m, "dateOfExpiry", d.dateOfExpiry());
        put(m, "issuingAuthorityLatin", d.issuingAuthorityLatin());
        put(m, "issuingAuthorityArabic", d.issuingAuthorityArabic());
        put(m, "nameLatin", d.nameLatin());
        put(m, "nameArabic", d.nameArabic());
        return m;
    }

    private static Map<String, Object> holder(HolderInfo h) {
        if (h == null) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        put(m, "nin", h.nin());
        put(m, "lastNameLatin", h.lastNameLatin());
        put(m, "firstNameLatin", h.firstNameLatin());
        put(m, "lastNameArabic", h.lastNameArabic());
        put(m, "firstNameArabic", h.firstNameArabic());
        put(m, "sex", h.sex());
        put(m, "sexArabic", h.sexArabic());
        put(m, "dateOfBirth", h.dateOfBirth());
        put(m, "placeOfBirthLatin", h.placeOfBirthLatin());
        put(m, "placeOfBirthArabic", h.placeOfBirthArabic());
        put(m, "nationality", h.nationality());
        put(m, "bloodGroup", h.bloodGroup());
        put(m, "address", h.address());
        return m;
    }

    private static Map<String, Object> mrz(MrzSummary z) {
        if (z == null) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        put(m, "format", z.format());
        put(m, "lines", z.lines());
        m.put("checkDigitsValid", z.checkDigitsValid());
        return m;
    }

    private static Map<String, Object> photo(Photo p) {
        if (p == null) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        put(m, "mimeType", p.mimeType());
        put(m, "base64", p.base64());
        put(m, "originalMimeType", p.originalMimeType());
        return m;
    }

    private static Map<String, Object> verification(Verification v) {
        if (v == null) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        put(m, "accessMethod", v.accessMethod());
        put(m, "dataGroupsPresent", v.dataGroupsPresent());
        put(m, "dataGroupsRead", v.dataGroupsRead());
        PassiveAuthentication pa = v.passiveAuthentication();
        if (pa != null) {
            Map<String, Object> p = new LinkedHashMap<>();
            put(p, "dataIntegrity", name(pa.dataIntegrity()));
            put(p, "signature", name(pa.signature()));
            put(p, "certificateChain", name(pa.certificateChain()));
            put(p, "digestAlgorithm", pa.digestAlgorithm());
            put(p, "documentSigner", signer(pa.documentSigner()));
            put(p, "summary", pa.summary());
            put(p, "details", pa.details());
            put(m, "passiveAuthentication", p);
        }
        ActiveAuthentication aa = v.activeAuthentication();
        if (aa != null) {
            Map<String, Object> a = new LinkedHashMap<>();
            put(a, "result", name(aa.result()));
            put(a, "algorithm", aa.algorithm());
            put(a, "summary", aa.summary());
            put(m, "activeAuthentication", a);
        }
        return m;
    }

    private static Map<String, Object> signer(SignerInfo s) {
        if (s == null) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        put(m, "subject", s.subject());
        put(m, "issuer", s.issuer());
        put(m, "serialNumber", s.serialNumber());
        put(m, "notBefore", s.notBefore());
        put(m, "notAfter", s.notAfter());
        return m;
    }

    private static String name(Enum<?> e) {
        return e == null ? null : e.name();
    }

    /** Ajoute la valeur sauf si elle est nulle ou vide (même règle que le JSON NON_EMPTY). */
    private static void put(Map<String, Object> m, String key, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String && ((String) value).isEmpty()) {
            return;
        }
        if (value instanceof Collection) {
            if (((Collection<?>) value).isEmpty()) {
                return;
            }
            value = new ArrayList<Object>((Collection<?>) value);
        }
        if (value instanceof Map && ((Map<?, ?>) value).isEmpty()) {
            return;
        }
        m.put(key, value);
    }
}
