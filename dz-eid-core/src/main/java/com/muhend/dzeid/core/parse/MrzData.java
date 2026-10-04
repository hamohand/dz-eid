package com.muhend.dzeid.core.parse;

import java.util.List;

/**
 * Contenu décodé d'une MRZ (DG1 ou saisie).
 *
 * @param format            TD1 (carte d'identité, 3×30), TD2 (2×36) ou TD3 (passeport, 2×44)
 * @param dateOfBirth       format brut {@code AAMMJJ}
 * @param dateOfExpiry      format brut {@code AAMMJJ}
 * @param sex               {@code M}, {@code F} ou {@code X}
 * @param invalidChecks     noms des chiffres de contrôle incorrects (vide si tout est valide)
 */
public record MrzData(
        String format,
        String documentCode,
        String issuingState,
        String documentNumber,
        String optionalData1,
        String dateOfBirth,
        String sex,
        String dateOfExpiry,
        String nationality,
        String optionalData2,
        String primaryIdentifier,
        String secondaryIdentifier,
        List<String> lines,
        List<String> invalidChecks) {

    public boolean checkDigitsValid() {
        return invalidChecks.isEmpty();
    }
}
