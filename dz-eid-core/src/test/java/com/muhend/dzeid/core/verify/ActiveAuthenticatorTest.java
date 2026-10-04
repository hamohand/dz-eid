package com.muhend.dzeid.core.verify;

import com.muhend.dzeid.core.CardData;
import com.muhend.dzeid.core.IdentityAssembler;
import com.muhend.dzeid.core.PhotoExtractor;
import com.muhend.dzeid.core.model.IdentityRecord;
import com.muhend.dzeid.core.model.IdentityRecord.ActiveAuthentication;
import com.muhend.dzeid.core.model.IdentityRecord.PassiveAuthentication;
import com.muhend.dzeid.core.model.IdentityRecord.Photo;
import com.muhend.dzeid.core.model.IdentityRecord.Status;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.crypto.Digest;
import org.bouncycastle.crypto.digests.SHA1Digest;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.engines.RSAEngine;
import org.bouncycastle.crypto.params.RSAKeyParameters;
import org.bouncycastle.crypto.signers.ISO9796d2Signer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Active Authentication : les signatures « carte » sont produites par l'implémentation ISO 9796-2
 * de BouncyCastle (référence indépendante), puis vérifiées par {@link ActiveAuthenticator}.
 */
class ActiveAuthenticatorTest {

    private static final SecureRandom RND = new SecureRandom();
    private static KeyPair rsa;

    @BeforeAll
    static void keys() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(1024, RND);
        rsa = g.generateKeyPair();
    }

    /** Simule la puce : signe M1 (aléatoire, longueur maximale récupérable) || défi. */
    private static byte[] cardSignRsa(byte[] challenge, Digest digest, boolean implicit) throws Exception {
        RSAPrivateKey priv = (RSAPrivateKey) rsa.getPrivate();
        int k = (priv.getModulus().bitLength() + 7) / 8;
        int m1Len = k - 1 - digest.getDigestSize() - (implicit ? 1 : 2);
        byte[] message = new byte[m1Len + challenge.length];
        RND.nextBytes(message);
        System.arraycopy(challenge, 0, message, m1Len, challenge.length);

        ISO9796d2Signer signer = new ISO9796d2Signer(new RSAEngine(), digest, implicit);
        signer.init(true, new RSAKeyParameters(true, priv.getModulus(), priv.getPrivateExponent()));
        signer.update(message, 0, message.length);
        return signer.generateSignature();
    }

    private static byte[] challenge() {
        byte[] c = new byte[8];
        RND.nextBytes(c);
        return c;
    }

    @Test
    void rsaSha1TrailerImplicite() throws Exception {
        byte[] c = challenge();
        byte[] response = cardSignRsa(c, new SHA1Digest(), true);
        ActiveAuthentication aa = ActiveAuthenticator.verify(rsa.getPublic(), c, response);
        assertEquals(Status.VALID, aa.result(), aa.summary());
        assertEquals("RSA-1024 ISO 9796-2 / SHA-1", aa.algorithm());
    }

    @Test
    void rsaSha256TrailerExplicite() throws Exception {
        byte[] c = challenge();
        byte[] response = cardSignRsa(c, new SHA256Digest(), false);
        ActiveAuthentication aa = ActiveAuthenticator.verify(rsa.getPublic(), c, response);
        assertEquals(Status.VALID, aa.result(), aa.summary());
        assertTrue(aa.algorithm().endsWith("SHA-256"));
    }

    @Test
    void rsaMauvaisDefiRefuse() throws Exception {
        byte[] c = challenge();
        byte[] response = cardSignRsa(c, new SHA1Digest(), true);
        byte[] autre = c.clone();
        autre[0] ^= 0x01;
        assertEquals(Status.INVALID, ActiveAuthenticator.verify(rsa.getPublic(), autre, response).result());
    }

    @Test
    void rsaReponseAlteréeRefusee() throws Exception {
        byte[] c = challenge();
        byte[] response = cardSignRsa(c, new SHA1Digest(), true);
        response[response.length / 2] ^= 0x40;
        assertEquals(Status.INVALID, ActiveAuthenticator.verify(rsa.getPublic(), c, response).result());
    }

    @Test
    void rsaAutreCleRefusee() throws Exception {
        byte[] c = challenge();
        byte[] response = cardSignRsa(c, new SHA1Digest(), true);
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(1024, RND);
        RSAPublicKey other = (RSAPublicKey) g.generateKeyPair().getPublic();
        assertEquals(Status.INVALID, ActiveAuthenticator.verify(other, c, response).result());
    }

    @Test
    void ecdsaFormatPlain() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
        g.initialize(new ECGenParameterSpec("secp256r1"), RND);
        KeyPair ec = g.generateKeyPair();
        byte[] c = challenge();
        Signature s = Signature.getInstance("SHA256withECDSA");
        s.initSign(ec.getPrivate());
        s.update(c);
        byte[] plain = derToPlain(s.sign(), 32);

        ActiveAuthentication aa = ActiveAuthenticator.verify(ec.getPublic(), c, plain);
        assertEquals(Status.VALID, aa.result(), aa.summary());
        assertEquals("ECDSA-256 / SHA-256", aa.algorithm());

        plain[5] ^= 0x01;
        assertEquals(Status.INVALID, ActiveAuthenticator.verify(ec.getPublic(), c, plain).result());
    }

    @Test
    void reponseAbsente() {
        assertEquals(Status.INVALID, ActiveAuthenticator.verify(rsa.getPublic(), challenge(), new byte[0]).result());
    }

    @Test
    void aaNonProbanteSansIntegriteDuDg15() {
        CardData data = new CardData(new HashMap<>(), null, null, "BAC");
        ActiveAuthentication valid = new ActiveAuthentication(Status.VALID, "RSA-1024 ISO 9796-2 / SHA-1", "ok");
        IdentityRecord r = IdentityAssembler.assemble(data, PassiveAuthentication.notChecked("pas de SOD"), valid, false);
        assertEquals(Status.NOT_CHECKED, r.verification().activeAuthentication().result());
        assertTrue(r.verification().activeAuthentication().summary().contains("non probant"));
    }

    @Test
    void signatureManuscriteDg7() {
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46,
                0x00, 0x01, (byte) 0xFF, (byte) 0xD9};
        byte[] dg7 = new byte[2 + 3 + 3 + jpeg.length];
        int i = 0;
        dg7[i++] = 0x67;
        dg7[i++] = (byte) (3 + 3 + jpeg.length);
        dg7[i++] = 0x02;
        dg7[i++] = 0x01;
        dg7[i++] = 0x01;
        dg7[i++] = 0x5F;
        dg7[i++] = 0x43;
        dg7[i++] = (byte) jpeg.length;
        System.arraycopy(jpeg, 0, dg7, i, jpeg.length);

        Photo sig = PhotoExtractor.extractSignature(dg7);
        assertNotNull(sig);
        assertEquals("image/jpeg", sig.mimeType());
        assertArrayEquals(jpeg, Base64.getDecoder().decode(sig.base64()));
    }

    private static byte[] derToPlain(byte[] der, int len) {
        ASN1Sequence seq = ASN1Sequence.getInstance(der);
        byte[] out = new byte[2 * len];
        copyUnsigned(((ASN1Integer) seq.getObjectAt(0)).getValue(), out, 0, len);
        copyUnsigned(((ASN1Integer) seq.getObjectAt(1)).getValue(), out, len, len);
        return out;
    }

    private static void copyUnsigned(BigInteger v, byte[] out, int off, int len) {
        byte[] b = v.toByteArray();
        int start = b.length > len ? b.length - len : 0;
        int n = b.length - start;
        System.arraycopy(b, start, out, off + len - n, n);
    }
}
