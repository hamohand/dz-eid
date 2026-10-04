package com.muhend.dzeid.core.parse;

import com.muhend.dzeid.core.text.DzText.Bilingual;

import java.util.Map;

/**
 * Contenu décodé du DG12 (informations complémentaires sur le document).
 *
 * <p>Particularités algériennes observées :</p>
 * <ul>
 *   <li>5F19 : autorité de délivrance, format {@code WILAYA_AUTORITÉ-WILAYA<<ARABE} ;</li>
 *   <li>5F1B : (ICAO « mentions ») contient la date d'expiration complète {@code AAAAMMJJ} ;</li>
 *   <li>5F1D : (ICAO « image recto ») contient l'intitulé du document en texte bilingue.</li>
 * </ul>
 *
 * @param dateOfIssue        5F26, ISO 8601
 * @param dateOfExpiryFull   5F1B lorsqu'il contient une date, ISO 8601
 * @param endorsements       5F1B lorsqu'il contient du texte
 * @param documentName       5F1D lorsqu'il contient du texte
 */
public record Dg12Data(
        Bilingual issuingAuthority,
        String dateOfIssue,
        String dateOfExpiryFull,
        String endorsements,
        Bilingual documentName,
        Map<String, String> otherFields) {
}
