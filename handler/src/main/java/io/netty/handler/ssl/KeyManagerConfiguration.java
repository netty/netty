/*
 * Copyright 2017 The Netty Project
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

package io.netty.handler.ssl;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

import java.io.File;
import java.io.InputStream;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;

/**
 * Configures the KeyManager for an SslContextBuilder.
 *
 * If the instance is not for configuring a server, key and keyCertChain may be left unspecified,
 * which disables mutual authentication.
 *
 * If the key is not password protected, the password may be left unspecified.
 */
public final class KeyManagerConfiguration {

    private PrivateKey key;
    private X509Certificate[] keyCertChain;
    private String keyPassword;

    /**
     * Creates a new instance.
     */
    public KeyManagerConfiguration() { }

    /**
     * Sets the private key for this host.
     *
     * @param key a PKCS#8 private key
     * @param keyPassword the password of the key, or null if it's not password protected
     */
    public KeyManagerConfiguration key(PrivateKey key, String keyPassword) {
        this.key = key;
        this.keyPassword = keyPassword;
        return this;
    }

    /**
     * Sets the private key for this host.
     *
     * @param key a PKCS#8 private key file in PEM format
     * @param keyPassword the password of the key, or null if it's not password protected
     */
    public KeyManagerConfiguration key(File keyFile, String keyPassword) {
        try {
            return key(SslContext.toPrivateKey(keyFile, keyPassword), keyPassword);
        } catch (Exception e) {
            throw new IllegalArgumentException("File does not contain valid private key: " + keyFile, e);
        }
    }

    /**
     * Sets the private key for this host.
     *
     * @param key a PKCS#8 private key input stream in PEM format
     * @param keyPassword the password of the key, or null if it's not password protected
     */
    public KeyManagerConfiguration key(InputStream keyInputStream, String keyPassword) {
        try {
            return key(SslContext.toPrivateKey(keyInputStream, keyPassword), keyPassword);
        } catch (Exception e) {
            throw new IllegalArgumentException("Input stream does not contain valid private key.", e);
        }
    }

    /**
     * Retrieves the private key for this host.
     *
     * @note Should not be invoked before calling {@code validate}
     */
    PrivateKey getKey() {
        return key;
    }

    /**
     * Retrieves the password for the private key for this host.
     *
     * @note Should not be invoked before calling {@code validate}
     */
    String getPassword() {
        return keyPassword;
    }

    /**
     * Sets the X.509 certificate chain.
     *
     * @param keyCertChain an X.509 certificate chain
     */
    public KeyManagerConfiguration keyCertChain(X509Certificate... keyCertChain) {
        if (keyCertChain == null || keyCertChain.length == 0) {
            this.keyCertChain = null;
        } else {
            this.keyCertChain = keyCertChain.clone();
        }
        return this;
    }

    /**
     * Sets the X.509 certificate chain.
     *
     * @param keyCertChain X.509 certificate chain files in PEM format
     */
    public KeyManagerConfiguration keyCertChain(File... keyCertChainFile) {
        ArrayList<X509Certificate> certs = new ArrayList<X509Certificate>();
        for (File file : keyCertChainFile) {
            try {
                Collections.addAll(certs, SslContext.toX509Certificates(file));
            } catch (Exception e) {
                throw new IllegalArgumentException("File does not contain valid certificates: " + file, e);
            }
        }
        return keyCertChain(certs.toArray(new X509Certificate[certs.size()]));
    }

    /**
     * Sets the X.509 certificate chain.
     *
     * @param keyCertChain X.509 certificate input stream in PEM format
     */
    public KeyManagerConfiguration keyCertChain(InputStream keyCertChainInputStream) {
        try {
            return keyCertChain(SslContext.toX509Certificates(keyCertChainInputStream));
        } catch (Exception e) {
            throw new IllegalArgumentException("Input stream not contain valid certificates.", e);
        }
    }

    /**
     * Retrieves the X.509 certificate chain.
     *
     * @note Should not be invoked before calling {@code validate}
     */
    X509Certificate[] getKeyCertChain() {
        return keyCertChain;
    }

    /**
     * Throws an IllegalArgumentException if the configuration is invalid, returns otherwise.
     */
    public void validate(boolean forServer) {
        if (forServer) {
            checkNotNull(keyCertChain, "keyCertChain required for servers");
            if (keyCertChain.length == 0) {
                throw new IllegalArgumentException("keyCertChain must be non-empty");
            }
            checkNotNull(key, "key required for servers");
        }
        if (keyCertChain != null && keyCertChain.length != 0) {
            for (X509Certificate cert: keyCertChain) {
                if (cert == null) {
                    throw new IllegalArgumentException("keyCertChain contains null entry");
                }
            }
        }
    }
}
