package com.muhend.dzeid.core.verify;

import com.muhend.dzeid.core.model.IdentityRecord.PassiveAuthentication;
import com.muhend.dzeid.core.model.IdentityRecord.SignerInfo;
import com.muhend.dzeid.core.model.IdentityRecord.Status;
import com.muhend.dzeid.core.tlv.TlvParser;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.icao.DataGroupHash;
import org.bouncycastle.asn1.icao.LDSSecurityObject;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;

import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Passive Authentication ICAO 9303 (partie 11) : prouve que les données lues ont bien été écrites
 * par l'État émetteur et n'ont pas été modifiées.
 *
 * <ol>
 *   <li><b>Intégrité</b> : l'empreinte de chaque DG lu est comparée à celle signée dans le SOD ;</li>
 *   <li><b>Signature</b> : la signature CMS du SOD est vérifiée avec le certificat du signataire (DS) ;</li>
 *   <li><b>Chaîne</b> : le certificat DS est vérifié avec un certificat racine CSCA de confiance.</li>
 * </ol>
 */
public final class PassiveAuthenticator {

    private static final Map<String, String> DIGESTS = new HashMap<>();

    static {
        DIGESTS.put("1.3.14.3.2.26", "SHA-1");
        DIGESTS.put("2.16.840.1.101.3.4.2.4", "SHA-224");
        DIGESTS.put("2.16.840.1.101.3.4.2.1", "SHA-256");
        DIGESTS.put("2.16.840.1.101.3.4.2.2", "SHA-384");
        DIGESTS.put("2.16.840.1.101.3.4.2.3", "SHA-512");
    }

    private PassiveAuthenticator() {
    }

    /**
     * @param sod         contenu brut du fichier EF.SOD (tag 0x77 inclus)
     * @param dataGroups  DG lus sur la puce (numéro → octets bruts complets)
     * @param cscaStore   certificats racines de confiance (peut être vide)
     */
    public static PassiveAuthentication verify(byte[] sod, Map<Integer, byte[]> dataGroups, CscaStore cscaStore) {
        List<String> details = new ArrayList<>();
        if (sod == null || sod.length == 0) {
            return PassiveAuthentication.notChecked("Fichier SOD absent : authenticité non vérifiable.");
        }

        CMSSignedData signedData;
        LDSSecurityObject lds;
        try {
            byte[] cms = (sod[0] & 0xFF) == 0x77 ? TlvParser.unwrap(sod) : sod;
            signedData = new CMSSignedData(cms);
            byte[] eContent = (byte[]) signedData.getSignedContent().getContent();
            lds = LDSSecurityObject.getInstance(ASN1Primitive.fromByteArray(eContent));
        } catch (Exception e) {
            details.add("SOD illisible : " + e.getMessage());
            return new PassiveAuthentication(Status.INVALID, Status.INVALID, Status.NOT_CHECKED, null, null,
                    "Structure de sécurité (SOD) invalide.", Collections.unmodifiableList(details));
        }

        // 1. Intégrité des groupes de données
        ASN1ObjectIdentifier digestOid = lds.getDigestAlgorithmIdentifier().getAlgorithm();
        String digestName = DIGESTS.getOrDefault(digestOid.getId(), digestOid.getId());
        Status integrity = checkIntegrity(lds, digestName, dataGroups, details);

        // 2. Signature du SOD
        X509Certificate ds = null;
        Status signature;
        try {
            Iterator<SignerInformation> signers = signedData.getSignerInfos().getSigners().iterator();
            if (!signers.hasNext()) {
                throw new IllegalStateException("aucun signataire dans le SOD");
            }
            SignerInformation signer = signers.next();
            @SuppressWarnings("unchecked")
            Collection<X509CertificateHolder> matches = signedData.getCertificates().getMatches(signer.getSID());
            if (matches.isEmpty()) {
                signature = Status.NOT_CHECKED;
                details.add("Certificat du signataire (DS) absent du SOD.");
            } else {
                ds = new JcaX509CertificateConverter().setProvider(Crypto.BC).getCertificate(matches.iterator().next());
                boolean ok = signer.verify(new JcaSimpleSignerInfoVerifierBuilder().setProvider(Crypto.BC).build(ds));
                signature = ok ? Status.VALID : Status.INVALID;
                if (!ok) {
                    details.add("La signature du SOD ne correspond pas au certificat DS.");
                }
            }
        } catch (Exception e) {
            signature = Status.INVALID;
            details.add("Vérification de la signature impossible : " + e.getMessage());
        }

        // 3. Chaîne de confiance DS → CSCA
        Status chain = checkChain(ds, cscaStore, details);

        return new PassiveAuthentication(integrity, signature, chain, digestName, signerInfo(ds),
                summary(integrity, signature, chain), Collections.unmodifiableList(details));
    }

    private static Status checkIntegrity(LDSSecurityObject lds, String digestName, Map<Integer, byte[]> dataGroups,
                                         List<String> details) {
        if (dataGroups == null || dataGroups.isEmpty()) {
            details.add("Aucun groupe de données à vérifier.");
            return Status.NOT_CHECKED;
        }
        Map<Integer, byte[]> expected = new HashMap<>();
        for (DataGroupHash h : lds.getDatagroupHash()) {
            expected.put(h.getDataGroupNumber(), h.getDataGroupHashValue().getOctets());
        }
        MessageDigest md;
        try {
            md = MessageDigest.getInstance(digestName, Crypto.BC);
        } catch (Exception e) {
            details.add("Algorithme d'empreinte non supporté : " + digestName);
            return Status.NOT_CHECKED;
        }
        boolean allValid = true;
        for (Map.Entry<Integer, byte[]> dg : dataGroups.entrySet()) {
            byte[] ref = expected.get(dg.getKey());
            if (ref == null) {
                allValid = false;
                details.add("DG" + dg.getKey() + " absent de la liste signée (SOD).");
                continue;
            }
            byte[] actual = md.digest(dg.getValue());
            if (!MessageDigest.isEqual(ref, actual)) {
                allValid = false;
                details.add("DG" + dg.getKey() + " : empreinte différente de celle signée (données modifiées).");
            }
        }
        return allValid ? Status.VALID : Status.INVALID;
    }

    private static Status checkChain(X509Certificate ds, CscaStore store, List<String> details) {
        if (ds == null) {
            return Status.NOT_CHECKED;
        }
        if (store == null || store.isEmpty()) {
            details.add("Aucun certificat CSCA installé : chaîne de confiance non vérifiée.");
            return Status.NOT_CHECKED;
        }
        boolean issuerFound = false;
        for (X509Certificate csca : store.certificates()) {
            if (!csca.getSubjectX500Principal().equals(ds.getIssuerX500Principal())) {
                continue;
            }
            issuerFound = true;
            try {
                ds.verify(csca.getPublicKey(), Crypto.BC);
                return Status.VALID;
            } catch (Exception ignored) {
                // essayer le CSCA suivant (renouvellement de clé)
            }
        }
        if (!issuerFound) {
            details.add("Aucun certificat CSCA correspondant à l'émetteur : " + ds.getIssuerX500Principal().getName());
            return Status.NOT_CHECKED;
        }
        details.add("Le certificat DS n'est signé par aucun CSCA de confiance.");
        return Status.INVALID;
    }

    private static SignerInfo signerInfo(X509Certificate ds) {
        if (ds == null) {
            return null;
        }
        return new SignerInfo(ds.getSubjectX500Principal().getName(), ds.getIssuerX500Principal().getName(),
                ds.getSerialNumber().toString(16), ds.getNotBefore().toInstant().toString(),
                ds.getNotAfter().toInstant().toString());
    }

    private static String summary(Status integrity, Status signature, Status chain) {
        if (integrity == Status.INVALID || signature == Status.INVALID || chain == Status.INVALID) {
            return "ALERTE : les données de la puce ne sont pas authentiques ou ont été modifiées.";
        }
        if (integrity == Status.VALID && signature == Status.VALID && chain == Status.VALID) {
            return "Document authentique : données intactes, signées par l'État émetteur (chaîne CSCA vérifiée).";
        }
        if (integrity == Status.VALID && signature == Status.VALID) {
            return "Données intactes et signature valide. Chaîne CSCA non vérifiée (certificat racine non installé).";
        }
        if (integrity == Status.VALID) {
            return "Données intactes. Signature non vérifiée.";
        }
        return "Authenticité non vérifiée.";
    }
}
