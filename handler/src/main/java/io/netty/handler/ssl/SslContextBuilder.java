/*
 * Copyright 2015 The Netty Project
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

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;

/**
 * Builder for configuring a new SslContext for creation.
 */
public final class SslContextBuilder {
    /**
     * Creates a builder for new client-side {@link SslContext}.
     */
    public static SslContextBuilder forClient() {
        return new SslContextBuilder(false);
    }

    /**
     * Creates a builder for new server-side {@link SslContext}.
     *
     * @param keyCertChainFile an X.509 certificate chain file in PEM format
     * @param keyFile a PKCS#8 private key file in PEM format
     */
    public static SslContextBuilder forServer(File keyCertChainFile, File keyFile) {
        return new SslContextBuilder(true).keyManager(keyCertChainFile, keyFile);
    }

    /**
     * Creates a builder for new server-side {@link SslContext}.
     *
     * @param keyCertChainFile an X.509 certificate chain file in PEM format
     * @param keyFile a PKCS#8 private key file in PEM format
     * @param keyPassword the password of the {@code keyFile}, or {@code null} if it's not
     *     password-protected
     */
    public static SslContextBuilder forServer(
            File keyCertChainFile, File keyFile, String keyPassword) {
        return new SslContextBuilder(true).keyManager(keyCertChainFile, keyFile, keyPassword);
    }

    private final boolean forServer;
    private SslProvider provider;
    private File trustCertChainFile;
    private TrustManagerFactory trustManagerFactory;
    private File keyCertChainFile;
    private File keyFile;
    private String keyPassword;
    private KeyManagerFactory keyManagerFactory;
    private Iterable<String> ciphers;
    private CipherSuiteFilter cipherFilter = IdentityCipherSuiteFilter.INSTANCE;
    private ApplicationProtocolConfig apn;
    private long sessionCacheSize;
    private long sessionTimeout;

    private SslContextBuilder(boolean forServer) {
        this.forServer = forServer;
    }

    /**
     * The {@link SslContext} implementation to use. {@code null} uses the default one.
     */
    public SslContextBuilder sslProvider(SslProvider provider) {
        this.provider = provider;
        return this;
    }

    /**
     * Trusted certificates for verifying the remote endpoint's certificate. The file should
     * contain an X.509 certificate chain in PEM format. {@code null} uses the system default.
     */
    public SslContextBuilder trustManager(File trustCertChainFile) {
        this.trustCertChainFile = trustCertChainFile;
        this.trustManagerFactory = null;
        return this;
    }

    /**
     * Trusted manager for verifying the remote endpoint's certificate. Using a {@link
     * TrustManagerFactory} is only supported for {@link SslProvider#JDK}; for other providers,
     * you must use {@link #trustManager(File)}. {@code null} uses the system default.
     */
    public SslContextBuilder trustManager(TrustManagerFactory trustManagerFactory) {
        this.trustCertChainFile = null;
        this.trustManagerFactory = trustManagerFactory;
        return this;
    }

    /**
     * Identifying certificate for this host. {@code keyCertChainFile} and {@code keyFile} may
     * be {@code null} for client contexts, which disables mutual authentication.
     *
     * @param keyCertChainFile an X.509 certificate chain file in PEM format
     * @param keyFile a PKCS#8 private key file in PEM format
     */
    public SslContextBuilder keyManager(File keyCertChainFile, File keyFile) {
        return keyManager(keyCertChainFile, keyFile, null);
    }

    /**
     * Identifying certificate for this host. {@code keyCertChainFile} and {@code keyFile} may
     * be {@code null} for client contexts, which disables mutual authentication.
     *
     * @param keyCertChainFile an X.509 certificate chain file in PEM format
     * @param keyFile a PKCS#8 private key file in PEM format
     * @param keyPassword the password of the {@code keyFile}, or {@code null} if it's not
     *     password-protected
     */
    public SslContextBuilder keyManager(File keyCertChainFile, File keyFile, String keyPassword) {
        if (forServer) {
            checkNotNull(keyCertChainFile, "keyCertChainFile required for servers");
            checkNotNull(keyFile, "keyFile required for servers");
        }
        this.keyCertChainFile = keyCertChainFile;
        this.keyFile = keyFile;
        this.keyPassword = keyPassword;
        this.keyManagerFactory = null;
        return this;
    }

    /**
     * Identifying manager for this host. {@code keyManagerFactory} may be {@code null} for
     * client contexts, which disables mutual authentication. Using a {@code KeyManagerFactory}
     * is only supported for {@link SslProvider#JDK}; for other providers, you must use {@link
     * #keyManager(File, File)} or {@link #keyManager(File, File, String)}.
     */
    public SslContextBuilder keyManager(KeyManagerFactory keyManagerFactory) {
        if (forServer) {
            checkNotNull(keyManagerFactory, "keyManagerFactory required for servers");
        }
        this.keyCertChainFile = null;
        this.keyFile = null;
        this.keyPassword = null;
        this.keyManagerFactory = keyManagerFactory;
        return this;
    }

    /**
     * The cipher suites to enable, in the order of preference. {@code null} to use default
     * cipher suites.
     */
    public SslContextBuilder ciphers(Iterable<String> ciphers) {
        return ciphers(ciphers, IdentityCipherSuiteFilter.INSTANCE);
    }

    /**
     * The cipher suites to enable, in the order of preference. {@code cipherFilter} will be
     * applied to the ciphers before use if provider is {@link SslProvider#JDK}. If {@code
     * ciphers} is {@code null}, then the default cipher suites will be used.
     */
    public SslContextBuilder ciphers(Iterable<String> ciphers, CipherSuiteFilter cipherFilter) {
        checkNotNull(cipherFilter, "cipherFilter");
        this.ciphers = ciphers;
        this.cipherFilter = cipherFilter;
        return this;
    }

    /**
     * Application protocol negotiation configuration. {@code null} disables support.
     */
    public SslContextBuilder applicationProtocolConfig(ApplicationProtocolConfig apn) {
        this.apn = apn;
        return this;
    }

    /**
     * Set the size of the cache used for storing SSL session objects. {@code 0} to use the
     * default value.
     */
    public SslContextBuilder sessionCacheSize(long sessionCacheSize) {
        this.sessionCacheSize = sessionCacheSize;
        return this;
    }

    /**
     * Set the timeout for the cached SSL session objects, in seconds. {@code 0} to use the
     * default value.
     */
    public SslContextBuilder sessionTimeout(long sessionTimeout) {
        this.sessionTimeout = sessionTimeout;
        return this;
    }

    /**
     * Create new {@code SslContext} instance with configured settings.
     */
    public SslContext build() throws SSLException {
        if (forServer) {
            return SslContext.newServerContextInternal(provider, trustCertChainFile,
                trustManagerFactory, keyCertChainFile, keyFile, keyPassword, keyManagerFactory,
                ciphers, cipherFilter, apn, sessionCacheSize, sessionTimeout);
        } else {
            return SslContext.newClientContextInternal(provider, trustCertChainFile,
                trustManagerFactory, keyCertChainFile, keyFile, keyPassword, keyManagerFactory,
                ciphers, cipherFilter, apn, sessionCacheSize, sessionTimeout);
        }
    }
}
