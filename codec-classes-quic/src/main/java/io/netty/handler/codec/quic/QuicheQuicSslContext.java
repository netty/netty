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

import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.ssl.ApplicationProtocolNegotiator;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContextOption;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.AbstractReferenceCounted;
import io.netty.util.Mapping;
import io.netty.util.ReferenceCounted;
import io.netty.util.internal.EmptyArrays;
import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.jetbrains.annotations.Nullable;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509TrustManager;
import java.io.File;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.LongFunction;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

final class QuicheQuicSslContext extends QuicSslContext {

    private static final InternalLogger LOGGER = InternalLoggerFactory.getInstance(QuicheQuicSslContext.class);

    // Use default that is supported in java 11 and earlier and also in OpenSSL / BoringSSL.
    // See https://github.com/netty/netty-tcnative/issues/567
    // See https://www.java.com/en/configure_crypto.html for ordering
    private static final String[] DEFAULT_NAMED_GROUPS = { "x25519", "secp256r1", "secp384r1", "secp521r1" };
    private static final String[] NAMED_GROUPS;

    static {
        String[] namedGroups = DEFAULT_NAMED_GROUPS;
        Set<String> defaultConvertedNamedGroups = new LinkedHashSet<>(namedGroups.length);
        for (int i = 0; i < namedGroups.length; i++) {
            defaultConvertedNamedGroups.add(GroupsConverter.toBoringSSL(namedGroups[i]));
        }

        // Call Quic.isAvailable() first to ensure native lib is loaded.
        // See https://github.com/netty/netty-incubator-codec-quic/issues/759
        if (Quic.isAvailable()) {
            final long sslCtx = BoringSSL.SSLContext_new();
            try {
                // Let's filter out any group that is not supported from the default.
                Iterator<String> defaultGroupsIter = defaultConvertedNamedGroups.iterator();
                while (defaultGroupsIter.hasNext()) {
                    if (BoringSSL.SSLContext_set1_groups_list(sslCtx, defaultGroupsIter.next()) == 0) {
                        // Not supported, let's remove it. This could for example be the case if we use
                        // fips and the configure group is not supported when using FIPS.
                        // See https://github.com/netty/netty-tcnative/issues/883
                        defaultGroupsIter.remove();
                    }
                }

                String groups = SystemPropertyUtil.get("jdk.tls.namedGroups", null);
                if (groups != null) {
                    String[] nGroups = groups.split(",");
                    Set<String> supportedNamedGroups = new LinkedHashSet<>(nGroups.length);
                    Set<String> supportedConvertedNamedGroups = new LinkedHashSet<>(nGroups.length);

                    Set<String> unsupportedNamedGroups = new LinkedHashSet<>();
                    for (String namedGroup : nGroups) {
                        String converted = GroupsConverter.toBoringSSL(namedGroup);
                        // Will return 0 on failure.
                        if (BoringSSL.SSLContext_set1_groups_list(sslCtx, converted) == 0) {
                            unsupportedNamedGroups.add(namedGroup);
                        } else {
                            supportedConvertedNamedGroups.add(converted);
                            supportedNamedGroups.add(namedGroup);
                        }
                    }

                    if (supportedNamedGroups.isEmpty()) {
                        namedGroups = defaultConvertedNamedGroups.toArray(EmptyArrays.EMPTY_STRINGS);
                        LOGGER.info("All configured namedGroups are not supported: {}. Use default: {}.",
                                Arrays.toString(unsupportedNamedGroups.toArray(EmptyArrays.EMPTY_STRINGS)),
                                Arrays.toString(DEFAULT_NAMED_GROUPS));
                    } else {
                        String[] groupArray = supportedNamedGroups.toArray(EmptyArrays.EMPTY_STRINGS);
                        if (unsupportedNamedGroups.isEmpty()) {
                            LOGGER.info("Using configured namedGroups -D 'jdk.tls.namedGroup': {} ",
                                    Arrays.toString(groupArray));
                        } else {
                            LOGGER.info("Using supported configured namedGroups: {}. Unsupported namedGroups: {}. ",
                                    Arrays.toString(groupArray),
                                    Arrays.toString(unsupportedNamedGroups.toArray(EmptyArrays.EMPTY_STRINGS)));
                        }
                        namedGroups = supportedConvertedNamedGroups.toArray(EmptyArrays.EMPTY_STRINGS);
                    }
                } else {
                    namedGroups = defaultConvertedNamedGroups.toArray(EmptyArrays.EMPTY_STRINGS);
                }
            } finally {
                BoringSSL.SSLContext_free(sslCtx);
            }
        }
        NAMED_GROUPS = namedGroups;
    }

    final ClientAuth clientAuth;
    private final boolean server;
    @SuppressWarnings("deprecation")
    private final ApplicationProtocolNegotiator apn;
    private long sessionCacheSize;
    private long sessionTimeout;
    private final QuicheQuicSslSessionContext sessionCtx;
    private final QuicheQuicSslEngineMap engineMap = new QuicheQuicSslEngineMap();
    private final QuicClientSessionCache sessionCache;

    private final BoringSSLSessionTicketCallback sessionTicketCallback = new BoringSSLSessionTicketCallback();

    final NativeSslContext nativeSslContext;

    QuicheQuicSslContext(boolean server, long sessionTimeout, long sessionCacheSize,
                         ClientAuth clientAuth, @Nullable TrustManagerFactory trustManagerFactory,
                         @Nullable KeyManagerFactory keyManagerFactory, String password,
                         @Nullable Mapping<? super String, ? extends QuicSslContext> mapping,
                         @Nullable Boolean earlyData, @Nullable BoringSSLKeylog keylog,
                         String[] applicationProtocols, Map.Entry<SslContextOption<?>, Object>... ctxOptions) {
        Quic.ensureAvailability();
        this.server = server;
        this.clientAuth = server ? checkNotNull(clientAuth, "clientAuth") : ClientAuth.NONE;
        final X509TrustManager trustManager;
        if (trustManagerFactory == null) {
            try {
                trustManagerFactory =
                        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                trustManagerFactory.init((KeyStore) null);
                trustManager = chooseTrustManager(trustManagerFactory);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        } else {
            trustManager = chooseTrustManager(trustManagerFactory);
        }
        final X509ExtendedKeyManager keyManager;
        if (keyManagerFactory == null) {
            if (server) {
                throw new IllegalArgumentException("No KeyManagerFactory");
            }
            keyManager = null;
        } else {
            keyManager = chooseKeyManager(keyManagerFactory);
        }
        String[] groups = NAMED_GROUPS;
        String[] sigalgs = EmptyArrays.EMPTY_STRINGS;
        Map<String, String> serverKeyTypes = null;
        Set<String> clientKeyTypes = null;

        if (ctxOptions != null) {
            for (Map.Entry<SslContextOption<?>, Object> ctxOpt : ctxOptions) {
                SslContextOption<?> option = ctxOpt.getKey();

                if (option == BoringSSLContextOption.GROUPS) {
                    String[] groupsArray = (String[]) ctxOpt.getValue();
                    Set<String> groupsSet = new LinkedHashSet<String>(groupsArray.length);
                    for (String group : groupsArray) {
                        groupsSet.add(GroupsConverter.toBoringSSL(group));
                    }
                    groups = groupsSet.toArray(EmptyArrays.EMPTY_STRINGS);
                } else if (option == BoringSSLContextOption.SIGNATURE_ALGORITHMS) {
                    String[] sigalgsArray = (String[]) ctxOpt.getValue();
                    Set<String> sigalgsSet = new LinkedHashSet<String>(sigalgsArray.length);
                    for (String sigalg : sigalgsArray) {
                        sigalgsSet.add(sigalg);
                    }
                    sigalgs = sigalgsSet.toArray(EmptyArrays.EMPTY_STRINGS);
                } else if (option == BoringSSLContextOption.CLIENT_KEY_TYPES) {
                    clientKeyTypes = (Set<String>) ctxOpt.getValue();
                } else if (option == BoringSSLContextOption.SERVER_KEY_TYPES) {
                    serverKeyTypes = (Map<String, String>) ctxOpt.getValue();
                } else {
                    LOGGER.debug("Skipping unsupported " + SslContextOption.class.getSimpleName()
                            + ": " + ctxOpt.getKey());
                }
            }
        }
        final BoringSSLPrivateKeyMethod privateKeyMethod;
        if (keyManagerFactory instanceof  BoringSSLKeylessManagerFactory) {
            privateKeyMethod = new BoringSSLAsyncPrivateKeyMethodAdapter(engineMap,
                    ((BoringSSLKeylessManagerFactory) keyManagerFactory).privateKeyMethod);
        } else {
            privateKeyMethod = null;
        }
        sessionCache = server ? null : new QuicClientSessionCache();
        int verifyMode = server ? boringSSLVerifyModeForServer(this.clientAuth) : BoringSSL.SSL_VERIFY_PEER;
        nativeSslContext = new NativeSslContext(BoringSSL.SSLContext_new(server, applicationProtocols,
                new BoringSSLHandshakeCompleteCallback(engineMap),
                new BoringSSLCertificateCallback(engineMap, keyManager, password, serverKeyTypes, clientKeyTypes),
                new BoringSSLCertificateVerifyCallback(engineMap, trustManager),
                mapping == null ? null : new BoringSSLTlsextServernameCallback(engineMap, mapping),
                keylog == null ? null : new BoringSSLKeylogCallback(engineMap, keylog),
                server ? null : new BoringSSLSessionCallback(engineMap, sessionCache), privateKeyMethod,
                sessionTicketCallback, verifyMode,
                BoringSSL.subjectNames(trustManager.getAcceptedIssuers())));
        boolean success = false;
        try {
            if (groups.length > 0 && BoringSSL.SSLContext_set1_groups_list(nativeSslContext.ctx, groups) == 0) {
                String msg = "failed to set curves / groups list: " + Arrays.toString(groups);
                String lastError = BoringSSL.ERR_last_error();
                if (lastError != null) {
                    // We have some more details about why the operations failed, include these into the message.
                    msg += ". " + lastError;
                }
                throw new IllegalStateException(msg);
            }

            if (sigalgs.length > 0 && BoringSSL.SSLContext_set1_sigalgs_list(nativeSslContext.ctx, sigalgs) == 0) {
                String msg = "failed to set signature algorithm list: " + Arrays.toString(sigalgs);
                String lastError = BoringSSL.ERR_last_error();
                if (lastError != null) {
                    // We have some more details about why the operations failed, include these into the message.
                    msg += ". " + lastError;
                }
                throw new IllegalStateException(msg);
            }

            apn = new QuicheQuicApplicationProtocolNegotiator(applicationProtocols);
            if (this.sessionCache != null) {
                // Cache is handled via our own implementation.
                this.sessionCache.setSessionCacheSize((int) sessionCacheSize);
                this.sessionCache.setSessionTimeout((int) sessionTimeout);
            } else {
                // Cache is handled by BoringSSL internally
                BoringSSL.SSLContext_setSessionCacheSize(
                        nativeSslContext.address(), sessionCacheSize);
                this.sessionCacheSize = sessionCacheSize;

                BoringSSL.SSLContext_setSessionCacheTimeout(
                        nativeSslContext.address(), sessionTimeout);
                this.sessionTimeout = sessionTimeout;
            }
            if (earlyData != null) {
                BoringSSL.SSLContext_set_early_data_enabled(nativeSslContext.address(), earlyData);
            }
            sessionCtx = new QuicheQuicSslSessionContext(this);
            success = true;
        } finally {
            if (!success) {
                nativeSslContext.release();
            }
        }
    }

    private X509ExtendedKeyManager chooseKeyManager(KeyManagerFactory keyManagerFactory) {
        for (KeyManager manager: keyManagerFactory.getKeyManagers()) {
            if (manager instanceof X509ExtendedKeyManager) {
                return (X509ExtendedKeyManager) manager;
            }
        }
        throw new IllegalArgumentException("No X509ExtendedKeyManager included");
    }

    private static X509TrustManager chooseTrustManager(TrustManagerFactory trustManagerFactory) {
        for (TrustManager manager: trustManagerFactory.getTrustManagers()) {
            if (manager instanceof X509TrustManager) {
                return (X509TrustManager) manager;
            }
        }
        throw new IllegalArgumentException("No X509TrustManager included");
    }

     static X509Certificate @Nullable [] toX509Certificates0(@Nullable File file) throws CertificateException {
        return toX509Certificates(file);
    }

    static PrivateKey toPrivateKey0(@Nullable File keyFile, @Nullable String keyPassword) throws Exception {
        return toPrivateKey(keyFile, keyPassword);
    }

    static TrustManagerFactory buildTrustManagerFactory0(
            X509Certificate @Nullable [] certCollection)
            throws NoSuchAlgorithmException, CertificateException, KeyStoreException, IOException {
        return buildTrustManagerFactory(certCollection, null, null);
    }

    private static int boringSSLVerifyModeForServer(ClientAuth mode) {
        switch (mode) {
            case NONE:
                return BoringSSL.SSL_VERIFY_NONE;
            case REQUIRE:
                return BoringSSL.SSL_VERIFY_PEER | BoringSSL.SSL_VERIFY_FAIL_IF_NO_PEER_CERT;
            case OPTIONAL:
                return BoringSSL.SSL_VERIFY_PEER;
            default:
                throw new Error(mode.toString());
        }
    }

    @Nullable
    QuicheQuicConnection createConnection(LongFunction<Long> connectionCreator, QuicheQuicSslEngine engine) {
        nativeSslContext.retain();
        long ssl = BoringSSL.SSL_new(nativeSslContext.address(), isServer(), engine.tlsHostName);
        engineMap.put(ssl, engine);
        long connection = connectionCreator.apply(ssl);
        if (connection == -1) {
            engineMap.remove(ssl);
            // We retained before but as we don't create a QuicheQuicConnection and transfer ownership we need to
            // explict call release again here.
            nativeSslContext.release();
            return null;
        }
        // The connection will call nativeSslContext.release() once it is freed.
        return new QuicheQuicConnection(connection, ssl, engine, nativeSslContext);
    }

    /**
     * Add the given engine to this context
     *
     * @param engine    the engine to add.
     * @return          the pointer address of this context.
     */
    long add(QuicheQuicSslEngine engine) {
        nativeSslContext.retain();
        engine.connection.reattach(nativeSslContext);
        engineMap.put(engine.connection.ssl, engine);
        return nativeSslContext.address();
    }

    /**
     * Remove the given engine from this context.
     *
     * @param engine    the engine to remove.
     */
    void remove(QuicheQuicSslEngine engine) {
        QuicheQuicSslEngine removed = engineMap.remove(engine.connection.ssl);
        assert removed == null || removed == engine;
        engine.removeSessionFromCacheIfInvalid();
    }

    @Nullable
    QuicClientSessionCache getSessionCache() {
        return sessionCache;
    }

    @Override
    public boolean isClient() {
        return !server;
    }

    @Override
    public List<String> cipherSuites() {
        return Arrays.asList("TLS_AES_128_GCM_SHA256", "TLS_AES_256_GCM_SHA384");
    }

    @Override
    public long sessionCacheSize() {
        if (sessionCache != null) {
            return sessionCache.getSessionCacheSize();
        } else {
            synchronized (this) {
                return sessionCacheSize;
            }
        }
    }

    @Override
    public long sessionTimeout() {
        if (sessionCache != null) {
            return sessionCache.getSessionTimeout();
        } else {
            synchronized (this) {
                return sessionTimeout;
            }
        }
    }

    @Override
    public ApplicationProtocolNegotiator applicationProtocolNegotiator() {
        return apn;
    }

    @Override
    public QuicSslEngine newEngine(ByteBufAllocator alloc) {
        return new QuicheQuicSslEngine(this, null, -1);
    }

    @Override
    public QuicSslEngine newEngine(ByteBufAllocator alloc, String peerHost, int peerPort) {
        return new QuicheQuicSslEngine(this, peerHost, peerPort);
    }

    @Override
    public QuicSslSessionContext sessionContext() {
        return sessionCtx;
    }

    @Override
    protected SslHandler newHandler(ByteBufAllocator alloc, boolean startTls) {
        throw new UnsupportedOperationException();
    }

    @Override
    public SslHandler newHandler(ByteBufAllocator alloc, Executor delegatedTaskExecutor) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected SslHandler newHandler(ByteBufAllocator alloc, boolean startTls, Executor executor) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected SslHandler newHandler(ByteBufAllocator alloc, String peerHost, int peerPort, boolean startTls) {
        throw new UnsupportedOperationException();
    }

    @Override
    public SslHandler newHandler(ByteBufAllocator alloc, String peerHost, int peerPort,
                                 Executor delegatedTaskExecutor) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected SslHandler newHandler(ByteBufAllocator alloc, String peerHost, int peerPort,
                                    boolean startTls, Executor delegatedTaskExecutor) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            nativeSslContext.release();
        } finally {
            super.finalize();
        }
    }

    void setSessionTimeout(int seconds) throws IllegalArgumentException {
        if (sessionCache != null) {
            sessionCache.setSessionTimeout(seconds);
        } else {
            BoringSSL.SSLContext_setSessionCacheTimeout(nativeSslContext.address(), seconds);
            this.sessionTimeout = seconds;
        }
    }

    void setSessionCacheSize(int size) throws IllegalArgumentException {
        if (sessionCache != null) {
            sessionCache.setSessionCacheSize(size);
        } else {
            BoringSSL.SSLContext_setSessionCacheSize(nativeSslContext.address(), size);
            sessionCacheSize = size;
        }
    }

    void setSessionTicketKeys(SslSessionTicketKey @Nullable [] ticketKeys) {
        sessionTicketCallback.setSessionTicketKeys(ticketKeys);
        BoringSSL.SSLContext_setSessionTicketKeys(
                nativeSslContext.address(), ticketKeys != null && ticketKeys.length != 0);
    }

    @SuppressWarnings("deprecation")
    private static final class QuicheQuicApplicationProtocolNegotiator implements ApplicationProtocolNegotiator {
        private final List<String> protocols;

        QuicheQuicApplicationProtocolNegotiator(String @Nullable ... protocols) {
            if (protocols == null) {
                this.protocols = Collections.emptyList();
            } else {
                this.protocols = Collections.unmodifiableList(Arrays.asList(protocols));
            }
        }

        @Override
        public List<String> protocols() {
            return protocols;
        }
    }

    private static final class QuicheQuicSslSessionContext implements QuicSslSessionContext {
        private final QuicheQuicSslContext context;

        QuicheQuicSslSessionContext(QuicheQuicSslContext context) {
            this.context = context;
        }

        @Override
        @Nullable
        public SSLSession getSession(byte[] sessionId) {
            return null;
        }

        @Override
        public Enumeration<byte[]> getIds() {
            return new Enumeration<byte[]>() {
                @Override
                public boolean hasMoreElements() {
                    return false;
                }

                @Override
                public byte[] nextElement() {
                    throw new NoSuchElementException();
                }
            };
        }

        @Override
        public void setSessionTimeout(int seconds) throws IllegalArgumentException {
            context.setSessionTimeout(seconds);
        }

        @Override
        public int getSessionTimeout() {
            return (int) context.sessionTimeout();
        }

        @Override
        public void setSessionCacheSize(int size) throws IllegalArgumentException {
            context.setSessionCacheSize(size);
        }

        @Override
        public int getSessionCacheSize() {
            return (int) context.sessionCacheSize();
        }

        @Override
        public void setTicketKeys(SslSessionTicketKey @Nullable ... keys) {
            context.setSessionTicketKeys(keys);
        }
    }

    static final class NativeSslContext extends AbstractReferenceCounted {
        private final long ctx;

        NativeSslContext(long ctx) {
            this.ctx = ctx;
        }

        long address() {
            return ctx;
        }

        @Override
        protected void deallocate() {
            BoringSSL.SSLContext_free(ctx);
        }

        @Override
        public ReferenceCounted touch(Object hint) {
            return this;
        }

        @Override
        public String toString() {
            return "NativeSslContext{" +
                    "ctx=" + ctx +
                    '}';
        }
    }

    private static final class BoringSSLAsyncPrivateKeyMethodAdapter implements BoringSSLPrivateKeyMethod {
        private final QuicheQuicSslEngineMap engineMap;
        private final BoringSSLAsyncPrivateKeyMethod privateKeyMethod;

        BoringSSLAsyncPrivateKeyMethodAdapter(QuicheQuicSslEngineMap engineMap,
                                              BoringSSLAsyncPrivateKeyMethod privateKeyMethod) {
            this.engineMap = engineMap;
            this.privateKeyMethod = privateKeyMethod;
        }

        @Override
        public void sign(long ssl, int signatureAlgorithm, byte[] input, BiConsumer<byte[], Throwable> callback) {
            final QuicheQuicSslEngine engine = engineMap.get(ssl);
            if (engine == null) {
                // May be null if it was destroyed in the meantime.
                callback.accept(null, null);
            } else {
                privateKeyMethod.sign(engine, signatureAlgorithm, input).addListener(f -> {
                    Throwable cause = f.cause();
                    if (cause != null) {
                        callback.accept(null, cause);
                    } else {
                        callback.accept((byte[]) f.getNow(), null);
                    }
                });
            }
        }

        @Override
        public void decrypt(long ssl, byte[] input, BiConsumer<byte[], Throwable> callback) {
            final QuicheQuicSslEngine engine = engineMap.get(ssl);
            if (engine == null) {
                // May be null if it was destroyed in the meantime.
                callback.accept(null, null);
            } else {
                privateKeyMethod.decrypt(engine, input).addListener(f -> {
                    Throwable cause = f.cause();
                    if (cause != null) {
                        callback.accept(null, cause);
                    } else {
                        callback.accept((byte[]) f.getNow(), null);
                    }
                });
            }
        }
    }
}
