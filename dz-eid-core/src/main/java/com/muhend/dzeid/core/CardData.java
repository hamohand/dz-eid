package com.muhend.dzeid.core;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Données brutes lues sur une puce, indépendantes du matériel.
 * Permet aussi de rejouer un décodage hors ligne (tests, diagnostic, données transmises par un mobile).
 *
 * @param dataGroups        DG lus (numéro → octets bruts complets, tag inclus)
 * @param sod               fichier EF.SOD brut, ou {@code null}
 * @param dataGroupsPresent DG annoncés par EF.COM (vide si inconnu)
 * @param accessMethod      {@code PACE}, {@code BAC} ou {@code OFFLINE}
 */
public record CardData(Map<Integer, byte[]> dataGroups, byte[] sod, List<Integer> dataGroupsPresent,
                       String accessMethod) {

    public CardData {
        dataGroups = Collections.unmodifiableMap(new TreeMap<>(dataGroups));
        dataGroupsPresent = dataGroupsPresent == null ? Collections.emptyList()
                : Collections.unmodifiableList(dataGroupsPresent);
    }

    public byte[] dataGroup(int number) {
        return dataGroups.get(number);
    }
}
