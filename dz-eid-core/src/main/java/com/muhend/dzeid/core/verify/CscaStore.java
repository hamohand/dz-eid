package com.muhend.dzeid.core.verify;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Magasin des certificats racines CSCA (Country Signing Certificate Authority) de confiance.
 *
 * <p>Tant qu'aucun certificat CSCA algérien n'est installé, la vérification de chaîne
 * renvoie {@code NOT_CHECKED} : l'intégrité et la signature restent vérifiées.</p>
 */
public final class CscaStore {

    private static final Logger LOG = Logger.getLogger(CscaStore.class.getName());

    private final List<X509Certificate> certificates;

    private CscaStore(List<X509Certificate> certificates) {
        this.certificates = Collections.unmodifiableList(new ArrayList<>(certificates));
    }

    public static CscaStore empty() {
        return new CscaStore(Collections.emptyList());
    }

    public static CscaStore of(List<X509Certificate> certificates) {
        return new CscaStore(certificates);
    }

    /**
     * Charge tous les certificats (.cer, .crt, .der, .pem) d'un répertoire.
     * Les fichiers illisibles sont ignorés et journalisés.
     */
    public static CscaStore fromDirectory(File directory) {
        List<X509Certificate> out = new ArrayList<>();
        File[] files = directory == null ? null : directory.listFiles();
        if (files == null) {
            return empty();
        }
        for (File f : files) {
            String name = f.getName().toLowerCase();
            if (!(name.endsWith(".cer") || name.endsWith(".crt") || name.endsWith(".der") || name.endsWith(".pem"))) {
                continue;
            }
            try (InputStream in = new FileInputStream(f)) {
                out.addAll(readCertificates(in));
            } catch (IOException | CertificateException e) {
                LOG.log(Level.WARNING, "Certificat CSCA illisible : " + f.getName(), e);
            }
        }
        return new CscaStore(out);
    }

    public static List<X509Certificate> readCertificates(InputStream in) throws CertificateException {
        CertificateFactory factory = CertificateFactory.getInstance("X.509", Crypto.BC);
        Collection<? extends Certificate> certs = factory.generateCertificates(in);
        List<X509Certificate> out = new ArrayList<>();
        for (Certificate c : certs) {
            if (c instanceof X509Certificate) {
                out.add((X509Certificate) c);
            }
        }
        return out;
    }

    public List<X509Certificate> certificates() {
        return certificates;
    }

    public boolean isEmpty() {
        return certificates.isEmpty();
    }

    public int size() {
        return certificates.size();
    }
}
