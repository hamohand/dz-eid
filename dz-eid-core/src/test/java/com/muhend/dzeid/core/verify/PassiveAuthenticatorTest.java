package com.muhend.dzeid.core.verify;

import com.muhend.dzeid.core.model.IdentityRecord.PassiveAuthentication;
import com.muhend.dzeid.core.model.IdentityRecord.Status;
import com.muhend.dzeid.core.testing.DzCardFixtures;
import org.bouncycastle.asn1.ASN1Encoding;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.icao.DataGroupHash;
import org.bouncycastle.asn1.icao.LDSSecurityObject;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Passive Authentication sur un SOD synthétique, signé par une fausse autorité CSCA de test.
 */
class PassiveAuthenticatorTest {

    private static X509Certificate csca;
    private static X509Certificate otherCsca;
    private static byte[] sod;
    private static Map<Integer, byte[]> dataGroups;

    @BeforeAll
    static void buildSignedSod() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA", Crypto.BC);
        kpg.initialize(2048);
        KeyPair cscaKeys = kpg.generateKeyPair();
        KeyPair dsKeys = kpg.generateKeyPair();
        KeyPair otherKeys = kpg.generateKeyPair();

        csca = certificate("CN=CSCA TEST,C=DZ", "CN=CSCA TEST,C=DZ", cscaKeys.getPublic(), cscaKeys.getPrivate(), 1);
        X509Certificate ds = certificate("CN=DS TEST,C=DZ", "CN=CSCA TEST,C=DZ", dsKeys.getPublic(), cscaKeys.getPrivate(), 2);
        // Même nom que le vrai CSCA mais autre clé : simule une fausse autorité
        otherCsca = certificate("CN=CSCA TEST,C=DZ", "CN=CSCA TEST,C=DZ", otherKeys.getPublic(), otherKeys.getPrivate(), 3);

        dataGroups = new LinkedHashMap<>();
        dataGroups.put(1, DzCardFixtures.dg1());
        dataGroups.put(11, DzCardFixtures.dg11());
        dataGroups.put(12, DzCardFixtures.dg12());

        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        DataGroupHash[] hashes = dataGroups.entrySet().stream()
                .map(e -> new DataGroupHash(e.getKey(), new DEROctetString(sha256.digest(e.getValue()))))
                .toArray(DataGroupHash[]::new);
        LDSSecurityObject lds = new LDSSecurityObject(new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha256), hashes);

        CMSSignedDataGenerator gen = new CMSSignedDataGenerator();
        gen.addSignerInfoGenerator(new JcaSignerInfoGeneratorBuilder(
                new JcaDigestCalculatorProviderBuilder().setProvider(Crypto.BC).build())
                .build(new JcaContentSignerBuilder("SHA256withRSA").setProvider(Crypto.BC).build(dsKeys.getPrivate()), ds));
        gen.addCertificate(new JcaX509CertificateHolder(ds));
        byte[] cms = gen.generate(new CMSProcessableByteArray(new ASN1ObjectIdentifier("2.23.136.1.1.1"),
                lds.getEncoded(ASN1Encoding.DER)), true).getEncoded();
        sod = DzCardFixtures.tlv(0x77, cms);
    }

    @Test
    void authenticDocumentWithTrustedCsca() {
        PassiveAuthentication pa = PassiveAuthenticator.verify(sod, dataGroups, CscaStore.of(Collections.singletonList(csca)));
        assertEquals(Status.VALID, pa.dataIntegrity(), String.valueOf(pa.details()));
        assertEquals(Status.VALID, pa.signature(), String.valueOf(pa.details()));
        assertEquals(Status.VALID, pa.certificateChain(), String.valueOf(pa.details()));
        assertEquals("SHA-256", pa.digestAlgorithm());
        assertNotNull(pa.documentSigner());
        assertTrue(pa.summary().startsWith("Document authentique"));
    }

    @Test
    void withoutCscaChainIsNotChecked() {
        PassiveAuthentication pa = PassiveAuthenticator.verify(sod, dataGroups, CscaStore.empty());
        assertEquals(Status.VALID, pa.dataIntegrity());
        assertEquals(Status.VALID, pa.signature());
        assertEquals(Status.NOT_CHECKED, pa.certificateChain());
    }

    @Test
    void detectsModifiedDataGroup() {
        Map<Integer, byte[]> tampered = new LinkedHashMap<>(dataGroups);
        byte[] dg11 = tampered.get(11).clone();
        dg11[dg11.length - 1] ^= 0x01; // un seul bit modifié
        tampered.put(11, dg11);
        PassiveAuthentication pa = PassiveAuthenticator.verify(sod, tampered, CscaStore.empty());
        assertEquals(Status.INVALID, pa.dataIntegrity());
        assertTrue(pa.summary().startsWith("ALERTE"));
    }

    @Test
    void detectsUntrustedSigner() {
        PassiveAuthentication pa = PassiveAuthenticator.verify(sod, dataGroups, CscaStore.of(Collections.singletonList(otherCsca)));
        assertEquals(Status.VALID, pa.signature());
        assertEquals(Status.INVALID, pa.certificateChain());
    }

    @Test
    void detectsTamperedSignedHashes() throws Exception {
        // Un fraudeur remplace l'empreinte signée du DG1 : intégrité ET signature doivent échouer
        byte[] dg1Hash = MessageDigest.getInstance("SHA-256").digest(dataGroups.get(1));
        byte[] broken = sod.clone();
        int at = indexOf(broken, dg1Hash);
        assertTrue(at > 0, "empreinte DG1 introuvable dans le SOD");
        broken[at] ^= 0x01;
        PassiveAuthentication pa = PassiveAuthenticator.verify(broken, dataGroups, CscaStore.empty());
        assertEquals(Status.INVALID, pa.dataIntegrity());
        assertEquals(Status.INVALID, pa.signature());
    }

    @Test
    void anyCorruptionIsDetectedWithTrustedCsca() {
        // Avec un CSCA de confiance, aucune altération (y compris du certificat DS) ne passe inaperçue
        CscaStore trusted = CscaStore.of(Collections.singletonList(csca));
        for (int pos = 4; pos < sod.length; pos += 37) {
            byte[] broken = sod.clone();
            broken[pos] ^= 0x55;
            PassiveAuthentication pa = PassiveAuthenticator.verify(broken, dataGroups, trusted);
            boolean allValid = pa.dataIntegrity() == Status.VALID && pa.signature() == Status.VALID
                    && pa.certificateChain() == Status.VALID;
            assertTrue(!allValid, "corruption non détectée à l'octet " + pos);
        }
    }

    private static int indexOf(byte[] data, byte[] pattern) {
        outer:
        for (int i = 0; i <= data.length - pattern.length; i++) {
            for (int j = 0; j < pattern.length; j++) {
                if (data[i + j] != pattern[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private static X509Certificate certificate(String subject, String issuer, PublicKey key, PrivateKey signer,
                                               long serial) throws Exception {
        Date from = new Date(System.currentTimeMillis() - 86_400_000L);
        Date to = new Date(System.currentTimeMillis() + 3650L * 86_400_000L);
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(new X500Name(issuer),
                BigInteger.valueOf(serial), from, to, new X500Name(subject), key);
        return new JcaX509CertificateConverter().setProvider(Crypto.BC).getCertificate(
                builder.build(new JcaContentSignerBuilder("SHA256withRSA").setProvider(Crypto.BC).build(signer)));
    }
}
