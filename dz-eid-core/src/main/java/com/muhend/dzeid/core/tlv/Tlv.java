package com.muhend.dzeid.core.tlv;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Nœud BER-TLV (Tag-Length-Value).
 *
 * <p>Le tag est représenté par l'entier formé de tous ses octets, par ex. {@code 0x5F0E}.</p>
 */
public final class Tlv {

    private final int tag;
    private final byte[] value;
    private final List<Tlv> children;

    Tlv(int tag, byte[] value, List<Tlv> children) {
        this.tag = tag;
        this.value = value;
        this.children = children == null ? Collections.emptyList() : Collections.unmodifiableList(children);
    }

    public int tag() {
        return tag;
    }

    /** Valeur brute (pour un nœud construit : l'encodage de ses enfants). */
    public byte[] value() {
        return value.clone();
    }

    public List<Tlv> children() {
        return children;
    }

    public boolean isConstructed() {
        int firstByte = tag;
        while (firstByte > 0xFF) {
            firstByte >>>= 8;
        }
        return (firstByte & 0x20) != 0;
    }

    /** Premier descendant (recherche en profondeur) portant ce tag, ou {@code null}. */
    public Tlv find(int searchedTag) {
        for (Tlv child : children) {
            if (child.tag == searchedTag) {
                return child;
            }
            Tlv deeper = child.find(searchedTag);
            if (deeper != null) {
                return deeper;
            }
        }
        return null;
    }

    /** Tous les descendants portant ce tag, dans l'ordre du document. */
    public List<Tlv> findAll(int searchedTag) {
        List<Tlv> out = new ArrayList<>();
        collect(searchedTag, out);
        return out;
    }

    private void collect(int searchedTag, List<Tlv> out) {
        for (Tlv child : children) {
            if (child.tag == searchedTag) {
                out.add(child);
            }
            child.collect(searchedTag, out);
        }
    }

    @Override
    public String toString() {
        return String.format("Tlv[%X, %d octets, %d enfants]", tag, value.length, children.size());
    }
}
