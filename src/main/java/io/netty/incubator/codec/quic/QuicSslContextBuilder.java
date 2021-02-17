/*
 * Copyright 2021 The Netty Project
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

package io.netty.incubator.codec.quic;

import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.util.KeyManagerFactoryWrapper;
import io.netty.handler.ssl.util.TrustManagerFactoryWrapper;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * Builder for configuring a new SslContext for creation.
 */
public final class QuicSslContextBuilder {

    /**
     * Creates a builder for new client-side {@link QuicSslContext} that can be used for {@code QUIC}.
     */
    public static QuicSslContextBuilder forClient() {
        return new QuicSslContextBuilder(false);
    }

    /**
     * Creates a builder for new server-side {@link QuicSslContext} that can be used for {@code QUIC}.
     *
     * @param keyFile a PKCS#8 private key file in PEM format
     * @param keyPassword the password of the {@code keyFile}, or {@code null} if it's not
     *     password-protected
     * @param certChainFile an X.509 certificate chain file in PEM format
     * @see #keyManager(File, String, File)
     */
    public static QuicSslContextBuilder forServer(
            File keyFile, String keyPassword, File certChainFile) {
        return new QuicSslContextBuilder(true).keyManager(keyFile, keyPassword, certChainFile);
    }

    /**
     * Creates a builder for new server-side {@link QuicSslContext} that can be used for {@code QUIC}.
     *
     * @param key a PKCS#8 private key
     * @param keyPassword the password of the {@code keyFile}, or {@code null} if it's not
     *     password-protected
     * @param certChain the X.509 certificate chain
     * @see #keyManager(File, String, File)
     */
    public static QuicSslContextBuilder forServer(
            PrivateKey key, String keyPassword, X509Certificate... certChain) {
        return new QuicSslContextBuilder(true).keyManager(key, keyPassword, certChain);
    }

    /**
     * Creates a builder for new server-side {@link QuicSslContext} that can be used for {@code QUIC}.
     *
     * @param keyManagerFactory non-{@code null} factory for server's private key
     * @see #keyManager(KeyManagerFactory, String)
     */
    public static QuicSslContextBuilder forServer(KeyManagerFactory keyManagerFactory, String password) {
        return new QuicSslContextBuilder(true).keyManager(keyManagerFactory, password);
    }

    /**
     * Creates a builder for new server-side {@link QuicSslContext} with {@link KeyManager} that can be used for
     * {@code QUIC}.
     *
     * @param keyManager non-{@code null} KeyManager for server's private key
     * @param keyPassword the password of the {@code keyFile}, or {@code null} if it's not
     *     password-protected
     */
    public static QuicSslContextBuilder forServer(KeyManager keyManager, String keyPassword) {
        return new QuicSslContextBuilder(true).keyManager(keyManager, keyPassword);
    }

    private final boolean forServer;
    private TrustManagerFactory trustManagerFactory;
    private String keyPassword;
    private KeyManagerFactory keyManagerFactory;
    private long sessionCacheSize = 20480;
    private long sessionTimeout = 300;
    private ClientAuth clientAuth = ClientAuth.NONE;
    private String[] applicationProtocols;
    private Boolean earlyData;

    private QuicSslContextBuilder(boolean forServer) {
        this.forServer = forServer;
    }

    /**
     * Enable / disable the usage of early data.
     */
    public QuicSslContextBuilder earlyData(boolean enabled) {
        this.earlyData = enabled;
        return this;
    }

    /**
     * Trusted certificates for verifying the remote endpoint's certificate. The file should
     * contain an X.509 certificate collection in PEM format. {@code null} uses the system default.
     */
    public QuicSslContextBuilder trustManager(File trustCertCollectionFile) {
        try {
            return trustManager(QuicheQuicSslContext.toX509Certificates0(trustCertCollectionFile));
        } catch (Exception e) {
            throw new IllegalArgumentException("File does not contain valid certificates: "
                    + trustCertCollectionFile, e);
        }
    }

    /**
     * Trusted certificates for verifying the remote endpoint's certificate, {@code null} uses the system default.
     */
    public QuicSslContextBuilder trustManager(X509Certificate... trustCertCollection) {
        try {
            return trustManager(QuicheQuicSslContext.buildTrustManagerFactory0(trustCertCollection));
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Trusted manager for verifying the remote endpoint's certificate. {@code null} uses the system default.
     */
    public QuicSslContextBuilder trustManager(TrustManagerFactory trustManagerFactory) {
        this.trustManagerFactory = trustManagerFactory;
        return this;
    }

    /**
     * A single trusted manager for verifying the remote endpoint's certificate.
     * This is helpful when custom implementation of {@link TrustManager} is needed.
     * Internally, a simple wrapper of {@link TrustManagerFactory} that only produces this
     * specified {@link TrustManager} will be created, thus all the requirements specified in
     * {@link #trustManager(TrustManagerFactory trustManagerFactory)} also apply here.
     */
    public QuicSslContextBuilder trustManager(TrustManager trustManager) {
        return trustManager(new TrustManagerFactoryWrapper(trustManager));
    }

    /**
     * Identifying certificate for this host. {@code keyCertChainFile} and {@code keyFile} may
     * be {@code null} for client contexts, which disables mutual authentication.
     *
     * @param keyFile a PKCS#8 private key file in PEM format
     * @param keyPassword the password of the {@code keyFile}, or {@code null} if it's not
     *     password-protected
     * @param keyCertChainFile an X.509 certificate chain file in PEM format
     */
    public QuicSslContextBuilder keyManager(File keyFile, String keyPassword, File keyCertChainFile) {
        X509Certificate[] keyCertChain;
        PrivateKey key;
        try {
            keyCertChain = QuicheQuicSslContext.toX509Certificates0(keyCertChainFile);
        } catch (Exception e) {
            throw new IllegalArgumentException("File does not contain valid certificates: " + keyCertChainFile, e);
        }
        try {
            key = QuicheQuicSslContext.toPrivateKey0(keyFile, keyPassword);
        } catch (Exception e) {
            throw new IllegalArgumentException("File does not contain valid private key: " + keyFile, e);
        }
        return keyManager(key, keyPassword, keyCertChain);
    }

    /**
     * Identifying certificate for this host. {@code keyCertChain} and {@code key} may
     * be {@code null} for client contexts, which disables mutual authentication.
     *
     * @param key a PKCS#8 private key file
     * @param keyPassword the password of the {@code key}, or {@code null} if it's not
     *     password-protected
     * @param certChain an X.509 certificate chain
     */
    public QuicSslContextBuilder keyManager(PrivateKey key, String keyPassword, X509Certificate... certChain) {
        try {
            java.security.KeyStore ks = java.security.KeyStore.getInstance(KeyStore.getDefaultType());
            ks.load(null);
            char[] pass = keyPassword == null ? new char[0]: keyPassword.toCharArray();
            ks.setKeyEntry("alias", key, pass, certChain);
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(
                    KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(ks, pass);
            return keyManager(keyManagerFactory, keyPassword);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Identifying manager for this host. {@code keyManagerFactory} may be {@code null} for
     * client contexts, which disables mutual authentication.
     */
    public QuicSslContextBuilder keyManager(KeyManagerFactory keyManagerFactory, String keyPassword) {
        this.keyPassword = keyPassword;
        this.keyManagerFactory = keyManagerFactory;
        return this;
    }

    /**
     * A single key manager managing the identity information of this host.
     * This is helpful when custom implementation of {@link KeyManager} is needed.
     * Internally, a wrapper of {@link KeyManagerFactory} that only produces this specified
     * {@link KeyManager} will be created, thus all the requirements specified in
     * {@link #keyManager(KeyManagerFactory, String)} also apply here.
     */
    public QuicSslContextBuilder keyManager(KeyManager keyManager, String password) {
        return keyManager(new KeyManagerFactoryWrapper(keyManager), password);
    }

    /**
     * Application protocol negotiation configuration. {@code null} disables support.
     */
    public QuicSslContextBuilder applicationProtocols(String... applicationProtocols) {
        this.applicationProtocols = applicationProtocols;
        return this;
    }

    /**
     * Set the size of the cache used for storing SSL session objects. {@code 0} to use the
     * default value.
     */
    public QuicSslContextBuilder sessionCacheSize(long sessionCacheSize) {
        this.sessionCacheSize = sessionCacheSize;
        return this;
    }

    /**
     * Set the timeout for the cached SSL session objects, in seconds. {@code 0} to use the
     * default value.
     */
    public QuicSslContextBuilder sessionTimeout(long sessionTimeout) {
        this.sessionTimeout = sessionTimeout;
        return this;
    }

    /**
     * Sets the client authentication mode.
     */
    public QuicSslContextBuilder clientAuth(ClientAuth clientAuth) {
        this.clientAuth = checkNotNull(clientAuth, "clientAuth");
        return this;
    }

    /**
     * Create new {@link QuicSslContext} instance with configured settings that can be used for {@code QUIC}.
     *
     */
    public QuicSslContext build() {
        if (forServer) {
            return new QuicheQuicSslContext(true, sessionCacheSize, sessionTimeout, clientAuth,
                    trustManagerFactory, keyManagerFactory, keyPassword, earlyData, applicationProtocols);
        } else {
            return new QuicheQuicSslContext(false, sessionCacheSize, sessionTimeout, clientAuth,
                    trustManagerFactory, keyManagerFactory, keyPassword, earlyData, applicationProtocols);
        }
    }
}
