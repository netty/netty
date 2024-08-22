/*
 * Copyright 2024 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.testcert;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

/**
 * A certificate bundle is a private key and a full certificate path, all the way to the root certificate.
 * The bundle offers ways of accessing these, and converting them into various representations.
 */
public final class X509Bundle {
    private static final char[] EMPTY_CHARS = new char[0];

    private final X509Certificate[] certPath;
    private final X509Certificate root;
    private final KeyPair keyPair;

    /**
     * Construct a bundle from a given certificate path, root certificate, and {@link KeyPair}.
     * @param certPath The certificate path, starting with the leaf certificate.The path can end either with the
     * root certificate, or the intermediate certificate signed by the root certificate.
     * @param root The self-signed root certificate.
     * @param keyPair The key pair.
     */
    public X509Bundle(X509Certificate[] certPath, X509Certificate root, KeyPair keyPair) {
        if (certPath.length > 1 && certPath[certPath.length - 1].equals(root)) {
            this.certPath = Arrays.copyOf(certPath, certPath.length - 1);
        } else {
            this.certPath = certPath.clone();
        }
        this.root = root;
        this.keyPair = keyPair;
    }

    /**
     * Construct a bundle for a certificate authority.
     * @param root The self-signed root certificate.
     * @param keyPair The key pair.
     * @return The new bundle.
     */
    public static X509Bundle fromRootCertificateAuthority(X509Certificate root, KeyPair keyPair) {
        X509Bundle bundle = new X509Bundle(new X509Certificate[]{root}, root, keyPair);
        if (!bundle.isCertificateAuthority() || !bundle.isSelfSigned()) {
            throw new IllegalArgumentException("Given certificate is not a root CA certificate: " +
                    root.getSubjectX500Principal() + ", issued by " + root.getIssuerX500Principal());
        }
        return bundle;
    }

    /**
     * Construct a bundle from a given certificate path, root certificate, and {@link KeyPair}.
     * @param certPath The certificate path, starting with the leaf certificate.The path can end either with the
     * root certificate, or the intermediate certificate signed by the root certificate.
     * @param root The self-signed root certificate.
     * @param keyPair The key pair.
     */
    public static X509Bundle fromCertificatePath(
            X509Certificate[] certPath, X509Certificate root, KeyPair keyPair) {
        return new X509Bundle(certPath, root, keyPair);
    }

    /**
     * Get the leaf certificate of the bundle.
     * If this bundle is for a certificate authority, then this return the same as {@link #getRootCertificate()}.
     * @return The leaf certificate.
     */
    public X509Certificate getCertificate() {
        return certPath[0];
    }

    /**
     * Get the PEM encoded string of the {@linkplain #getCertificate() leaf certificate}.
     * @return The certificate PEM string.
     */
    public String getCertificatePEM() {
        return toCertPem(certPath[0]);
    }

    /**
     * Get the certificate path, starting with the leaf certificate up to but excluding the root certificate.
     * @return The certificate path.
     */
    public X509Certificate[] getCertificatePath() {
        return certPath.clone();
    }

    /**
     * Get the certificate path, starting with the leaf certificate up to and including the root certificate.
     * @return The certificate path, including the root certificate.
     */
    public X509Certificate[] getCertificatePathWithRoot() {
        X509Certificate[] path = Arrays.copyOf(certPath, certPath.length + 1);
        path[path.length - 1] = root;
        return path;
    }

    /**
     * Get the certificate path as a list, starting with the leaf certificate up to but excluding the root certificate.
     * @return The certificate path list.
     */
    public List<X509Certificate> getCertificatePathList() {
        return List.of(certPath);
    }

    /**
     * Get the {@linkplain #getCertificatePath() certificate path} as a PEM encoded string.
     * @return The PEM encoded certificate path.
     */
    public String getCertificatePathPEM() {
        return toCertPem(certPath);
    }

    /**
     * Get the key pair.
     * @return The key pair.
     */
    public KeyPair getKeyPair() {
        return keyPair;
    }

    /**
     * Get the root certificate that anchors the certificate path.
     * @return The root certificate.
     */
    public X509Certificate getRootCertificate() {
        return root;
    }

    /**
     * Get the {@linkplain #getRootCertificate() root certificate} as a PEM encoded string.
     * @return The PEM encoded root certificate.
     */
    public String getRootCertificatePEM() {
        return toCertPem(root);
    }

    private static String toCertPem(X509Certificate... certs) {
        Base64.Encoder encoder = Base64.getMimeEncoder();
        StringBuilder sb = new StringBuilder();
        for (X509Certificate cert : certs) {
            sb.append("-----BEGIN CERTIFICATE-----\r\n");
            try {
                sb.append(encoder.encodeToString(cert.getEncoded()));
            } catch (CertificateEncodingException e) {
                throw new IllegalStateException(e);
            }
            sb.append("\r\n-----END CERTIFICATE-----\r\n");
        }
        return sb.toString();
    }

    /**
     * Get the private key as a PEM encoded PKCS#8 string.
     * @return The private key in PKCS#8 and PEM encoded string.
     */
    public String getPrivateKeyPEM() {
        Base64.Encoder encoder = Base64.getMimeEncoder();
        StringBuilder sb = new StringBuilder();
        sb.append("-----BEGIN PRIVATE KEY-----\r\n");
        sb.append(encoder.encodeToString(keyPair.getPrivate().getEncoded()));
        sb.append("\r\n-----END PRIVATE KEY-----\r\n");
        return sb.toString();
    }

    /**
     * Get the root certificate as a new {@link TrustAnchor} object.
     * Note that {@link TrustAnchor} instance have object identity, so if this method is called twice,
     * the two trust anchors will not be equal to each other.
     * @return A new {@link TrustAnchor} instance containing the root certificate.
     */
    public TrustAnchor getTrustAnchor() {
        return new TrustAnchor(root, root.getExtensionValue(CertificateBuilder.OID_X509_NAME_CONSTRAINTS));
    }

    /**
     * Query if this bundle is for a certificate authority root certificate.
     * @return {@code true} if the {@linkplain #getCertificate() leaf certificate} is a certificate authority,
     * otherwise {@code false}.
     */
    public boolean isCertificateAuthority() {
        return certPath[0].getBasicConstraints() != -1;
    }

    /**
     * Query if this bundle is for a self-signed certificate.
     * @return {@code true} if the {@linkplain #getCertificate() leaf certificate} is self-signed.
     */
    public boolean isSelfSigned() {
        X509Certificate leaf = certPath[0];
        return certPath.length == 1 &&
                leaf.getSubjectX500Principal().equals(leaf.getIssuerX500Principal()) &&
                Arrays.equals(leaf.getSubjectUniqueID(), leaf.getIssuerUniqueID());
    }

    /**
     * Create a {@link TrustManager} instance that trusts the root certificate in this bundle.
     * @return The new {@link TrustManager}.
     */
    public TrustManager toTrustManager() {
        TrustManagerFactory tmf = toTrustManagerFactory();
        return tmf.getTrustManagers()[0];
    }

    /**
     * Create  {@link TrustManagerFactory} instance that trusts the root certificate in this bundle.
     * @return The new {@link TrustManagerFactory}.
     */
    public TrustManagerFactory toTrustManagerFactory() {
        try {
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(toKeyStore(EMPTY_CHARS));
            return tmf;
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("Default TrustManagerFactory algorithm was not available.", e);
        } catch (KeyStoreException e) {
            throw new IllegalStateException("Failed to initialize TrustManagerFactory with KeyStore.", e);
        }
    }

    /**
     * Create a {@link KeyStore} with the contents of this bundle.
     * The root certificate will be a trusted root in the key store.
     * If this bundle is not a {@linkplain #isCertificateAuthority() certificate authority},
     * then the private key and certificate path will also be added to the key store.
     * @param keyEntryPassword The password used to encrypt the private key entry in the key store.
     * @return The key store.
     * @throws KeyStoreException If an error occurred when adding entries to the key store.
     */
    public KeyStore toKeyStore(char[] keyEntryPassword) throws KeyStoreException {
        KeyStore keyStore;
        try {
            keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(null, null);
        } catch (KeyStoreException | IOException | NoSuchAlgorithmException | CertificateException e) {
            throw new AssertionError("Failed to initialize PKCS#12 KeyStore.", e);
        }
        keyStore.setCertificateEntry("1", root);
        if (!isCertificateAuthority()) {
            keyStore.setKeyEntry("2", keyPair.getPrivate(), keyEntryPassword, certPath);
        }
        return keyStore;
    }

    /**
     * Create a temporary PKCS#12 file with the {@linkplain #toKeyStore(char[]) key store} of this bundle.
     * The temporary file is automatically deleted when the JVM terminates normally.
     * @param password The password used both to encrypt the private key in the key store,
     * and to protect the key store itself.
     * @return The {@link File} object with the path to the PKCS#12 key store.
     * @throws Exception If something went wrong with creating the key store file.
     */
    public File toTempKeyStoreFile(char[] password) throws Exception {
        return toTempKeyStoreFile(password, password);
    }

    /**
     * Create a temporary PKCS#12 file with the {@linkplain #toKeyStore(char[]) key store} of this bundle.
     * The temporary file is automatically deleted when the JVM terminates normally.
     * @param pkcs12Password The password used to encrypt the PKCS#12 file.
     * @param keyEntryPassword The password used to encrypt the private key entry in the PKCS#12 file.
     * @return The {@link File} object with the path to the PKCS#12 key store.
     * @throws Exception If something went wrong with creating the key store file.
     */
    public File toTempKeyStoreFile(char[] pkcs12Password, char[] keyEntryPassword) throws Exception {
        KeyStore keyStore = toKeyStore(keyEntryPassword);
        Path tempFile = Files.createTempFile("ks", ".p12");
        try (OutputStream out = Files.newOutputStream(tempFile, StandardOpenOption.WRITE)) {
            keyStore.store(out, pkcs12Password);
        }
        File file = tempFile.toFile();
        file.deleteOnExit();
        return file;
    }

    /**
     * Create a temporary PEM file with the {@linkplain #getRootCertificate() root certificate} of this bundle.
     * The temporary file is automatically deleted whent he JVM terminates normally.
     * @return The {@link File} object with the path to the trust root PEM file.
     * @throws IOException If an IO error occurred when creating the trust root file.
     */
    public File toTempRootCertPem() throws IOException {
        Path tempFile = Files.createTempFile("ca", ".pem");
        try (OutputStream out = Files.newOutputStream(tempFile, StandardOpenOption.WRITE)) {
            out.write(getRootCertificatePEM().getBytes(StandardCharsets.ISO_8859_1));
        }
        File file = tempFile.toFile();
        file.deleteOnExit();
        return file;
    }

    /**
     * Create a {@link KeyManagerFactory} from this bundle.
     * @return The new {@link KeyManagerFactory}.
     * @throws KeyStoreException If there was a problem creating or initializing the key store.
     * @throws UnrecoverableKeyException If the private key could not be recovered,
     * for instance if this bundle is a {@linkplain #isCertificateAuthority() certificate authority}.
     * @throws NoSuchAlgorithmException If the key manager factory algorithm is not supported by the current
     * security provider.
     */
    public KeyManagerFactory toKeyManagerFactory()
            throws KeyStoreException, UnrecoverableKeyException, NoSuchAlgorithmException {
        KeyManagerFactory kmf;
        try {
            kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("Default KeyManagerFactory algorithm was not available.", e);
        }
        kmf.init(toKeyStore(EMPTY_CHARS), EMPTY_CHARS);
        return kmf;
    }
}
