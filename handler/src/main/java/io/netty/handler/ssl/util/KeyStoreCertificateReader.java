/*
 * Copyright 2016 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.ssl.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * Allows to easily extract a {@link PrivateKey} and the {@link X509Certificate} chain out of a {@link KeyStore}.
 */
public final class KeyStoreCertificateReader {

    private final X509Certificate[] certChain;
    private final PrivateKey key;

    /**
     * Creates a new {@link KeyStoreCertificateReader} instance.
     *
     * @param jks the JKS file from which load the {@link KeyStore}.
     * @param alias the alias or {@code null} if we should try to auto-detect.
     * @param password the password or {@code null} if no password is needed to load the {@link KeyStore}.
     */
    public KeyStoreCertificateReader(File jks, String alias, String password)
            throws KeyStoreException, UnrecoverableKeyException, IOException,
            NoSuchAlgorithmException, CertificateException {
        this(keyStore(checkNotNull(jks, "jks"), password), alias, password);
    }

    /**
     * Creates a new {@link KeyStoreCertificateReader} instance.
     *
     * @param ks the {@link KeyStore}.
     * @param alias the alias or {@code null} if we should try to auto-detect.
     * @param password the password or {@code null} if no password is needed to load the {@link KeyStore}.
     */
    public KeyStoreCertificateReader(KeyStore ks, String alias, String password)
            throws KeyStoreException, UnrecoverableKeyException, NoSuchAlgorithmException {
        alias = alias(checkNotNull(ks, "ks"), alias);
        certChain = certificateChain(ks, alias);
        key = privateKey(ks, alias, password);
    }

    /**
     * Returns the {@link PrivateKey}.
     */
    public PrivateKey key() {
        return key;
    }

    /**
     * Return the {@link X509Certificate} chain.
     */
    public X509Certificate[] certificateChain() {
        return certChain.clone();
    }

    private static X509Certificate[] certificateChain(KeyStore ks, String alias) throws KeyStoreException {
        Certificate[] certs = ks.getCertificateChain(alias);

        if (certs == null) {
            throw new KeyStoreException("no certificate chain for alias: " + alias);
        }

        X509Certificate[] x509Certs = new X509Certificate[certs.length];
        for (int i = 0; i < certs.length; i++) {
            Certificate cert = certs[i];
            if (!(cert instanceof X509Certificate)) {
                throw new IllegalArgumentException("certificate chain contains non X.509 certificate: " + cert);
            }
            x509Certs[i] = (X509Certificate) cert;
        }
        return x509Certs;
    }

   private static PrivateKey privateKey(KeyStore ks, String alias, String password)
            throws KeyStoreException, UnrecoverableKeyException, NoSuchAlgorithmException {
       Key key = ks.getKey(alias, toChars(password));
       if (key == null) {
           throw new KeyStoreException("no key for alias: " + alias);
       }
       if (!(key instanceof PrivateKey)) {
           throw new KeyStoreException("key is not an instanceof PrivateKey: " + key);
       }
       return (PrivateKey) key;
    }

    private static KeyStore keyStore(File file, String password)
            throws KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException {
        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(new FileInputStream(file), toChars(password));
        return ks;
    }

    private static char[] toChars(String password) {
        return password == null ? null : password.toCharArray();
    }

    private static String alias(KeyStore ks, String alias) throws KeyStoreException {
        if (alias != null) {
            return alias;
        }
        Enumeration<String> e = ks.aliases();
        List<String> aliases = new ArrayList<String>();

        while (e.hasMoreElements()) {
            aliases.add(e.nextElement());
        }

        if (aliases.size() == 1) {
            return aliases.get(0);
        } else {
            throw new KeyStoreException("unable to auto-select alias because more than one alias was found");
        }
    }
}
