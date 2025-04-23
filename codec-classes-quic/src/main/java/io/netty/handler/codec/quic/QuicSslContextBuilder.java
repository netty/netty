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

package io.netty.handler.codec.quic;

import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContextOption;
import io.netty.handler.ssl.util.KeyManagerFactoryWrapper;
import io.netty.handler.ssl.util.TrustManagerFactoryWrapper;
import io.netty.util.Mapping;
import org.jetbrains.annotations.Nullable;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedKeyManager;
import java.io.File;
import java.net.Socket;
import java.security.KeyStore;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * Builder for configuring a new SslContext for creation.
 */
public final class QuicSslContextBuilder {

    /**
     * Special {@link X509ExtendedKeyManager} implementation which will just fail the certificate selection.
     * This is used as a "dummy" implementation when SNI is used as we should always select an other
     * {@link QuicSslContext} based on the provided hostname.
     */
    private static final X509ExtendedKeyManager SNI_KEYMANAGER = new X509ExtendedKeyManager() {
        private final X509Certificate[] emptyCerts = new X509Certificate[0];
        private final String[] emptyStrings = new String[0];

        @Override
        public String[] getClientAliases(String keyType, Principal[] issuers) {
            return emptyStrings;
        }

        @Override
        @Nullable
        public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket) {
            return null;
        }

        @Override
        public String[] getServerAliases(String keyType, Principal[] issuers) {
            return emptyStrings;
        }

        @Override
        @Nullable
        public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
            return null;
        }

        @Override
        public X509Certificate[] getCertificateChain(String alias) {
            return emptyCerts;
        }

        @Override
        @Nullable
        public PrivateKey getPrivateKey(String alias) {
            return null;
        }
    };

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
            File keyFile, @Nullable String keyPassword, File certChainFile) {
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
            PrivateKey key, @Nullable String keyPassword, X509Certificate... certChain) {
        return new QuicSslContextBuilder(true).keyManager(key, keyPassword, certChain);
    }

    /**
     * Creates a builder for new server-side {@link QuicSslContext} that can be used for {@code QUIC}.
     *
     * @param keyManagerFactory non-{@code null} factory for server's private key
     * @see #keyManager(KeyManagerFactory, String)
     */
    public static QuicSslContextBuilder forServer(KeyManagerFactory keyManagerFactory, @Nullable String password) {
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
    public static QuicSslContextBuilder forServer(KeyManager keyManager, @Nullable String keyPassword) {
        return new QuicSslContextBuilder(true).keyManager(keyManager, keyPassword);
    }

    /**
     * Enables support for
     * <a href="https://quicwg.org/ops-drafts/draft-ietf-quic-manageability.html#name-server-name-indication-sni">
     *     SNI</a> on the server side.
     *
     * @param mapping   the {@link Mapping} that is used to map names to the {@link QuicSslContext} to use.
     *                  Usually using {@link io.netty.util.DomainWildcardMappingBuilder} should be used
     *                  to create the {@link Mapping}.
     */
    public static QuicSslContext buildForServerWithSni(Mapping<? super String, ? extends QuicSslContext> mapping) {
        return forServer(SNI_KEYMANAGER, null).sni(mapping).build();
    }

    private static final Map.Entry[] EMPTY_ENTRIES = new Map.Entry[0];

    private final boolean forServer;
    private final Map<SslContextOption<?>, Object> options = new HashMap<>();
    private TrustManagerFactory trustManagerFactory;
    private String keyPassword;
    private KeyManagerFactory keyManagerFactory;
    private long sessionCacheSize = 20480;
    private long sessionTimeout = 300;
    private ClientAuth clientAuth = ClientAuth.NONE;
    private String[] applicationProtocols;
    private Boolean earlyData;
    private BoringSSLKeylog keylog;
    private Mapping<? super String, ? extends QuicSslContext> mapping;

    private QuicSslContextBuilder(boolean forServer) {
        this.forServer = forServer;
    }

    private QuicSslContextBuilder sni(Mapping<? super String, ? extends QuicSslContext> mapping) {
        this.mapping = checkNotNull(mapping, "mapping");
        return this;
    }

    /**
     * Configure a {@link SslContextOption}.
     */
    public <T> QuicSslContextBuilder option(SslContextOption<T> option, T value) {
        if (value == null) {
            options.remove(option);
        } else {
            options.put(option, value);
        }
        return this;
    }

    /**
     * Enable / disable the usage of early data.
     */
    public QuicSslContextBuilder earlyData(boolean enabled) {
        this.earlyData = enabled;
        return this;
    }

    /**
     * Enable / disable keylog. When enabled, TLS keys are logged to an internal logger named
     * "io.netty.handler.codec.quic.BoringSSLLogginKeylog" with DEBUG level, see
     * {@link BoringSSLKeylog} for detail, logging keys are following
     * <a href="https://developer.mozilla.org/en-US/docs/Mozilla/Projects/NSS/Key_Log_Format">
     *     NSS Key Log Format</a>. This is intended for debugging use with tools like Wireshark.
     */
    public QuicSslContextBuilder keylog(boolean enabled) {
        keylog(enabled ? BoringSSLLoggingKeylog.INSTANCE : null);
        return this;
    }

    /**
     * Enable / disable keylog. When enabled, TLS keys are logged to {@link BoringSSLKeylog#logKey(SSLEngine, String)}
     * logging keys are following
     * <a href="https://developer.mozilla.org/en-US/docs/Mozilla/Projects/NSS/Key_Log_Format">
     *     NSS Key Log Format</a>. This is intended for debugging use with tools like Wireshark.
     */
    public QuicSslContextBuilder keylog(@Nullable BoringSSLKeylog keylog) {
        this.keylog = keylog;
        return this;
    }

    /**
     * Trusted certificates for verifying the remote endpoint's certificate. The file should
     * contain an X.509 certificate collection in PEM format. {@code null} uses the system default
     * which only works with Java 8u261 and later as these versions support TLS1.3,
     * see <a href="https://www.oracle.com/java/technologies/javase/8u261-relnotes.html">
     *     JDK 8u261 Update Release Notes</a>
     */
    public QuicSslContextBuilder trustManager(@Nullable File trustCertCollectionFile) {
        try {
            return trustManager(QuicheQuicSslContext.toX509Certificates0(trustCertCollectionFile));
        } catch (Exception e) {
            throw new IllegalArgumentException("File does not contain valid certificates: "
                    + trustCertCollectionFile, e);
        }
    }

    /**
     * Trusted certificates for verifying the remote endpoint's certificate. {@code null} uses the system default
     * which only works with Java 8u261 and later as these versions support TLS1.3,
     * see <a href="https://www.oracle.com/java/technologies/javase/8u261-relnotes.html">
     *     JDK 8u261 Update Release Notes</a>
     */
    public QuicSslContextBuilder trustManager(X509Certificate @Nullable ... trustCertCollection) {
        try {
            return trustManager(QuicheQuicSslContext.buildTrustManagerFactory0(trustCertCollection));
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Trusted manager for verifying the remote endpoint's certificate. {@code null} uses the system default
     * which only works with Java 8u261 and later as these versions support TLS1.3,
     * see <a href="https://www.oracle.com/java/technologies/javase/8u261-relnotes.html">
     *     JDK 8u261 Update Release Notes</a>
     */
    public QuicSslContextBuilder trustManager(@Nullable TrustManagerFactory trustManagerFactory) {
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
    public QuicSslContextBuilder keyManager(
            @Nullable File keyFile, @Nullable String keyPassword, @Nullable File keyCertChainFile) {
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
    public QuicSslContextBuilder keyManager(
            @Nullable PrivateKey key, @Nullable String keyPassword, X509Certificate @Nullable ... certChain) {
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
    public QuicSslContextBuilder keyManager(
            @Nullable KeyManagerFactory keyManagerFactory, @Nullable String keyPassword) {
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
    public QuicSslContextBuilder keyManager(KeyManager keyManager, @Nullable String password) {
        return keyManager(new KeyManagerFactoryWrapper(keyManager), password);
    }

    /**
     * Application protocol negotiation configuration. {@code null} disables support.
     */
    public QuicSslContextBuilder applicationProtocols(String @Nullable ... applicationProtocols) {
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
        if (!forServer) {
            throw new UnsupportedOperationException("Only supported for server");
        }
        this.clientAuth = checkNotNull(clientAuth, "clientAuth");
        return this;
    }

    /**
     * Create new {@link QuicSslContext} instance with configured settings that can be used for {@code QUIC}.
     *
     */
    public QuicSslContext build() {
        if (forServer) {
            return new QuicheQuicSslContext(true, sessionTimeout, sessionCacheSize, clientAuth, trustManagerFactory,
                    keyManagerFactory, keyPassword, mapping, earlyData, keylog,
                    applicationProtocols, toArray(options.entrySet(), EMPTY_ENTRIES));
        } else {
            return new QuicheQuicSslContext(false, sessionTimeout, sessionCacheSize, clientAuth, trustManagerFactory,
                    keyManagerFactory, keyPassword, mapping, earlyData, keylog,
                    applicationProtocols, toArray(options.entrySet(), EMPTY_ENTRIES));
        }
    }

    private static <T> T[] toArray(Iterable<? extends T> iterable, T[] prototype) {
        if (iterable == null) {
            return null;
        }
        final List<T> list = new ArrayList<>();
        for (T element : iterable) {
            list.add(element);
        }
        return list.toArray(prototype);
    }
}
