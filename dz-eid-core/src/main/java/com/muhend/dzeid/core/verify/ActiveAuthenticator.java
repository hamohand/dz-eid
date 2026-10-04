package com.muhend.dzeid.core.verify;

import com.muhend.dzeid.core.model.IdentityRecord.ActiveAuthentication;
import com.muhend.dzeid.core.model.IdentityRecord.Status;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.DERSequence;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;

/**
 * Vérification de l'Active Authentication (ICAO 9303 partie 11, §6.1) : contrôle de la réponse de la puce
 * à un défi aléatoire, avec la clé publique du DG15. Fonctions pures, sans accès matériel.
 *
 * <ul>
 *   <li><b>RSA</b> : signature ISO/IEC 9796-2 schéma 1 à récupération partielle.
 *       Bloc récupéré {@code F = en-tête || M1 || H || trailer} avec {@code H = Hash(M1 || défi)} ;
 *       trailer {@code BC} = SHA-1 implicite, {@code xxCC} = fonction de hachage explicite.</li>
 *   <li><b>ECDSA</b> : signature au format « plain » {@code r || s} (BSI TR-03111) ou DER ;
 *       sans DG14 la fonction de hachage n'est pas annoncée, on essaie donc les SHA-2 et SHA-1.</li>
 * </ul>
 *
 * <p>La confiance dans la clé du DG15 provient de la Passive Authentication (empreinte du DG15 signée
 * dans le SOD) : ce contrôle doit donc être lu conjointement avec l'intégrité des données.</p>
 */
public final class ActiveAuthenticator {

    private static final String[] EC_DIGESTS = {"SHA256", "SHA384", "SHA512", "SHA224", "SHA1"};

    private ActiveAuthenticator() {
    }

    public static ActiveAuthentication verify(PublicKey key, byte[] challenge, byte[] response) {
        if (key == null || challenge == null || response == null || response.length == 0) {
            return new ActiveAuthentication(Status.INVALID, null, "Réponse de la puce absente : puce non authentifiée.");
        }
        try {
            if (key instanceof RSAPublicKey) {
                return verifyRsa((RSAPublicKey) key, challenge, response);
            }
            if (key instanceof ECPublicKey) {
                return verifyEc((ECPublicKey) key, challenge, response);
            }
            return new ActiveAuthentication(Status.INVALID, key.getAlgorithm(),
                    "Type de clé non pris en charge : " + key.getAlgorithm() + ".");
        } catch (Exception e) {
            return new ActiveAuthentication(Status.INVALID, key.getAlgorithm(),
                    "Réponse de la puce invalide (" + e.getClass().getSimpleName() + ").");
        }
    }

    // ------------------------------------------------------------------ RSA / ISO 9796-2

    static ActiveAuthentication verifyRsa(RSAPublicKey key, byte[] challenge, byte[] response) throws Exception {
        BigInteger n = key.getModulus();
        String algo = "RSA-" + n.bitLength() + " ISO 9796-2";
        BigInteger s = new BigInteger(1, response);
        if (s.signum() == 0 || s.compareTo(n) >= 0) {
            return invalid(algo);
        }
        BigInteger m = s.modPow(key.getPublicExponent(), n);
        byte[] f = unsigned(m);
        if (!looksLike9796(f)) {
            // Variante autorisée par la norme : représentant min(m, n − m)
            f = unsigned(n.subtract(m));
            if (!looksLike9796(f)) {
                return invalid(algo);
            }
        }

        int last = f[f.length - 1] & 0xFF;
        String digestName;
        int trailerLen;
        if (last == 0xBC) {
            digestName = "SHA-1";
            trailerLen = 1;
        } else {
            digestName = digestFromTrailer(f[f.length - 2] & 0xFF);
            trailerLen = 2;
            if (digestName == null) {
                return invalid(algo);
            }
        }
        MessageDigest md = MessageDigest.getInstance(digestName);
        int hLen = md.getDigestLength();

        int mStart = 0;
        while (mStart < f.length && (f[mStart] & 0x0F) != 0x0A) {
            mStart++;
        }
        mStart++;
        int hEnd = f.length - trailerLen;
        int hStart = hEnd - hLen;
        if (hStart < mStart) {
            return invalid(algo + " / " + digestName);
        }
        md.update(f, mStart, hStart - mStart); // M1 (partie récupérée)
        md.update(challenge);                   // M2 (défi envoyé à la puce)
        byte[] expected = md.digest();
        byte[] actual = new byte[hLen];
        System.arraycopy(f, hStart, actual, 0, hLen);

        algo = algo + " / " + digestName;
        return MessageDigest.isEqual(expected, actual) ? valid(algo) : invalid(algo);
    }

    private static boolean looksLike9796(byte[] f) {
        return f.length > 2 && (f[0] & 0xC0) == 0x40 && (f[f.length - 1] & 0x0F) == 0x0C;
    }

    private static String digestFromTrailer(int id) {
        switch (id) {
            case 0x33:
                return "SHA-1";
            case 0x34:
                return "SHA-256";
            case 0x35:
                return "SHA-512";
            case 0x36:
                return "SHA-384";
            case 0x38:
                return "SHA-224";
            default:
                return null;
        }
    }

    // ------------------------------------------------------------------ ECDSA

    static ActiveAuthentication verifyEc(ECPublicKey key, byte[] challenge, byte[] response) throws Exception {
        int fieldBytes = (key.getParams().getCurve().getField().getFieldSize() + 7) / 8;
        byte[] der;
        if ((response[0] & 0xFF) == 0x30) {
            der = response;
        } else if (response.length % 2 == 0) {
            int half = response.length / 2;
            ASN1EncodableVector v = new ASN1EncodableVector();
            v.add(new ASN1Integer(new BigInteger(1, java.util.Arrays.copyOfRange(response, 0, half))));
            v.add(new ASN1Integer(new BigInteger(1, java.util.Arrays.copyOfRange(response, half, response.length))));
            der = new DERSequence(v).getEncoded();
        } else {
            return invalid("ECDSA-" + fieldBytes * 8);
        }
        for (String digest : EC_DIGESTS) {
            Signature sig = Signature.getInstance(digest + "withECDSA", Crypto.BC);
            sig.initVerify(key);
            sig.update(challenge);
            if (sig.verify(der)) {
                return valid("ECDSA-" + fieldBytes * 8 + " / " + digest.replace("SHA", "SHA-"));
            }
        }
        return invalid("ECDSA-" + fieldBytes * 8);
    }

    // ------------------------------------------------------------------ utilitaires

    private static ActiveAuthentication valid(String algo) {
        return new ActiveAuthentication(Status.VALID, algo,
                "Puce authentique : elle a prouvé détenir sa clé secrète (la carte n'est pas un clone).");
    }

    private static ActiveAuthentication invalid(String algo) {
        return new ActiveAuthentication(Status.INVALID, algo,
                "Réponse de la puce incorrecte : la puce n'a pas pu prouver son authenticité (clone possible).");
    }

    private static byte[] unsigned(BigInteger v) {
        byte[] b = v.toByteArray();
        int i = 0;
        while (i < b.length - 1 && b[i] == 0) {
            i++;
        }
        return java.util.Arrays.copyOfRange(b, i, b.length);
    }
}
