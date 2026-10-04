package com.muhend.dzeid.core;

import com.muhend.dzeid.core.model.IdentityRecord;
import com.muhend.dzeid.core.model.IdentityRecord.ActiveAuthentication;
import com.muhend.dzeid.core.model.IdentityRecord.DocumentInfo;
import com.muhend.dzeid.core.model.IdentityRecord.HolderInfo;
import com.muhend.dzeid.core.model.IdentityRecord.MrzSummary;
import com.muhend.dzeid.core.model.IdentityRecord.PassiveAuthentication;
import com.muhend.dzeid.core.model.IdentityRecord.Photo;
import com.muhend.dzeid.core.model.IdentityRecord.Verification;
import com.muhend.dzeid.core.parse.Dg11Data;
import com.muhend.dzeid.core.parse.Dg11Parser;
import com.muhend.dzeid.core.parse.Dg12Data;
import com.muhend.dzeid.core.parse.Dg12Parser;
import com.muhend.dzeid.core.parse.DzDates;
import com.muhend.dzeid.core.parse.MrzData;
import com.muhend.dzeid.core.parse.MrzParser;
import com.muhend.dzeid.core.text.DzText.Bilingual;
import com.muhend.dzeid.core.util.Hex;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Assemble un {@link IdentityRecord} à partir des données brutes de la puce.
 *
 * <p>Fonction pure, sans accès matériel : elle est entièrement testable et réutilisable
 * pour décoder des données reçues d'un autre appareil.</p>
 *
 * <p>Règles de priorité : la puce (DG11/DG12) est préférée à la MRZ quand elle est plus complète
 * (noms non tronqués, années sur 4 chiffres). Les incohérences produisent des avertissements.</p>
 */
public final class IdentityAssembler {

    private IdentityAssembler() {
    }

    public static IdentityRecord assemble(CardData data, PassiveAuthentication passiveAuth, boolean includeRaw) {
        return assemble(data, passiveAuth, ActiveAuthentication.notChecked(
                "Contrôle anti-clonage possible uniquement pendant une lecture de la carte."), includeRaw);
    }

    /**
     * @param activeAuth résultat de l'Active Authentication réalisée pendant la lecture
     *                   (ne peut pas être rejouée hors ligne : le défi est aléatoire)
     */
    public static IdentityRecord assemble(CardData data, PassiveAuthentication passiveAuth,
                                          ActiveAuthentication activeAuth, boolean includeRaw) {
        List<String> warnings = new ArrayList<>();

        MrzData mrz = null;
        byte[] dg1 = data.dataGroup(1);
        if (dg1 == null) {
            warnings.add("DG1 (MRZ) absent.");
        } else {
            try {
                mrz = MrzParser.parseDg1(dg1);
                if (!mrz.checkDigitsValid()) {
                    warnings.add("MRZ de la puce : chiffres de contrôle invalides (" + String.join(", ", mrz.invalidChecks()) + ").");
                }
            } catch (RuntimeException e) {
                warnings.add("DG1 illisible : " + e.getMessage());
            }
        }

        Dg11Data dg11 = null;
        if (data.dataGroup(11) != null) {
            try {
                dg11 = Dg11Parser.parse(data.dataGroup(11));
            } catch (RuntimeException e) {
                warnings.add("DG11 illisible : " + e.getMessage());
            }
        }

        Dg12Data dg12 = null;
        if (data.dataGroup(12) != null) {
            try {
                dg12 = Dg12Parser.parse(data.dataGroup(12));
            } catch (RuntimeException e) {
                warnings.add("DG12 illisible : " + e.getMessage());
            }
        }

        Photo photo = null;
        if (data.dataGroup(2) != null) {
            photo = PhotoExtractor.extract(data.dataGroup(2));
            if (photo == null) {
                warnings.add("Photo (DG2) présente mais non décodable.");
            }
        }

        Photo signatureImage = null;
        if (data.dataGroup(7) != null) {
            signatureImage = PhotoExtractor.extractSignature(data.dataGroup(7));
            if (signatureImage == null) {
                warnings.add("Signature manuscrite (DG7) présente mais non décodable.");
            }
        }

        DocumentInfo document = buildDocument(mrz, dg12, warnings);
        HolderInfo holder = buildHolder(mrz, dg11, warnings);
        MrzSummary mrzSummary = mrz == null ? null : new MrzSummary(mrz.format(), mrz.lines(), mrz.checkDigitsValid());

        ActiveAuthentication aa = activeAuth;
        if (aa != null && aa.result() == IdentityRecord.Status.VALID
                && (passiveAuth == null || passiveAuth.dataIntegrity() != IdentityRecord.Status.VALID)) {
            // Sans empreinte du DG15 validée, un clone pourrait présenter sa propre clé : non probant
            aa = new ActiveAuthentication(IdentityRecord.Status.NOT_CHECKED, aa.algorithm(),
                    "Anti-clonage non probant : la clé de la puce (DG15) n'est pas garantie par la signature de l'État.");
        }

        Verification verification = new Verification(data.accessMethod(), data.dataGroupsPresent(),
                new ArrayList<>(data.dataGroups().keySet()), passiveAuth, aa);

        Map<String, String> raw = null;
        if (includeRaw) {
            raw = new LinkedHashMap<>();
            for (Map.Entry<Integer, byte[]> e : data.dataGroups().entrySet()) {
                raw.put("dg" + e.getKey(), Hex.encode(e.getValue()));
            }
            if (data.sod() != null) {
                raw.put("sod", Hex.encode(data.sod()));
            }
        }

        return new IdentityRecord(IdentityRecord.SCHEMA_VERSION, Instant.now().toString(), document, holder,
                mrzSummary, photo, signatureImage, verification, Collections.unmodifiableList(warnings), raw);
    }

    private static DocumentInfo buildDocument(MrzData mrz, Dg12Data dg12, List<String> warnings) {
        String code = mrz == null ? null : mrz.documentCode();
        String mrzExpiry = mrz == null ? null : DzDates.fromMrzExpiryDate(mrz.dateOfExpiry());
        String expiry = mrzExpiry;
        if (dg12 != null && dg12.dateOfExpiryFull() != null) {
            if (mrzExpiry == null || mrzExpiry.equals(dg12.dateOfExpiryFull())) {
                expiry = dg12.dateOfExpiryFull();
            } else {
                warnings.add("Date d'expiration différente entre la MRZ (" + mrzExpiry + ") et le DG12 ("
                        + dg12.dateOfExpiryFull() + ") : la MRZ fait foi.");
            }
        }
        Bilingual authority = dg12 == null ? Bilingual.EMPTY : dg12.issuingAuthority();
        Bilingual name = dg12 == null ? Bilingual.EMPTY : dg12.documentName();
        return new DocumentInfo(
                documentType(code),
                code,
                mrz == null ? null : mrz.documentNumber(),
                mrz == null ? null : mrz.issuingState(),
                dg12 == null ? null : dg12.dateOfIssue(),
                expiry,
                authority.latin(),
                authority.arabic(),
                name.latin(),
                name.arabic());
    }

    private static HolderInfo buildHolder(MrzData mrz, Dg11Data dg11, List<String> warnings) {
        // Profil algérien : noms bilingues LATIN<<ARABE dans le DG11
        boolean bilingualNames = dg11 != null && dg11.fullName().arabic() != null;

        String lastLatin = mrz == null ? null : mrz.primaryIdentifier();
        String firstLatin = mrz == null ? null : mrz.secondaryIdentifier();
        String lastArabic = null;
        String firstArabic = null;
        if (bilingualNames) {
            if (dg11.fullName().latin() != null) {
                lastLatin = dg11.fullName().latin();
            }
            lastArabic = dg11.fullName().arabic();
            List<String> latinNames = new ArrayList<>();
            List<String> arabicNames = new ArrayList<>();
            for (Bilingual b : dg11.otherNames()) {
                if (b.latin() != null) {
                    latinNames.add(b.latin());
                }
                if (b.arabic() != null) {
                    arabicNames.add(b.arabic());
                }
            }
            if (!latinNames.isEmpty()) {
                firstLatin = String.join(" ", latinNames);
            }
            if (!arabicNames.isEmpty()) {
                firstArabic = String.join(" ", arabicNames);
            }
        }

        String mrzBirth = mrz == null ? null : DzDates.fromMrzBirthDate(mrz.dateOfBirth());
        String birth = mrzBirth;
        if (dg11 != null && dg11.fullDateOfBirth() != null) {
            String chipBirth = dg11.fullDateOfBirth();
            if (mrzBirth == null || chipBirth.substring(2).equals(mrzBirth.substring(2))) {
                birth = chipBirth; // l'année sur 4 chiffres lève l'ambiguïté du siècle
            } else {
                warnings.add("Date de naissance différente entre la MRZ (" + mrzBirth + ") et le DG11 (" + chipBirth + ").");
            }
        }

        String sex = mrz == null ? null : mrz.sex();
        if ((sex == null || "X".equals(sex)) && dg11 != null && dg11.sexLatin() != null) {
            sex = dg11.sexLatin();
        }

        String nin = dg11 == null ? null : dg11.personalNumber();
        if (nin == null && mrz != null && mrz.optionalData2() != null && mrz.optionalData2().matches("\\d+")) {
            nin = mrz.optionalData2();
        }

        Bilingual place = dg11 == null ? Bilingual.EMPTY : dg11.placeOfBirth();
        return new HolderInfo(
                nin,
                lastLatin,
                firstLatin,
                lastArabic,
                firstArabic,
                sex,
                dg11 == null ? null : dg11.sexArabic(),
                birth,
                place.latin(),
                place.arabic(),
                mrz == null ? null : mrz.nationality(),
                dg11 == null ? null : dg11.bloodGroup(),
                dg11 == null ? null : dg11.address());
    }

    private static String documentType(String code) {
        if (code == null || code.isEmpty()) {
            return null;
        }
        char c = code.charAt(0);
        if (c == 'P') {
            return "PASSPORT";
        }
        if (c == 'I' || c == 'A' || c == 'C') {
            return "ID_CARD";
        }
        return "OTHER";
    }
}
