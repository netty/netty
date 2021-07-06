/*
 * Copyright 2016 The Netty Project
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
package io.netty.handler.ssl;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.ssl.util.LazyX509Certificate;
import io.netty.internal.tcnative.AsyncSSLPrivateKeyMethod;
import io.netty.internal.tcnative.CertificateVerifier;
import io.netty.internal.tcnative.ResultCallback;
import io.netty.internal.tcnative.SSL;
import io.netty.internal.tcnative.SSLContext;
import io.netty.internal.tcnative.SSLPrivateKeyMethod;
import io.netty.util.AbstractReferenceCounted;
import io.netty.util.ReferenceCounted;
import io.netty.util.ResourceLeakDetector;
import io.netty.util.ResourceLeakDetectorFactory;
import io.netty.util.ResourceLeakTracker;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.SuppressJava6Requirement;
import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.UnstableApi;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.security.PrivateKey;
import java.security.SignatureException;
import java.security.cert.CertPathValidatorException;
import java.security.cert.Certificate;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.CertificateRevokedException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;

import static io.netty.handler.ssl.OpenSsl.DEFAULT_CIPHERS;
import static io.netty.handler.ssl.OpenSsl.availableJavaCipherSuites;
import static io.netty.handler.ssl.OpenSsl.ensureAvailability;
import static io.netty.util.internal.ObjectUtil.checkNotNull;
import static io.netty.util.internal.ObjectUtil.checkNonEmpty;
import static io.netty.util.internal.ObjectUtil.checkPositiveOrZero;

/**
 * An implementation of {@link SslContext} which works with libraries that support the
 * <a href="https://www.openssl.org/">OpenSsl</a> C library API.
 * <p>Instances of this class must be {@link #release() released} or else native memory will leak!
 *
 * <p>Instances of this class <strong>must not</strong> be released before any {@link ReferenceCountedOpenSslEngine}
 * which depends upon the instance of this class is released. Otherwise if any method of
 * {@link ReferenceCountedOpenSslEngine} is called which uses this class's JNI resources the JVM may crash.
 */
public abstract class ReferenceCountedOpenSslContext extends SslContext implements ReferenceCounted {
    private static final InternalLogger logger =
            InternalLoggerFactory.getInstance(ReferenceCountedOpenSslContext.class);

    private static final int DEFAULT_BIO_NON_APPLICATION_BUFFER_SIZE = Math.max(1,
            SystemPropertyUtil.getInt("io.netty.handler.ssl.openssl.bioNonApplicationBufferSize",
                    2048));
    // Let's use tasks by default but still allow the user to disable it via system property just in case.
    static final boolean USE_TASKS =
            SystemPropertyUtil.getBoolean("io.netty.handler.ssl.openssl.useTasks", true);
    private static final Integer DH_KEY_LENGTH;
    private static final ResourceLeakDetector<ReferenceCountedOpenSslContext> leakDetector =
            ResourceLeakDetectorFactory.instance().newResourceLeakDetector(ReferenceCountedOpenSslContext.class);

    // TODO: Maybe make configurable ?
    protected static final int VERIFY_DEPTH = 10;

    static final boolean CLIENT_ENABLE_SESSION_TICKET =
            SystemPropertyUtil.getBoolean("jdk.tls.client.enableSessionTicketExtension", false);

    static final boolean CLIENT_ENABLE_SESSION_TICKET_TLSV13 =
            SystemPropertyUtil.getBoolean("jdk.tls.client.enableSessionTicketExtension", true);

    static final boolean SERVER_ENABLE_SESSION_TICKET =
            SystemPropertyUtil.getBoolean("jdk.tls.server.enableSessionTicketExtension", false);

     static final boolean SERVER_ENABLE_SESSION_TICKET_TLSV13 =
            SystemPropertyUtil.getBoolean("jdk.tls.server.enableSessionTicketExtension", true);

    static final boolean SERVER_ENABLE_SESSION_CACHE =
            SystemPropertyUtil.getBoolean("io.netty.handler.ssl.openssl.sessionCacheServer", true);
    // session caching is disabled by default on the client side due a JDK bug:
    // https://mail.openjdk.java.net/pipermail/security-dev/2021-March/024758.html
    static final boolean CLIENT_ENABLE_SESSION_CACHE =
            SystemPropertyUtil.getBoolean("io.netty.handler.ssl.openssl.sessionCacheClient", false);
    /**
     * The OpenSSL SSL_CTX object.
     *
     * <strong>{@link #ctxLock} must be hold while using ctx!</strong>
     */
    protected long ctx;
    private final List<String> unmodifiableCiphers;
    private final OpenSslApplicationProtocolNegotiator apn;
    private final int mode;

    // Reference Counting
    private final ResourceLeakTracker<ReferenceCountedOpenSslContext> leak;
    private final AbstractReferenceCounted refCnt = new AbstractReferenceCounted() {
        @Override
        public ReferenceCounted touch(Object hint) {
            if (leak != null) {
                leak.record(hint);
            }

            return ReferenceCountedOpenSslContext.this;
        }

        @Override
        protected void deallocate() {
            destroy();
            if (leak != null) {
                boolean closed = leak.close(ReferenceCountedOpenSslContext.this);
                assert closed;
            }
        }
    };

    final Certificate[] keyCertChain;
    final ClientAuth clientAuth;
    final String[] protocols;
    final boolean enableOcsp;
    final OpenSslEngineMap engineMap = new DefaultOpenSslEngineMap();
    final ReadWriteLock ctxLock = new ReentrantReadWriteLock();

    private volatile int bioNonApplicationBufferSize = DEFAULT_BIO_NON_APPLICATION_BUFFER_SIZE;

    @SuppressWarnings("deprecation")
    static final OpenSslApplicationProtocolNegotiator NONE_PROTOCOL_NEGOTIATOR =
            new OpenSslApplicationProtocolNegotiator() {
                @Override
                public ApplicationProtocolConfig.Protocol protocol() {
                    return ApplicationProtocolConfig.Protocol.NONE;
                }

                @Override
                public List<String> protocols() {
                    return Collections.emptyList();
                }

                @Override
                public ApplicationProtocolConfig.SelectorFailureBehavior selectorFailureBehavior() {
                    return ApplicationProtocolConfig.SelectorFailureBehavior.CHOOSE_MY_LAST_PROTOCOL;
                }

                @Override
                public ApplicationProtocolConfig.SelectedListenerFailureBehavior selectedListenerFailureBehavior() {
                    return ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT;
                }
            };

    static {
        Integer dhLen = null;

        try {
            String dhKeySize = SystemPropertyUtil.get("jdk.tls.ephemeralDHKeySize");
            if (dhKeySize != null) {
                try {
                    dhLen = Integer.valueOf(dhKeySize);
                } catch (NumberFormatException e) {
                    logger.debug("ReferenceCountedOpenSslContext supports -Djdk.tls.ephemeralDHKeySize={int}, but got: "
                            + dhKeySize);
                }
            }
        } catch (Throwable ignore) {
            // ignore
        }
        DH_KEY_LENGTH = dhLen;
    }

    final boolean tlsFalseStart;

    ReferenceCountedOpenSslContext(Iterable<String> ciphers, CipherSuiteFilter cipherFilter,
                                   OpenSslApplicationProtocolNegotiator apn, int mode, Certificate[] keyCertChain,
                                   ClientAuth clientAuth, String[] protocols, boolean startTls, boolean enableOcsp,
                                   boolean leakDetection, Map.Entry<SslContextOption<?>, Object>... ctxOptions)
            throws SSLException {
        super(startTls);

        OpenSsl.ensureAvailability();

        if (enableOcsp && !OpenSsl.isOcspSupported()) {
            throw new IllegalStateException("OCSP is not supported.");
        }

        if (mode != SSL.SSL_MODE_SERVER && mode != SSL.SSL_MODE_CLIENT) {
            throw new IllegalArgumentException("mode most be either SSL.SSL_MODE_SERVER or SSL.SSL_MODE_CLIENT");
        }

        boolean tlsFalseStart = false;
        boolean useTasks = USE_TASKS;
        OpenSslPrivateKeyMethod privateKeyMethod = null;
        OpenSslAsyncPrivateKeyMethod asyncPrivateKeyMethod = null;

        if (ctxOptions != null) {
            for (Map.Entry<SslContextOption<?>, Object> ctxOpt : ctxOptions) {
                SslContextOption<?> option = ctxOpt.getKey();

                if (option == OpenSslContextOption.TLS_FALSE_START) {
                    tlsFalseStart = (Boolean) ctxOpt.getValue();
                } else if (option == OpenSslContextOption.USE_TASKS) {
                    useTasks = (Boolean) ctxOpt.getValue();
                } else if (option == OpenSslContextOption.PRIVATE_KEY_METHOD) {
                    privateKeyMethod = (OpenSslPrivateKeyMethod) ctxOpt.getValue();
                } else if (option == OpenSslContextOption.ASYNC_PRIVATE_KEY_METHOD) {
                    asyncPrivateKeyMethod = (OpenSslAsyncPrivateKeyMethod) ctxOpt.getValue();
                } else {
                    logger.debug("Skipping unsupported " + SslContextOption.class.getSimpleName()
                            + ": " + ctxOpt.getKey());
                }
            }
        }
        if (privateKeyMethod != null && asyncPrivateKeyMethod != null) {
            throw new IllegalArgumentException("You can either only use "
                    + OpenSslAsyncPrivateKeyMethod.class.getSimpleName() + " or "
                    + OpenSslPrivateKeyMethod.class.getSimpleName());
        }

        this.tlsFalseStart = tlsFalseStart;

        leak = leakDetection ? leakDetector.track(this) : null;
        this.mode = mode;
        this.clientAuth = isServer() ? checkNotNull(clientAuth, "clientAuth") : ClientAuth.NONE;
        this.protocols = protocols;
        this.enableOcsp = enableOcsp;

        this.keyCertChain = keyCertChain == null ? null : keyCertChain.clone();

        unmodifiableCiphers = Arrays.asList(checkNotNull(cipherFilter, "cipherFilter").filterCipherSuites(
                ciphers, DEFAULT_CIPHERS, availableJavaCipherSuites()));

        this.apn = checkNotNull(apn, "apn");

        // Create a new SSL_CTX and configure it.
        boolean success = false;
        try {
            boolean tlsv13Supported = OpenSsl.isTlsv13Supported();

            try {
                int protocolOpts = SSL.SSL_PROTOCOL_SSLV3 | SSL.SSL_PROTOCOL_TLSV1 |
                                   SSL.SSL_PROTOCOL_TLSV1_1 | SSL.SSL_PROTOCOL_TLSV1_2;
                if (tlsv13Supported) {
                    protocolOpts |= SSL.SSL_PROTOCOL_TLSV1_3;
                }
                ctx = SSLContext.make(protocolOpts, mode);
            } catch (Exception e) {
                throw new SSLException("failed to create an SSL_CTX", e);
            }

            StringBuilder cipherBuilder = new StringBuilder();
            StringBuilder cipherTLSv13Builder = new StringBuilder();

            /* List the ciphers that are permitted to negotiate. */
            try {
                if (unmodifiableCiphers.isEmpty()) {
                    // Set non TLSv1.3 ciphers.
                    SSLContext.setCipherSuite(ctx, StringUtil.EMPTY_STRING, false);
                    if (tlsv13Supported) {
                        // Set TLSv1.3 ciphers.
                        SSLContext.setCipherSuite(ctx, StringUtil.EMPTY_STRING, true);
                    }
                } else {
                    CipherSuiteConverter.convertToCipherStrings(
                            unmodifiableCiphers, cipherBuilder, cipherTLSv13Builder, OpenSsl.isBoringSSL());

                    // Set non TLSv1.3 ciphers.
                    SSLContext.setCipherSuite(ctx, cipherBuilder.toString(), false);
                    if (tlsv13Supported) {
                        // Set TLSv1.3 ciphers.
                        SSLContext.setCipherSuite(ctx,
                                OpenSsl.checkTls13Ciphers(logger, cipherTLSv13Builder.toString()), true);
                    }
                }
            } catch (SSLException e) {
                throw e;
            } catch (Exception e) {
                throw new SSLException("failed to set cipher suite: " + unmodifiableCiphers, e);
            }

            int options = SSLContext.getOptions(ctx) |
                          SSL.SSL_OP_NO_SSLv2 |
                          SSL.SSL_OP_NO_SSLv3 |
                          // Disable TLSv1 and TLSv1.1 by default as these are not considered secure anymore
                          // and the JDK is doing the same:
                          // https://www.oracle.com/java/technologies/javase/8u291-relnotes.html
                          SSL.SSL_OP_NO_TLSv1 |
                          SSL.SSL_OP_NO_TLSv1_1 |

                          SSL.SSL_OP_CIPHER_SERVER_PREFERENCE |

                          // We do not support compression at the moment so we should explicitly disable it.
                          SSL.SSL_OP_NO_COMPRESSION |

                          // Disable ticket support by default to be more inline with SSLEngineImpl of the JDK.
                          // This also let SSLSession.getId() work the same way for the JDK implementation and the
                          // OpenSSLEngine. If tickets are supported SSLSession.getId() will only return an ID on the
                          // server-side if it could make use of tickets.
                          SSL.SSL_OP_NO_TICKET;

            if (cipherBuilder.length() == 0) {
                // No ciphers that are compatible with SSLv2 / SSLv3 / TLSv1 / TLSv1.1 / TLSv1.2
                options |= SSL.SSL_OP_NO_SSLv2 | SSL.SSL_OP_NO_SSLv3 | SSL.SSL_OP_NO_TLSv1
                           | SSL.SSL_OP_NO_TLSv1_1 | SSL.SSL_OP_NO_TLSv1_2;
            }

            SSLContext.setOptions(ctx, options);

            // We need to enable SSL_MODE_ACCEPT_MOVING_WRITE_BUFFER as the memory address may change between
            // calling OpenSSLEngine.wrap(...).
            // See https://github.com/netty/netty-tcnative/issues/100
            SSLContext.setMode(ctx, SSLContext.getMode(ctx) | SSL.SSL_MODE_ACCEPT_MOVING_WRITE_BUFFER);

            if (DH_KEY_LENGTH != null) {
                SSLContext.setTmpDHLength(ctx, DH_KEY_LENGTH);
            }

            List<String> nextProtoList = apn.protocols();
                /* Set next protocols for next protocol negotiation extension, if specified */
            if (!nextProtoList.isEmpty()) {
                String[] appProtocols = nextProtoList.toArray(new String[0]);
                int selectorBehavior = opensslSelectorFailureBehavior(apn.selectorFailureBehavior());

                switch (apn.protocol()) {
                    case NPN:
                        SSLContext.setNpnProtos(ctx, appProtocols, selectorBehavior);
                        break;
                    case ALPN:
                        SSLContext.setAlpnProtos(ctx, appProtocols, selectorBehavior);
                        break;
                    case NPN_AND_ALPN:
                        SSLContext.setNpnProtos(ctx, appProtocols, selectorBehavior);
                        SSLContext.setAlpnProtos(ctx, appProtocols, selectorBehavior);
                        break;
                    default:
                        throw new Error();
                }
            }

            if (enableOcsp) {
                SSLContext.enableOcsp(ctx, isClient());
            }

            SSLContext.setUseTasks(ctx, useTasks);
            if (privateKeyMethod != null) {
                SSLContext.setPrivateKeyMethod(ctx, new PrivateKeyMethod(engineMap, privateKeyMethod));
            }
            if (asyncPrivateKeyMethod != null) {
                SSLContext.setPrivateKeyMethod(ctx, new AsyncPrivateKeyMethod(engineMap, asyncPrivateKeyMethod));
            }
            success = true;
        } finally {
            if (!success) {
                release();
            }
        }
    }

    private static int opensslSelectorFailureBehavior(ApplicationProtocolConfig.SelectorFailureBehavior behavior) {
        switch (behavior) {
            case NO_ADVERTISE:
                return SSL.SSL_SELECTOR_FAILURE_NO_ADVERTISE;
            case CHOOSE_MY_LAST_PROTOCOL:
                return SSL.SSL_SELECTOR_FAILURE_CHOOSE_MY_LAST_PROTOCOL;
            default:
                throw new Error();
        }
    }

    @Override
    public final List<String> cipherSuites() {
        return unmodifiableCiphers;
    }

    @Override
    public ApplicationProtocolNegotiator applicationProtocolNegotiator() {
        return apn;
    }

    @Override
    public final boolean isClient() {
        return mode == SSL.SSL_MODE_CLIENT;
    }

    @Override
    public final SSLEngine newEngine(ByteBufAllocator alloc, String peerHost, int peerPort) {
        return newEngine0(alloc, peerHost, peerPort, true);
    }

    @Override
    protected final SslHandler newHandler(ByteBufAllocator alloc, boolean startTls) {
        return new SslHandler(newEngine0(alloc, null, -1, false), startTls);
    }

    @Override
    protected final SslHandler newHandler(ByteBufAllocator alloc, String peerHost, int peerPort, boolean startTls) {
        return new SslHandler(newEngine0(alloc, peerHost, peerPort, false), startTls);
    }

    @Override
    protected SslHandler newHandler(ByteBufAllocator alloc, boolean startTls, Executor executor) {
        return new SslHandler(newEngine0(alloc, null, -1, false), startTls, executor);
    }

    @Override
    protected SslHandler newHandler(ByteBufAllocator alloc, String peerHost, int peerPort,
                                    boolean startTls, Executor executor) {
        return new SslHandler(newEngine0(alloc, peerHost, peerPort, false), executor);
    }

    SSLEngine newEngine0(ByteBufAllocator alloc, String peerHost, int peerPort, boolean jdkCompatibilityMode) {
        return new ReferenceCountedOpenSslEngine(this, alloc, peerHost, peerPort, jdkCompatibilityMode, true);
    }

    /**
     * Returns a new server-side {@link SSLEngine} with the current configuration.
     */
    @Override
    public final SSLEngine newEngine(ByteBufAllocator alloc) {
        return newEngine(alloc, null, -1);
    }

    /**
     * Returns the pointer to the {@code SSL_CTX} object for this {@link ReferenceCountedOpenSslContext}.
     * Be aware that it is freed as soon as the {@link #finalize()}  method is called.
     * At this point {@code 0} will be returned.
     *
     * @deprecated this method is considered unsafe as the returned pointer may be released later. Dont use it!
     */
    @Deprecated
    public final long context() {
        return sslCtxPointer();
    }

    /**
     * Returns the stats of this context.
     *
     * @deprecated use {@link #sessionContext#stats()}
     */
    @Deprecated
    public final OpenSslSessionStats stats() {
        return sessionContext().stats();
    }

    /**
     * {@deprecated Renegotiation is not supported}
     * Specify if remote initiated renegotiation is supported or not. If not supported and the remote side tries
     * to initiate a renegotiation a {@link SSLHandshakeException} will be thrown during decoding.
     */
    @Deprecated
    public void setRejectRemoteInitiatedRenegotiation(boolean rejectRemoteInitiatedRenegotiation) {
        if (!rejectRemoteInitiatedRenegotiation) {
            throw new UnsupportedOperationException("Renegotiation is not supported");
        }
    }

    /**
     * {@deprecated Renegotiation is not supported}
     * @return {@code true} because renegotiation is not supported.
     */
    @Deprecated
    public boolean getRejectRemoteInitiatedRenegotiation() {
        return true;
    }

    /**
     * Set the size of the buffer used by the BIO for non-application based writes
     * (e.g. handshake, renegotiation, etc...).
     */
    public void setBioNonApplicationBufferSize(int bioNonApplicationBufferSize) {
        this.bioNonApplicationBufferSize =
                checkPositiveOrZero(bioNonApplicationBufferSize, "bioNonApplicationBufferSize");
    }

    /**
     * Returns the size of the buffer used by the BIO for non-application based writes
     */
    public int getBioNonApplicationBufferSize() {
        return bioNonApplicationBufferSize;
    }

    /**
     * Sets the SSL session ticket keys of this context.
     *
     * @deprecated use {@link OpenSslSessionContext#setTicketKeys(byte[])}
     */
    @Deprecated
    public final void setTicketKeys(byte[] keys) {
        sessionContext().setTicketKeys(keys);
    }

    @Override
    public abstract OpenSslSessionContext sessionContext();

    /**
     * Returns the pointer to the {@code SSL_CTX} object for this {@link ReferenceCountedOpenSslContext}.
     * Be aware that it is freed as soon as the {@link #release()} method is called.
     * At this point {@code 0} will be returned.
     *
     * @deprecated this method is considered unsafe as the returned pointer may be released later. Dont use it!
     */
    @Deprecated
    public final long sslCtxPointer() {
        Lock readerLock = ctxLock.readLock();
        readerLock.lock();
        try {
            return SSLContext.getSslCtx(ctx);
        } finally {
            readerLock.unlock();
        }
    }

    /**
     * Set the {@link OpenSslPrivateKeyMethod} to use. This allows to offload private-key operations
     * if needed.
     *
     * This method is currently only supported when {@code BoringSSL} is used.
     *
     * @param        method method to use.
     * @deprecated   use {@link SslContextBuilder#option(SslContextOption, Object)} with
     *              {@link OpenSslContextOption#PRIVATE_KEY_METHOD}.
     */
    @Deprecated
    @UnstableApi
    public final void setPrivateKeyMethod(OpenSslPrivateKeyMethod method) {
        checkNotNull(method, "method");
        Lock writerLock = ctxLock.writeLock();
        writerLock.lock();
        try {
            SSLContext.setPrivateKeyMethod(ctx, new PrivateKeyMethod(engineMap, method));
        } finally {
            writerLock.unlock();
        }
    }

    /**
     * @deprecated   use {@link SslContextBuilder#option(SslContextOption, Object)} with
     *              {@link OpenSslContextOption#USE_TASKS}.
     */
    @Deprecated
    public final void setUseTasks(boolean useTasks) {
        Lock writerLock = ctxLock.writeLock();
        writerLock.lock();
        try {
            SSLContext.setUseTasks(ctx, useTasks);
        } finally {
            writerLock.unlock();
        }
    }

    // IMPORTANT: This method must only be called from either the constructor or the finalizer as a user MUST never
    //            get access to an OpenSslSessionContext after this method was called to prevent the user from
    //            producing a segfault.
    private void destroy() {
        Lock writerLock = ctxLock.writeLock();
        writerLock.lock();
        try {
            if (ctx != 0) {
                if (enableOcsp) {
                    SSLContext.disableOcsp(ctx);
                }

                SSLContext.free(ctx);
                ctx = 0;

                OpenSslSessionContext context = sessionContext();
                if (context != null) {
                    context.destroy();
                }
            }
        } finally {
            writerLock.unlock();
        }
    }

    protected static X509Certificate[] certificates(byte[][] chain) {
        X509Certificate[] peerCerts = new X509Certificate[chain.length];
        for (int i = 0; i < peerCerts.length; i++) {
            peerCerts[i] = new LazyX509Certificate(chain[i]);
        }
        return peerCerts;
    }

    protected static X509TrustManager chooseTrustManager(TrustManager[] managers) {
        for (TrustManager m : managers) {
            if (m instanceof X509TrustManager) {
                if (PlatformDependent.javaVersion() >= 7) {
                    return OpenSslX509TrustManagerWrapper.wrapIfNeeded((X509TrustManager) m);
                }
                return (X509TrustManager) m;
            }
        }
        throw new IllegalStateException("no X509TrustManager found");
    }

    protected static X509KeyManager chooseX509KeyManager(KeyManager[] kms) {
        for (KeyManager km : kms) {
            if (km instanceof X509KeyManager) {
                return (X509KeyManager) km;
            }
        }
        throw new IllegalStateException("no X509KeyManager found");
    }

    /**
     * Translate a {@link ApplicationProtocolConfig} object to a
     * {@link OpenSslApplicationProtocolNegotiator} object.
     *
     * @param config The configuration which defines the translation
     * @return The results of the translation
     */
    @SuppressWarnings("deprecation")
    static OpenSslApplicationProtocolNegotiator toNegotiator(ApplicationProtocolConfig config) {
        if (config == null) {
            return NONE_PROTOCOL_NEGOTIATOR;
        }

        switch (config.protocol()) {
            case NONE:
                return NONE_PROTOCOL_NEGOTIATOR;
            case ALPN:
            case NPN:
            case NPN_AND_ALPN:
                switch (config.selectedListenerFailureBehavior()) {
                    case CHOOSE_MY_LAST_PROTOCOL:
                    case ACCEPT:
                        switch (config.selectorFailureBehavior()) {
                            case CHOOSE_MY_LAST_PROTOCOL:
                            case NO_ADVERTISE:
                                return new OpenSslDefaultApplicationProtocolNegotiator(
                                        config);
                            default:
                                throw new UnsupportedOperationException(
                                        new StringBuilder("OpenSSL provider does not support ")
                                                .append(config.selectorFailureBehavior())
                                                .append(" behavior").toString());
                        }
                    default:
                        throw new UnsupportedOperationException(
                                new StringBuilder("OpenSSL provider does not support ")
                                        .append(config.selectedListenerFailureBehavior())
                                        .append(" behavior").toString());
                }
            default:
                throw new Error();
        }
    }

    @SuppressJava6Requirement(reason = "Guarded by java version check")
    static boolean useExtendedTrustManager(X509TrustManager trustManager) {
        return PlatformDependent.javaVersion() >= 7 && trustManager instanceof X509ExtendedTrustManager;
    }

    @Override
    public final int refCnt() {
        return refCnt.refCnt();
    }

    @Override
    public final ReferenceCounted retain() {
        refCnt.retain();
        return this;
    }

    @Override
    public final ReferenceCounted retain(int increment) {
        refCnt.retain(increment);
        return this;
    }

    @Override
    public final ReferenceCounted touch() {
        refCnt.touch();
        return this;
    }

    @Override
    public final ReferenceCounted touch(Object hint) {
        refCnt.touch(hint);
        return this;
    }

    @Override
    public final boolean release() {
        return refCnt.release();
    }

    @Override
    public final boolean release(int decrement) {
        return refCnt.release(decrement);
    }

    abstract static class AbstractCertificateVerifier extends CertificateVerifier {
        private final OpenSslEngineMap engineMap;

        AbstractCertificateVerifier(OpenSslEngineMap engineMap) {
            this.engineMap = engineMap;
        }

        @Override
        public final int verify(long ssl, byte[][] chain, String auth) {
            final ReferenceCountedOpenSslEngine engine = engineMap.get(ssl);
            if (engine == null) {
                // May be null if it was destroyed in the meantime.
                return CertificateVerifier.X509_V_ERR_UNSPECIFIED;
            }
            X509Certificate[] peerCerts = certificates(chain);
            try {
                verify(engine, peerCerts, auth);
                return CertificateVerifier.X509_V_OK;
            } catch (Throwable cause) {
                logger.debug("verification of certificate failed", cause);
                engine.initHandshakeException(cause);

                // Try to extract the correct error code that should be used.
                if (cause instanceof OpenSslCertificateException) {
                    // This will never return a negative error code as its validated when constructing the
                    // OpenSslCertificateException.
                    return ((OpenSslCertificateException) cause).errorCode();
                }
                if (cause instanceof CertificateExpiredException) {
                    return CertificateVerifier.X509_V_ERR_CERT_HAS_EXPIRED;
                }
                if (cause instanceof CertificateNotYetValidException) {
                    return CertificateVerifier.X509_V_ERR_CERT_NOT_YET_VALID;
                }
                if (PlatformDependent.javaVersion() >= 7) {
                    return translateToError(cause);
                }

                // Could not detect a specific error code to use, so fallback to a default code.
                return CertificateVerifier.X509_V_ERR_UNSPECIFIED;
            }
        }

        @SuppressJava6Requirement(reason = "Usage guarded by java version check")
        private static int translateToError(Throwable cause) {
            if (cause instanceof CertificateRevokedException) {
                return CertificateVerifier.X509_V_ERR_CERT_REVOKED;
            }

            // The X509TrustManagerImpl uses a Validator which wraps a CertPathValidatorException into
            // an CertificateException. So we need to handle the wrapped CertPathValidatorException to be
            // able to send the correct alert.
            Throwable wrapped = cause.getCause();
            while (wrapped != null) {
                if (wrapped instanceof CertPathValidatorException) {
                    CertPathValidatorException ex = (CertPathValidatorException) wrapped;
                    CertPathValidatorException.Reason reason = ex.getReason();
                    if (reason == CertPathValidatorException.BasicReason.EXPIRED) {
                        return CertificateVerifier.X509_V_ERR_CERT_HAS_EXPIRED;
                    }
                    if (reason == CertPathValidatorException.BasicReason.NOT_YET_VALID) {
                        return CertificateVerifier.X509_V_ERR_CERT_NOT_YET_VALID;
                    }
                    if (reason == CertPathValidatorException.BasicReason.REVOKED) {
                        return CertificateVerifier.X509_V_ERR_CERT_REVOKED;
                    }
                }
                wrapped = wrapped.getCause();
            }
            return CertificateVerifier.X509_V_ERR_UNSPECIFIED;
        }

        abstract void verify(ReferenceCountedOpenSslEngine engine, X509Certificate[] peerCerts,
                             String auth) throws Exception;
    }

    private static final class DefaultOpenSslEngineMap implements OpenSslEngineMap {
        private final Map<Long, ReferenceCountedOpenSslEngine> engines = PlatformDependent.newConcurrentHashMap();

        @Override
        public ReferenceCountedOpenSslEngine remove(long ssl) {
            return engines.remove(ssl);
        }

        @Override
        public void add(ReferenceCountedOpenSslEngine engine) {
            engines.put(engine.sslPointer(), engine);
        }

        @Override
        public ReferenceCountedOpenSslEngine get(long ssl) {
            return engines.get(ssl);
        }
    }

    static void setKeyMaterial(long ctx, X509Certificate[] keyCertChain, PrivateKey key, String keyPassword)
            throws SSLException {
         /* Load the certificate file and private key. */
        long keyBio = 0;
        long keyCertChainBio = 0;
        long keyCertChainBio2 = 0;
        PemEncoded encoded = null;
        try {
            // Only encode one time
            encoded = PemX509Certificate.toPEM(ByteBufAllocator.DEFAULT, true, keyCertChain);
            keyCertChainBio = toBIO(ByteBufAllocator.DEFAULT, encoded.retain());
            keyCertChainBio2 = toBIO(ByteBufAllocator.DEFAULT, encoded.retain());

            if (key != null) {
                keyBio = toBIO(ByteBufAllocator.DEFAULT, key);
            }

            SSLContext.setCertificateBio(
                    ctx, keyCertChainBio, keyBio,
                    keyPassword == null ? StringUtil.EMPTY_STRING : keyPassword);
            // We may have more then one cert in the chain so add all of them now.
            SSLContext.setCertificateChainBio(ctx, keyCertChainBio2, true);
        } catch (SSLException e) {
            throw e;
        } catch (Exception e) {
            throw new SSLException("failed to set certificate and key", e);
        } finally {
            freeBio(keyBio);
            freeBio(keyCertChainBio);
            freeBio(keyCertChainBio2);
            if (encoded != null) {
                encoded.release();
            }
        }
    }

    static void freeBio(long bio) {
        if (bio != 0) {
            SSL.freeBIO(bio);
        }
    }

    /**
     * Return the pointer to a <a href="https://www.openssl.org/docs/crypto/BIO_get_mem_ptr.html">in-memory BIO</a>
     * or {@code 0} if the {@code key} is {@code null}. The BIO contains the content of the {@code key}.
     */
    static long toBIO(ByteBufAllocator allocator, PrivateKey key) throws Exception {
        if (key == null) {
            return 0;
        }

        PemEncoded pem = PemPrivateKey.toPEM(allocator, true, key);
        try {
            return toBIO(allocator, pem.retain());
        } finally {
            pem.release();
        }
    }

    /**
     * Return the pointer to a <a href="https://www.openssl.org/docs/crypto/BIO_get_mem_ptr.html">in-memory BIO</a>
     * or {@code 0} if the {@code certChain} is {@code null}. The BIO contains the content of the {@code certChain}.
     */
    static long toBIO(ByteBufAllocator allocator, X509Certificate... certChain) throws Exception {
        if (certChain == null) {
            return 0;
        }

        checkNonEmpty(certChain, "certChain");

        PemEncoded pem = PemX509Certificate.toPEM(allocator, true, certChain);
        try {
            return toBIO(allocator, pem.retain());
        } finally {
            pem.release();
        }
    }

    static long toBIO(ByteBufAllocator allocator, PemEncoded pem) throws Exception {
        try {
            // We can turn direct buffers straight into BIOs. No need to
            // make a yet another copy.
            ByteBuf content = pem.content();

            if (content.isDirect()) {
                return newBIO(content.retainedSlice());
            }

            ByteBuf buffer = allocator.directBuffer(content.readableBytes());
            try {
                buffer.writeBytes(content, content.readerIndex(), content.readableBytes());
                return newBIO(buffer.retainedSlice());
            } finally {
                try {
                    // If the contents of the ByteBuf is sensitive (e.g. a PrivateKey) we
                    // need to zero out the bytes of the copy before we're releasing it.
                    if (pem.isSensitive()) {
                        SslUtils.zeroout(buffer);
                    }
                } finally {
                    buffer.release();
                }
            }
        } finally {
            pem.release();
        }
    }

    private static long newBIO(ByteBuf buffer) throws Exception {
        try {
            long bio = SSL.newMemBIO();
            int readable = buffer.readableBytes();
            if (SSL.bioWrite(bio, OpenSsl.memoryAddress(buffer) + buffer.readerIndex(), readable) != readable) {
                SSL.freeBIO(bio);
                throw new IllegalStateException("Could not write data to memory BIO");
            }
            return bio;
        } finally {
            buffer.release();
        }
    }

    /**
     * Returns the {@link OpenSslKeyMaterialProvider} that should be used for OpenSSL. Depending on the given
     * {@link KeyManagerFactory} this may cache the {@link OpenSslKeyMaterial} for better performance if it can
     * ensure that the same material is always returned for the same alias.
     */
    static OpenSslKeyMaterialProvider providerFor(KeyManagerFactory factory, String password) {
        if (factory instanceof OpenSslX509KeyManagerFactory) {
            return ((OpenSslX509KeyManagerFactory) factory).newProvider();
        }

        if (factory instanceof OpenSslCachingX509KeyManagerFactory) {
            // The user explicit used OpenSslCachingX509KeyManagerFactory which signals us that its fine to cache.
            return ((OpenSslCachingX509KeyManagerFactory) factory).newProvider(password);
        }
        // We can not be sure if the material may change at runtime so we will not cache it.
        return new OpenSslKeyMaterialProvider(chooseX509KeyManager(factory.getKeyManagers()), password);
    }

    private static final class PrivateKeyMethod implements SSLPrivateKeyMethod {

        private final OpenSslEngineMap engineMap;
        private final OpenSslPrivateKeyMethod keyMethod;
        PrivateKeyMethod(OpenSslEngineMap engineMap, OpenSslPrivateKeyMethod keyMethod) {
            this.engineMap = engineMap;
            this.keyMethod = keyMethod;
        }

        private ReferenceCountedOpenSslEngine retrieveEngine(long ssl) throws SSLException {
            ReferenceCountedOpenSslEngine engine = engineMap.get(ssl);
            if (engine == null) {
                throw new SSLException("Could not find a " +
                        StringUtil.simpleClassName(ReferenceCountedOpenSslEngine.class) + " for sslPointer " + ssl);
            }
            return engine;
        }

        @Override
        public byte[] sign(long ssl, int signatureAlgorithm, byte[] digest) throws Exception {
            ReferenceCountedOpenSslEngine engine = retrieveEngine(ssl);
            try {
                return verifyResult(keyMethod.sign(engine, signatureAlgorithm, digest));
            } catch (Exception e) {
                engine.initHandshakeException(e);
                throw e;
            }
        }

        @Override
        public byte[] decrypt(long ssl, byte[] input) throws Exception {
            ReferenceCountedOpenSslEngine engine = retrieveEngine(ssl);
            try {
                return verifyResult(keyMethod.decrypt(engine, input));
            } catch (Exception e) {
                engine.initHandshakeException(e);
                throw e;
            }
        }
    }

    private static final class AsyncPrivateKeyMethod implements AsyncSSLPrivateKeyMethod {

        private final OpenSslEngineMap engineMap;
        private final OpenSslAsyncPrivateKeyMethod keyMethod;

        AsyncPrivateKeyMethod(OpenSslEngineMap engineMap, OpenSslAsyncPrivateKeyMethod keyMethod) {
            this.engineMap = engineMap;
            this.keyMethod = keyMethod;
        }

        private ReferenceCountedOpenSslEngine retrieveEngine(long ssl) throws SSLException {
            ReferenceCountedOpenSslEngine engine = engineMap.get(ssl);
            if (engine == null) {
                throw new SSLException("Could not find a " +
                        StringUtil.simpleClassName(ReferenceCountedOpenSslEngine.class) + " for sslPointer " + ssl);
            }
            return engine;
        }

        @Override
        public void sign(long ssl, int signatureAlgorithm, byte[] bytes, ResultCallback<byte[]> resultCallback) {
            try {
                ReferenceCountedOpenSslEngine engine = retrieveEngine(ssl);
                keyMethod.sign(engine, signatureAlgorithm, bytes)
                        .addListener(new ResultCallbackListener(engine, ssl, resultCallback));
            } catch (SSLException e) {
                resultCallback.onError(ssl, e);
            }
        }

        @Override
        public void decrypt(long ssl, byte[] bytes, ResultCallback<byte[]> resultCallback) {
            try {
                ReferenceCountedOpenSslEngine engine = retrieveEngine(ssl);
                keyMethod.decrypt(engine, bytes)
                        .addListener(new ResultCallbackListener(engine, ssl, resultCallback));
            } catch (SSLException e) {
                resultCallback.onError(ssl, e);
            }
        }

        private static final class ResultCallbackListener implements FutureListener<byte[]> {
            private final ReferenceCountedOpenSslEngine engine;
            private final long ssl;
            private final ResultCallback<byte[]> resultCallback;

            ResultCallbackListener(ReferenceCountedOpenSslEngine engine, long ssl,
                                   ResultCallback<byte[]> resultCallback) {
                this.engine = engine;
                this.ssl = ssl;
                this.resultCallback = resultCallback;
            }

            @Override
            public void operationComplete(Future<byte[]> future) {
                Throwable cause = future.cause();
                if (cause == null) {
                    try {
                        byte[] result = verifyResult(future.getNow());
                        resultCallback.onSuccess(ssl, result);
                        return;
                    } catch (SignatureException e) {
                        cause = e;
                        engine.initHandshakeException(e);
                    }
                }
                resultCallback.onError(ssl, cause);
            }
        }
    }

    private static byte[] verifyResult(byte[] result) throws SignatureException {
        if (result == null) {
            throw new SignatureException();
        }
        return result;
    }
}
