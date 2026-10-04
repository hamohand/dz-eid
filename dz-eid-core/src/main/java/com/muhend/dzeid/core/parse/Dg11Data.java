package com.muhend.dzeid.core.parse;

import com.muhend.dzeid.core.text.DzText.Bilingual;

import java.util.List;
import java.util.Map;

/**
 * Contenu décodé du DG11 (informations personnelles complémentaires).
 *
 * <p>Sur la CNIe algérienne, le tag 5F42 (normalement « adresse ») contient en réalité
 * {@code SEXE<<SEXE_ARABE<<GROUPE_SANGUIN}, par ex. {@code M<<ذكر<<A+}. Le parseur reconnaît ce
 * motif ; sinon la valeur est traitée comme une adresse ICAO standard.</p>
 *
 * @param fullName         5F0E — nom (latin / arabe)
 * @param otherNames       5F0F — prénoms (latin / arabe), dans la structure A0
 * @param personalNumber   5F10 — NIN (numéro d'identification national, 18 chiffres)
 * @param fullDateOfBirth  5F2B — date de naissance complète, ISO 8601
 * @param placeOfBirth     5F11 — lieu de naissance (latin / arabe)
 * @param otherFields      autres tags rencontrés (clé = tag hexadécimal, valeur = texte décodé)
 */
public record Dg11Data(
        Bilingual fullName,
        List<Bilingual> otherNames,
        String personalNumber,
        String fullDateOfBirth,
        Bilingual placeOfBirth,
        String sexLatin,
        String sexArabic,
        String bloodGroup,
        String address,
        String telephone,
        String profession,
        Map<String, String> otherFields) {
}
