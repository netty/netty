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
import io.netty.handler.ssl.util.LazyJavaxX509Certificate;
import io.netty.handler.ssl.util.LazyX509Certificate;
import io.netty.internal.tcnative.AsyncTask;
import io.netty.internal.tcnative.Buffer;
import io.netty.internal.tcnative.SSL;
import io.netty.util.AbstractReferenceCounted;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCounted;
import io.netty.util.ResourceLeakDetector;
import io.netty.util.ResourceLeakDetectorFactory;
import io.netty.util.ResourceLeakTracker;
import io.netty.util.internal.EmptyArrays;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.SuppressJava6Requirement;
import io.netty.util.internal.ThrowableUtil;
import io.netty.util.internal.UnstableApi;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.security.Principal;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;

import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSessionBindingEvent;
import javax.net.ssl.SSLSessionBindingListener;
import javax.security.cert.X509Certificate;

import static io.netty.handler.ssl.OpenSsl.memoryAddress;
import static io.netty.handler.ssl.SslUtils.SSL_RECORD_HEADER_LENGTH;
import static io.netty.util.internal.ObjectUtil.checkNotNull;
import static io.netty.util.internal.ObjectUtil.checkNotNullArrayParam;
import static io.netty.util.internal.ObjectUtil.checkNotNullWithIAE;
import static java.lang.Integer.MAX_VALUE;
import static java.lang.Math.min;
import static javax.net.ssl.SSLEngineResult.HandshakeStatus.FINISHED;
import static javax.net.ssl.SSLEngineResult.HandshakeStatus.NEED_TASK;
import static javax.net.ssl.SSLEngineResult.HandshakeStatus.NEED_UNWRAP;
import static javax.net.ssl.SSLEngineResult.HandshakeStatus.NEED_WRAP;
import static javax.net.ssl.SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING;
import static javax.net.ssl.SSLEngineResult.Status.BUFFER_OVERFLOW;
import static javax.net.ssl.SSLEngineResult.Status.BUFFER_UNDERFLOW;
import static javax.net.ssl.SSLEngineResult.Status.CLOSED;
import static javax.net.ssl.SSLEngineResult.Status.OK;

/**
 * Implements a {@link SSLEngine} using
 * <a href="https://www.openssl.org/docs/crypto/BIO_s_bio.html#EXAMPLE">OpenSSL BIO abstractions</a>.
 * <p>Instances of this class must be {@link #release() released} or else native memory will leak!
 *
 * <p>Instances of this class <strong>must</strong> be released before the {@link ReferenceCountedOpenSslContext}
 * the instance depends upon are released. Otherwise if any method of this class is called which uses the
 * the {@link ReferenceCountedOpenSslContext} JNI resources the JVM may crash.
 */
public class ReferenceCountedOpenSslEngine extends SSLEngine implements ReferenceCounted, ApplicationProtocolAccessor {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(ReferenceCountedOpenSslEngine.class);

    private static final ResourceLeakDetector<ReferenceCountedOpenSslEngine> leakDetector =
            ResourceLeakDetectorFactory.instance().newResourceLeakDetector(ReferenceCountedOpenSslEngine.class);
    private static final int OPENSSL_OP_NO_PROTOCOL_INDEX_SSLV2 = 0;
    private static final int OPENSSL_OP_NO_PROTOCOL_INDEX_SSLV3 = 1;
    private static final int OPENSSL_OP_NO_PROTOCOL_INDEX_TLSv1 = 2;
    private static final int OPENSSL_OP_NO_PROTOCOL_INDEX_TLSv1_1 = 3;
    private static final int OPENSSL_OP_NO_PROTOCOL_INDEX_TLSv1_2 = 4;
    private static final int OPENSSL_OP_NO_PROTOCOL_INDEX_TLSv1_3 = 5;
    private static final int[] OPENSSL_OP_NO_PROTOCOLS = {
            SSL.SSL_OP_NO_SSLv2,
            SSL.SSL_OP_NO_SSLv3,
            SSL.SSL_OP_NO_TLSv1,
            SSL.SSL_OP_NO_TLSv1_1,
            SSL.SSL_OP_NO_TLSv1_2,
            SSL.SSL_OP_NO_TLSv1_3
    };

    /**
     * Depends upon tcnative ... only use if tcnative is available!
     */
    static final int MAX_PLAINTEXT_LENGTH = SSL.SSL_MAX_PLAINTEXT_LENGTH;
    /**
     * Depends upon tcnative ... only use if tcnative is available!
     */
    static final int MAX_RECORD_SIZE = SSL.SSL_MAX_RECORD_LENGTH;

    private static final SSLEngineResult NEED_UNWRAP_OK = new SSLEngineResult(OK, NEED_UNWRAP, 0, 0);
    private static final SSLEngineResult NEED_UNWRAP_CLOSED = new SSLEngineResult(CLOSED, NEED_UNWRAP, 0, 0);
    private static final SSLEngineResult NEED_WRAP_OK = new SSLEngineResult(OK, NEED_WRAP, 0, 0);
    private static final SSLEngineResult NEED_WRAP_CLOSED = new SSLEngineResult(CLOSED, NEED_WRAP, 0, 0);
    private static final SSLEngineResult CLOSED_NOT_HANDSHAKING = new SSLEngineResult(CLOSED, NOT_HANDSHAKING, 0, 0);

    // OpenSSL state
    private long ssl;
    private long networkBIO;

    private enum HandshakeState {
        /**
         * Not started yet.
         */
        NOT_STARTED,
        /**
         * Started via unwrap/wrap.
         */
        STARTED_IMPLICITLY,
        /**
         * Started via {@link #beginHandshake()}.
         */
        STARTED_EXPLICITLY,
        /**
         * Handshake is finished.
         */
        FINISHED
    }

    private HandshakeState handshakeState = HandshakeState.NOT_STARTED;
    private boolean receivedShutdown;
    private volatile boolean destroyed;
    private volatile String applicationProtocol;
    private volatile boolean needTask;
    private String[] explicitlyEnabledProtocols;
    private boolean sessionSet;

    // Reference Counting
    private final ResourceLeakTracker<ReferenceCountedOpenSslEngine> leak;
    private final AbstractReferenceCounted refCnt = new AbstractReferenceCounted() {
        @Override
        public ReferenceCounted touch(Object hint) {
            if (leak != null) {
                leak.record(hint);
            }

            return ReferenceCountedOpenSslEngine.this;
        }

        @Override
        protected void deallocate() {
            shutdown();
            if (leak != null) {
                boolean closed = leak.close(ReferenceCountedOpenSslEngine.this);
                assert closed;
            }
            parentContext.release();
        }
    };

    private volatile ClientAuth clientAuth = ClientAuth.NONE;

    // Updated once a new handshake is started and so the SSLSession reused.
    private volatile long lastAccessed = -1;

    private String endPointIdentificationAlgorithm;
    // Store as object as AlgorithmConstraints only exists since java 7.
    private Object algorithmConstraints;
    private List<String> sniHostNames;

    // Mark as volatile as accessed by checkSniHostnameMatch(...) and also not specify the SNIMatcher type to allow us
    // using it with java7.
    private volatile Collection<?> matchers;

    // SSL Engine status variables
    private boolean isInboundDone;
    private boolean outboundClosed;

    final boolean jdkCompatibilityMode;
    private final boolean clientMode;
    final ByteBufAllocator alloc;
    private final OpenSslEngineMap engineMap;
    private final OpenSslApplicationProtocolNegotiator apn;
    private final ReferenceCountedOpenSslContext parentContext;
    private final OpenSslSession session;
    private final ByteBuffer[] singleSrcBuffer = new ByteBuffer[1];
    private final ByteBuffer[] singleDstBuffer = new ByteBuffer[1];
    private final boolean enableOcsp;
    private int maxWrapOverhead;
    private int maxWrapBufferSize;
    private Throwable pendingException;

    /**
     * Create a new instance.
     * @param context Reference count release responsibility is not transferred! The callee still owns this object.
     * @param alloc The allocator to use.
     * @param peerHost The peer host name.
     * @param peerPort The peer port.
     * @param jdkCompatibilityMode {@code true} to behave like described in
     *                             https://docs.oracle.com/javase/7/docs/api/javax/net/ssl/SSLEngine.html.
     *                             {@code false} allows for partial and/or multiple packets to be process in a single
     *                             wrap or unwrap call.
     * @param leakDetection {@code true} to enable leak detection of this object.
     */
    ReferenceCountedOpenSslEngine(ReferenceCountedOpenSslContext context, final ByteBufAllocator alloc, String peerHost,
                                  int peerPort, boolean jdkCompatibilityMode, boolean leakDetection) {
        super(peerHost, peerPort);
        OpenSsl.ensureAvailability();
        this.alloc = checkNotNull(alloc, "alloc");
        apn = (OpenSslApplicationProtocolNegotiator) context.applicationProtocolNegotiator();
        clientMode = context.isClient();

        if (PlatformDependent.javaVersion() >= 7) {
            session = new ExtendedOpenSslSession(new DefaultOpenSslSession(context.sessionContext())) {
                private String[] peerSupportedSignatureAlgorithms;
                private List requestedServerNames;

                @Override
                public List getRequestedServerNames() {
                    if (clientMode) {
                        return Java8SslUtils.getSniHostNames(sniHostNames);
                    } else {
                        synchronized (ReferenceCountedOpenSslEngine.this) {
                            if (requestedServerNames == null) {
                                if (isDestroyed()) {
                                    requestedServerNames = Collections.emptyList();
                                } else {
                                    String name = SSL.getSniHostname(ssl);
                                    if (name == null) {
                                        requestedServerNames = Collections.emptyList();
                                    } else {
                                        // Convert to bytes as we do not want to do any strict validation of the
                                        // SNIHostName while creating it.
                                        requestedServerNames =
                                                Java8SslUtils.getSniHostName(
                                                        SSL.getSniHostname(ssl).getBytes(CharsetUtil.UTF_8));
                                    }
                                }
                            }
                            return requestedServerNames;
                        }
                    }
                }

                @Override
                public String[] getPeerSupportedSignatureAlgorithms() {
                    synchronized (ReferenceCountedOpenSslEngine.this) {
                        if (peerSupportedSignatureAlgorithms == null) {
                            if (isDestroyed()) {
                                peerSupportedSignatureAlgorithms = EmptyArrays.EMPTY_STRINGS;
                            } else {
                                String[] algs = SSL.getSigAlgs(ssl);
                                if (algs == null) {
                                    peerSupportedSignatureAlgorithms = EmptyArrays.EMPTY_STRINGS;
                                } else {
                                    Set<String> algorithmList = new LinkedHashSet<String>(algs.length);
                                    for (String alg: algs) {
                                        String converted = SignatureAlgorithmConverter.toJavaName(alg);

                                        if (converted != null) {
                                            algorithmList.add(converted);
                                        }
                                    }
                                    peerSupportedSignatureAlgorithms = algorithmList.toArray(new String[0]);
                                }
                            }
                        }
                        return peerSupportedSignatureAlgorithms.clone();
                    }
                }

                @Override
                public List<byte[]> getStatusResponses() {
                    byte[] ocspResponse = null;
                    if (enableOcsp && clientMode) {
                        synchronized (ReferenceCountedOpenSslEngine.this) {
                            if (!isDestroyed()) {
                                ocspResponse = SSL.getOcspResponse(ssl);
                            }
                        }
                    }
                    return ocspResponse == null ?
                            Collections.<byte[]>emptyList() : Collections.singletonList(ocspResponse);
                }
            };
        } else {
            session = new DefaultOpenSslSession(context.sessionContext());
        }
        engineMap = context.engineMap;
        enableOcsp = context.enableOcsp;
        if (!context.sessionContext().useKeyManager()) {
            session.setLocalCertificate(context.keyCertChain);
        }

        this.jdkCompatibilityMode = jdkCompatibilityMode;
        Lock readerLock = context.ctxLock.readLock();
        readerLock.lock();
        final long finalSsl;
        try {
            finalSsl = SSL.newSSL(context.ctx, !context.isClient());
        } finally {
            readerLock.unlock();
        }
        synchronized (this) {
            ssl = finalSsl;
            try {
                networkBIO = SSL.bioNewByteBuffer(ssl, context.getBioNonApplicationBufferSize());

                // Set the client auth mode, this needs to be done via setClientAuth(...) method so we actually call the
                // needed JNI methods.
                setClientAuth(clientMode ? ClientAuth.NONE : context.clientAuth);

                if (context.protocols != null) {
                    setEnabledProtocols0(context.protocols, true);
                } else {
                    this.explicitlyEnabledProtocols = getEnabledProtocols();
                }

                // Use SNI if peerHost was specified and a valid hostname
                // See https://github.com/netty/netty/issues/4746
                if (clientMode && SslUtils.isValidHostNameForSNI(peerHost)) {
                    SSL.setTlsExtHostName(ssl, peerHost);
                    sniHostNames = Collections.singletonList(peerHost);
                }

                if (enableOcsp) {
                    SSL.enableOcsp(ssl);
                }

                if (!jdkCompatibilityMode) {
                    SSL.setMode(ssl, SSL.getMode(ssl) | SSL.SSL_MODE_ENABLE_PARTIAL_WRITE);
                }

                if (isProtocolEnabled(SSL.getOptions(ssl), SSL.SSL_OP_NO_TLSv1_3, SslProtocols.TLS_v1_3)) {
                    final boolean enableTickets = clientMode ?
                            ReferenceCountedOpenSslContext.CLIENT_ENABLE_SESSION_TICKET_TLSV13 :
                            ReferenceCountedOpenSslContext.SERVER_ENABLE_SESSION_TICKET_TLSV13;
                    if (enableTickets) {
                        // We should enable session tickets for stateless resumption when TLSv1.3 is enabled. This
                        // is also done by OpenJDK and without this session resumption does not work at all with
                        // BoringSSL when TLSv1.3 is used as BoringSSL only supports stateless resumption with TLSv1.3:
                        //
                        // See:
                        //  - https://bugs.openjdk.java.net/browse/JDK-8223922
                        //  - https://boringssl.googlesource.com/boringssl/+/refs/heads/master/ssl/tls13_server.cc#104
                        SSL.clearOptions(ssl, SSL.SSL_OP_NO_TICKET);
                    }
                }

                // setMode may impact the overhead.
                calculateMaxWrapOverhead();
            } catch (Throwable cause) {
                // Call shutdown so we are sure we correctly release all native memory and also guard against the
                // case when shutdown() will be called by the finalizer again.
                shutdown();

                PlatformDependent.throwException(cause);
            }
        }

        // Now that everything looks good and we're going to successfully return the
        // object so we need to retain a reference to the parent context.
        parentContext = context;
        parentContext.retain();

        // Only create the leak after everything else was executed and so ensure we don't produce a false-positive for
        // the ResourceLeakDetector.
        leak = leakDetection ? leakDetector.track(this) : null;
    }

    final synchronized String[] authMethods() {
        if (isDestroyed()) {
            return EmptyArrays.EMPTY_STRINGS;
        }
        return SSL.authenticationMethods(ssl);
    }

    final boolean setKeyMaterial(OpenSslKeyMaterial keyMaterial) throws  Exception {
        synchronized (this) {
            if (isDestroyed()) {
                return false;
            }
            SSL.setKeyMaterial(ssl, keyMaterial.certificateChainAddress(), keyMaterial.privateKeyAddress());
        }
        session.setLocalCertificate(keyMaterial.certificateChain());
        return true;
    }

    final synchronized SecretKeySpec masterKey() {
        if (isDestroyed()) {
            return null;
        }
        return new SecretKeySpec(SSL.getMasterKey(ssl), "AES");
    }

    synchronized boolean isSessionReused() {
        if (isDestroyed()) {
            return false;
        }
        return SSL.isSessionReused(ssl);
    }

    /**
     * Sets the OCSP response.
     */
    @UnstableApi
    public void setOcspResponse(byte[] response) {
        if (!enableOcsp) {
            throw new IllegalStateException("OCSP stapling is not enabled");
        }

        if (clientMode) {
            throw new IllegalStateException("Not a server SSLEngine");
        }

        synchronized (this) {
            if (!isDestroyed()) {
                SSL.setOcspResponse(ssl, response);
            }
        }
    }

    /**
     * Returns the OCSP response or {@code null} if the server didn't provide a stapled OCSP response.
     */
    @UnstableApi
    public byte[] getOcspResponse() {
        if (!enableOcsp) {
            throw new IllegalStateException("OCSP stapling is not enabled");
        }

        if (!clientMode) {
            throw new IllegalStateException("Not a client SSLEngine");
        }

        synchronized (this) {
            if (isDestroyed()) {
                return EmptyArrays.EMPTY_BYTES;
            }
            return SSL.getOcspResponse(ssl);
        }
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

    // These method will override the method defined by Java 8u251 and later. As we may compile with an earlier
    // java8 version we don't use @Override annotations here.
    public String getApplicationProtocol() {
        return applicationProtocol;
    }

    // These method will override the method defined by Java 8u251 and later. As we may compile with an earlier
    // java8 version we don't use @Override annotations here.
    public String getHandshakeApplicationProtocol() {
        return applicationProtocol;
    }

    @Override
    public final synchronized SSLSession getHandshakeSession() {
        // Javadocs state return value should be:
        // null if this instance is not currently handshaking, or if the current handshake has not
        // progressed far enough to create a basic SSLSession. Otherwise, this method returns the
        // SSLSession currently being negotiated.
        switch(handshakeState) {
            case NOT_STARTED:
            case FINISHED:
                return null;
            default:
                return session;
        }
    }

    /**
     * Returns the pointer to the {@code SSL} object for this {@link ReferenceCountedOpenSslEngine}.
     * Be aware that it is freed as soon as the {@link #release()} or {@link #shutdown()} methods are called.
     * At this point {@code 0} will be returned.
     */
    public final synchronized long sslPointer() {
        return ssl;
    }

    /**
     * Destroys this engine.
     */
    public final synchronized void shutdown() {
        if (!destroyed) {
            destroyed = true;
            engineMap.remove(ssl);
            SSL.freeSSL(ssl);
            ssl = networkBIO = 0;

            isInboundDone = outboundClosed = true;
        }

        // On shutdown clear all errors
        SSL.clearError();
    }

    /**
     * Write plaintext data to the OpenSSL internal BIO
     *
     * Calling this function with src.remaining == 0 is undefined.
     */
    private int writePlaintextData(final ByteBuffer src, int len) {
        final int pos = src.position();
        final int limit = src.limit();
        final int sslWrote;

        if (src.isDirect()) {
            sslWrote = SSL.writeToSSL(ssl, bufferAddress(src) + pos, len);
            if (sslWrote > 0) {
                src.position(pos + sslWrote);
            }
        } else {
            ByteBuf buf = alloc.directBuffer(len);
            try {
                src.limit(pos + len);

                buf.setBytes(0, src);
                src.limit(limit);

                sslWrote = SSL.writeToSSL(ssl, memoryAddress(buf), len);
                if (sslWrote > 0) {
                    src.position(pos + sslWrote);
                } else {
                    src.position(pos);
                }
            } finally {
                buf.release();
            }
        }
        return sslWrote;
    }

    /**
     * Write encrypted data to the OpenSSL network BIO.
     */
    private ByteBuf writeEncryptedData(final ByteBuffer src, int len) throws SSLException {
        final int pos = src.position();
        if (src.isDirect()) {
            SSL.bioSetByteBuffer(networkBIO, bufferAddress(src) + pos, len, false);
        } else {
            final ByteBuf buf = alloc.directBuffer(len);
            try {
                final int limit = src.limit();
                src.limit(pos + len);
                buf.writeBytes(src);
                // Restore the original position and limit because we don't want to consume from `src`.
                src.position(pos);
                src.limit(limit);

                SSL.bioSetByteBuffer(networkBIO, memoryAddress(buf), len, false);
                return buf;
            } catch (Throwable cause) {
                buf.release();
                PlatformDependent.throwException(cause);
            }
        }
        return null;
    }

    /**
     * Read plaintext data from the OpenSSL internal BIO
     */
    private int readPlaintextData(final ByteBuffer dst) throws SSLException {
        final int sslRead;
        final int pos = dst.position();
        if (dst.isDirect()) {
            sslRead = SSL.readFromSSL(ssl, bufferAddress(dst) + pos, dst.limit() - pos);
            if (sslRead > 0) {
                dst.position(pos + sslRead);
            }
        } else {
            final int limit = dst.limit();
            final int len = min(maxEncryptedPacketLength0(), limit - pos);
            final ByteBuf buf = alloc.directBuffer(len);
            try {
                sslRead = SSL.readFromSSL(ssl, memoryAddress(buf), len);
                if (sslRead > 0) {
                    dst.limit(pos + sslRead);
                    buf.getBytes(buf.readerIndex(), dst);
                    dst.limit(limit);
                }
            } finally {
                buf.release();
            }
        }

        return sslRead;
    }

    /**
     * Visible only for testing!
     */
    final synchronized int maxWrapOverhead() {
        return maxWrapOverhead;
    }

    /**
     * Visible only for testing!
     */
    final synchronized int maxEncryptedPacketLength() {
        return maxEncryptedPacketLength0();
    }

    /**
     * This method is intentionally not synchronized, only use if you know you are in the EventLoop
     * thread and visibility on {@link #maxWrapOverhead} is achieved via other synchronized blocks.
     */
    final int maxEncryptedPacketLength0() {
        return maxWrapOverhead + MAX_PLAINTEXT_LENGTH;
    }

    /**
     * This method is intentionally not synchronized, only use if you know you are in the EventLoop
     * thread and visibility on {@link #maxWrapBufferSize} and {@link #maxWrapOverhead} is achieved
     * via other synchronized blocks.
     */
    final int calculateMaxLengthForWrap(int plaintextLength, int numComponents) {
        return (int) min(maxWrapBufferSize, plaintextLength + (long) maxWrapOverhead * numComponents);
    }

    final synchronized int sslPending() {
        return sslPending0();
    }

    /**
     * It is assumed this method is called in a synchronized block (or the constructor)!
     */
    private void calculateMaxWrapOverhead() {
        maxWrapOverhead = SSL.getMaxWrapOverhead(ssl);

        // maxWrapBufferSize must be set after maxWrapOverhead because there is a dependency on this value.
        // If jdkCompatibility mode is off we allow enough space to encrypt 16 buffers at a time. This could be
        // configurable in the future if necessary.
        maxWrapBufferSize = jdkCompatibilityMode ? maxEncryptedPacketLength0() : maxEncryptedPacketLength0() << 4;
    }

    private int sslPending0() {
        // OpenSSL has a limitation where if you call SSL_pending before the handshake is complete OpenSSL will throw a
        // "called a function you should not call" error. Using the TLS_method instead of SSLv23_method may solve this
        // issue but this API is only available in 1.1.0+ [1].
        // [1] https://www.openssl.org/docs/man1.1.0/ssl/SSL_CTX_new.html
        return handshakeState != HandshakeState.FINISHED ? 0 : SSL.sslPending(ssl);
    }

    private boolean isBytesAvailableEnoughForWrap(int bytesAvailable, int plaintextLength, int numComponents) {
        return bytesAvailable - (long) maxWrapOverhead * numComponents >= plaintextLength;
    }

    @Override
    public final SSLEngineResult wrap(
            final ByteBuffer[] srcs, int offset, final int length, final ByteBuffer dst) throws SSLException {
        // Throw required runtime exceptions
        checkNotNullWithIAE(srcs, "srcs");
        checkNotNullWithIAE(dst, "dst");

        if (offset >= srcs.length || offset + length > srcs.length) {
            throw new IndexOutOfBoundsException(
                    "offset: " + offset + ", length: " + length +
                            " (expected: offset <= offset + length <= srcs.length (" + srcs.length + "))");
        }

        if (dst.isReadOnly()) {
            throw new ReadOnlyBufferException();
        }

        synchronized (this) {
            if (isOutboundDone()) {
                // All drained in the outbound buffer
                return isInboundDone() || isDestroyed() ? CLOSED_NOT_HANDSHAKING : NEED_UNWRAP_CLOSED;
            }

            int bytesProduced = 0;
            ByteBuf bioReadCopyBuf = null;
            try {
                // Setup the BIO buffer so that we directly write the encryption results into dst.
                if (dst.isDirect()) {
                    SSL.bioSetByteBuffer(networkBIO, bufferAddress(dst) + dst.position(), dst.remaining(),
                            true);
                } else {
                    bioReadCopyBuf = alloc.directBuffer(dst.remaining());
                    SSL.bioSetByteBuffer(networkBIO, memoryAddress(bioReadCopyBuf), bioReadCopyBuf.writableBytes(),
                            true);
                }

                int bioLengthBefore = SSL.bioLengthByteBuffer(networkBIO);

                // Explicitly use outboundClosed as we want to drain any bytes that are still present.
                if (outboundClosed) {
                    // If the outbound was closed we want to ensure we can produce the alert to the destination buffer.
                    // This is true even if we not using jdkCompatibilityMode.
                    //
                    // We use a plaintextLength of 2 as we at least want to have an alert fit into it.
                    // https://tools.ietf.org/html/rfc5246#section-7.2
                    if (!isBytesAvailableEnoughForWrap(dst.remaining(), 2, 1)) {
                        return new SSLEngineResult(BUFFER_OVERFLOW, getHandshakeStatus(), 0, 0);
                    }

                    // There is something left to drain.
                    // See https://github.com/netty/netty/issues/6260
                    bytesProduced = SSL.bioFlushByteBuffer(networkBIO);
                    if (bytesProduced <= 0) {
                        return newResultMayFinishHandshake(NOT_HANDSHAKING, 0, 0);
                    }
                    // It is possible when the outbound was closed there was not enough room in the non-application
                    // buffers to hold the close_notify. We should keep trying to close until we consume all the data
                    // OpenSSL can give us.
                    if (!doSSLShutdown()) {
                        return newResultMayFinishHandshake(NOT_HANDSHAKING, 0, bytesProduced);
                    }
                    bytesProduced = bioLengthBefore - SSL.bioLengthByteBuffer(networkBIO);
                    return newResultMayFinishHandshake(NEED_WRAP, 0, bytesProduced);
                }

                // Flush any data that may be implicitly generated by OpenSSL (handshake, close, etc..).
                SSLEngineResult.HandshakeStatus status = NOT_HANDSHAKING;
                // Prepare OpenSSL to work in server mode and receive handshake
                if (handshakeState != HandshakeState.FINISHED) {
                    if (handshakeState != HandshakeState.STARTED_EXPLICITLY) {
                        // Update accepted so we know we triggered the handshake via wrap
                        handshakeState = HandshakeState.STARTED_IMPLICITLY;
                    }

                    // Flush any data that may have been written implicitly during the handshake by OpenSSL.
                    bytesProduced = SSL.bioFlushByteBuffer(networkBIO);

                    if (pendingException != null) {
                        // TODO(scott): It is possible that when the handshake failed there was not enough room in the
                        // non-application buffers to hold the alert. We should get all the data before progressing on.
                        // However I'm not aware of a way to do this with the OpenSSL APIs.
                        // See https://github.com/netty/netty/issues/6385.

                        // We produced / consumed some data during the handshake, signal back to the caller.
                        // If there is a handshake exception and we have produced data, we should send the data before
                        // we allow handshake() to throw the handshake exception.
                        //
                        // When the user calls wrap() again we will propagate the handshake error back to the user as
                        // soon as there is no more data to was produced (as part of an alert etc).
                        if (bytesProduced > 0) {
                            return newResult(NEED_WRAP, 0, bytesProduced);
                        }
                        // Nothing was produced see if there is a handshakeException that needs to be propagated
                        // to the caller by calling handshakeException() which will return the right HandshakeStatus
                        // if it can "recover" from the exception for now.
                        return newResult(handshakeException(), 0, 0);
                    }

                    status = handshake();

                    // Handshake may have generated more data, for example if the internal SSL buffer is small
                    // we may have freed up space by flushing above.
                    bytesProduced = bioLengthBefore - SSL.bioLengthByteBuffer(networkBIO);

                    if (status == NEED_TASK) {
                        return newResult(status, 0, bytesProduced);
                    }

                    if (bytesProduced > 0) {
                        // If we have filled up the dst buffer and we have not finished the handshake we should try to
                        // wrap again. Otherwise we should only try to wrap again if there is still data pending in
                        // SSL buffers.
                        return newResult(mayFinishHandshake(status != FINISHED ?
                                         bytesProduced == bioLengthBefore ? NEED_WRAP :
                                         getHandshakeStatus(SSL.bioLengthNonApplication(networkBIO)) : FINISHED),
                                         0, bytesProduced);
                    }

                    if (status == NEED_UNWRAP) {
                        // Signal if the outbound is done or not.
                        return isOutboundDone() ? NEED_UNWRAP_CLOSED : NEED_UNWRAP_OK;
                    }

                    // Explicit use outboundClosed and not outboundClosed() as we want to drain any bytes that are
                    // still present.
                    if (outboundClosed) {
                        bytesProduced = SSL.bioFlushByteBuffer(networkBIO);
                        return newResultMayFinishHandshake(status, 0, bytesProduced);
                    }
                }

                final int endOffset = offset + length;
                if (jdkCompatibilityMode) {
                    int srcsLen = 0;
                    for (int i = offset; i < endOffset; ++i) {
                        final ByteBuffer src = srcs[i];
                        if (src == null) {
                            throw new IllegalArgumentException("srcs[" + i + "] is null");
                        }
                        if (srcsLen == MAX_PLAINTEXT_LENGTH) {
                            continue;
                        }

                        srcsLen += src.remaining();
                        if (srcsLen > MAX_PLAINTEXT_LENGTH || srcsLen < 0) {
                            // If srcLen > MAX_PLAINTEXT_LENGTH or secLen < 0 just set it to MAX_PLAINTEXT_LENGTH.
                            // This also help us to guard against overflow.
                            // We not break out here as we still need to check for null entries in srcs[].
                            srcsLen = MAX_PLAINTEXT_LENGTH;
                        }
                    }

                    // jdkCompatibilityMode will only produce a single TLS packet, and we don't aggregate src buffers,
                    // so we always fix the number of buffers to 1 when checking if the dst buffer is large enough.
                    if (!isBytesAvailableEnoughForWrap(dst.remaining(), srcsLen, 1)) {
                        return new SSLEngineResult(BUFFER_OVERFLOW, getHandshakeStatus(), 0, 0);
                    }
                }

                // There was no pending data in the network BIO -- encrypt any application data
                int bytesConsumed = 0;
                assert bytesProduced == 0;

                // Flush any data that may have been written implicitly by OpenSSL in case a shutdown/alert occurs.
                bytesProduced = SSL.bioFlushByteBuffer(networkBIO);

                if (bytesProduced > 0) {
                    return newResultMayFinishHandshake(status, bytesConsumed, bytesProduced);
                }
                // There was a pending exception that we just delayed because there was something to produce left.
                // Throw it now and shutdown the engine.
                if (pendingException != null) {
                    Throwable error = pendingException;
                    pendingException = null;
                    shutdown();
                    // Throw a new exception wrapping the pending exception, so the stacktrace is meaningful and
                    // contains all the details.
                    throw new SSLException(error);
                }

                for (; offset < endOffset; ++offset) {
                    final ByteBuffer src = srcs[offset];
                    final int remaining = src.remaining();
                    if (remaining == 0) {
                        continue;
                    }

                    final int bytesWritten;
                    if (jdkCompatibilityMode) {
                        // Write plaintext application data to the SSL engine. We don't have to worry about checking
                        // if there is enough space if jdkCompatibilityMode because we only wrap at most
                        // MAX_PLAINTEXT_LENGTH and we loop over the input before hand and check if there is space.
                        bytesWritten = writePlaintextData(src, min(remaining, MAX_PLAINTEXT_LENGTH - bytesConsumed));
                    } else {
                        // OpenSSL's SSL_write keeps state between calls. We should make sure the amount we attempt to
                        // write is guaranteed to succeed so we don't have to worry about keeping state consistent
                        // between calls.
                        final int availableCapacityForWrap = dst.remaining() - bytesProduced - maxWrapOverhead;
                        if (availableCapacityForWrap <= 0) {
                            return new SSLEngineResult(BUFFER_OVERFLOW, getHandshakeStatus(), bytesConsumed,
                                    bytesProduced);
                        }
                        bytesWritten = writePlaintextData(src, min(remaining, availableCapacityForWrap));
                    }

                    // Determine how much encrypted data was generated.
                    //
                    // Even if SSL_write doesn't consume any application data it is possible that OpenSSL will
                    // produce non-application data into the BIO. For example session tickets....
                    // See https://github.com/netty/netty/issues/10041
                    final int pendingNow = SSL.bioLengthByteBuffer(networkBIO);
                    bytesProduced += bioLengthBefore - pendingNow;
                    bioLengthBefore = pendingNow;

                    if (bytesWritten > 0) {
                        bytesConsumed += bytesWritten;

                        if (jdkCompatibilityMode || bytesProduced == dst.remaining()) {
                            return newResultMayFinishHandshake(status, bytesConsumed, bytesProduced);
                        }
                    } else {
                        int sslError = SSL.getError(ssl, bytesWritten);
                        if (sslError == SSL.SSL_ERROR_ZERO_RETURN) {
                            // This means the connection was shutdown correctly, close inbound and outbound
                            if (!receivedShutdown) {
                                closeAll();

                                bytesProduced += bioLengthBefore - SSL.bioLengthByteBuffer(networkBIO);

                                // If we have filled up the dst buffer and we have not finished the handshake we should
                                // try to wrap again. Otherwise we should only try to wrap again if there is still data
                                // pending in SSL buffers.
                                SSLEngineResult.HandshakeStatus hs = mayFinishHandshake(
                                        status != FINISHED ? bytesProduced == dst.remaining() ? NEED_WRAP
                                                : getHandshakeStatus(SSL.bioLengthNonApplication(networkBIO))
                                                : FINISHED);
                                return newResult(hs, bytesConsumed, bytesProduced);
                            }

                            return newResult(NOT_HANDSHAKING, bytesConsumed, bytesProduced);
                        } else if (sslError == SSL.SSL_ERROR_WANT_READ) {
                            // If there is no pending data to read from BIO we should go back to event loop and try
                            // to read more data [1]. It is also possible that event loop will detect the socket has
                            // been closed. [1] https://www.openssl.org/docs/manmaster/ssl/SSL_write.html
                            return newResult(NEED_UNWRAP, bytesConsumed, bytesProduced);
                        } else if (sslError == SSL.SSL_ERROR_WANT_WRITE) {
                            // SSL_ERROR_WANT_WRITE typically means that the underlying transport is not writable
                            // and we should set the "want write" flag on the selector and try again when the
                            // underlying transport is writable [1]. However we are not directly writing to the
                            // underlying transport and instead writing to a BIO buffer. The OpenSsl documentation
                            // says we should do the following [1]:
                            //
                            // "When using a buffering BIO, like a BIO pair, data must be written into or retrieved
                            // out of the BIO before being able to continue."
                            //
                            // In practice this means the destination buffer doesn't have enough space for OpenSSL
                            // to write encrypted data to. This is an OVERFLOW condition.
                            // [1] https://www.openssl.org/docs/manmaster/ssl/SSL_write.html
                            if (bytesProduced > 0) {
                                // If we produced something we should report this back and let the user call
                                // wrap again.
                                return newResult(NEED_WRAP, bytesConsumed, bytesProduced);
                            }
                            return newResult(BUFFER_OVERFLOW, status, bytesConsumed, bytesProduced);
                        } else if (sslError == SSL.SSL_ERROR_WANT_X509_LOOKUP ||
                                sslError == SSL.SSL_ERROR_WANT_CERTIFICATE_VERIFY ||
                                sslError == SSL.SSL_ERROR_WANT_PRIVATE_KEY_OPERATION) {

                            return newResult(NEED_TASK, bytesConsumed, bytesProduced);
                        } else {
                            // Everything else is considered as error
                            throw shutdownWithError("SSL_write", sslError);
                        }
                    }
                }
                return newResultMayFinishHandshake(status, bytesConsumed, bytesProduced);
            } finally {
                SSL.bioClearByteBuffer(networkBIO);
                if (bioReadCopyBuf == null) {
                    dst.position(dst.position() + bytesProduced);
                } else {
                    assert bioReadCopyBuf.readableBytes() <= dst.remaining() : "The destination buffer " + dst +
                            " didn't have enough remaining space to hold the encrypted content in " + bioReadCopyBuf;
                    dst.put(bioReadCopyBuf.internalNioBuffer(bioReadCopyBuf.readerIndex(), bytesProduced));
                    bioReadCopyBuf.release();
                }
            }
        }
    }

    private SSLEngineResult newResult(SSLEngineResult.HandshakeStatus hs, int bytesConsumed, int bytesProduced) {
        return newResult(OK, hs, bytesConsumed, bytesProduced);
    }

    private SSLEngineResult newResult(SSLEngineResult.Status status, SSLEngineResult.HandshakeStatus hs,
                                      int bytesConsumed, int bytesProduced) {
        // If isOutboundDone, then the data from the network BIO
        // was the close_notify message and all was consumed we are not required to wait
        // for the receipt the peer's close_notify message -- shutdown.
        if (isOutboundDone()) {
            if (isInboundDone()) {
                // If the inbound was done as well, we need to ensure we return NOT_HANDSHAKING to signal we are done.
                hs = NOT_HANDSHAKING;

                // As the inbound and the outbound is done we can shutdown the engine now.
                shutdown();
            }
            return new SSLEngineResult(CLOSED, hs, bytesConsumed, bytesProduced);
        }
        if (hs == NEED_TASK) {
            // Set needTask to true so getHandshakeStatus() will return the correct value.
            needTask = true;
        }
        return new SSLEngineResult(status, hs, bytesConsumed, bytesProduced);
    }

    private SSLEngineResult newResultMayFinishHandshake(SSLEngineResult.HandshakeStatus hs,
                                                        int bytesConsumed, int bytesProduced) throws SSLException {
        return newResult(mayFinishHandshake(hs, bytesConsumed, bytesProduced), bytesConsumed, bytesProduced);
    }

    private SSLEngineResult newResultMayFinishHandshake(SSLEngineResult.Status status,
                                                        SSLEngineResult.HandshakeStatus hs,
                                                        int bytesConsumed, int bytesProduced) throws SSLException {
        return newResult(status, mayFinishHandshake(hs, bytesConsumed, bytesProduced), bytesConsumed, bytesProduced);
    }

    /**
     * Log the error, shutdown the engine and throw an exception.
     */
    private SSLException shutdownWithError(String operations, int sslError) {
        return shutdownWithError(operations, sslError, SSL.getLastErrorNumber());
    }

    private SSLException shutdownWithError(String operation, int sslError, int error) {
        String errorString = SSL.getErrorString(error);
        if (logger.isDebugEnabled()) {
            logger.debug("{} failed with {}: OpenSSL error: {} {}",
                         operation, sslError, error, errorString);
        }

        // There was an internal error -- shutdown
        shutdown();
        if (handshakeState == HandshakeState.FINISHED) {
            return new SSLException(errorString);
        }

        SSLHandshakeException exception = new SSLHandshakeException(errorString);
        // If we have a handshakeException stored already we should include it as well to help the user debug things.
        if (pendingException != null) {
            exception.initCause(pendingException);
            pendingException = null;
        }
        return exception;
    }

    private SSLEngineResult handleUnwrapException(int bytesConsumed, int bytesProduced, SSLException e)
            throws SSLException {
        int lastError = SSL.getLastErrorNumber();
        if (lastError != 0) {
            return sslReadErrorResult(SSL.SSL_ERROR_SSL, lastError, bytesConsumed,
                    bytesProduced);
        }
        throw e;
    }

    public final SSLEngineResult unwrap(
            final ByteBuffer[] srcs, int srcsOffset, final int srcsLength,
            final ByteBuffer[] dsts, int dstsOffset, final int dstsLength) throws SSLException {

        // Throw required runtime exceptions
        checkNotNullWithIAE(srcs, "srcs");
        if (srcsOffset >= srcs.length
                || srcsOffset + srcsLength > srcs.length) {
            throw new IndexOutOfBoundsException(
                    "offset: " + srcsOffset + ", length: " + srcsLength +
                            " (expected: offset <= offset + length <= srcs.length (" + srcs.length + "))");
        }
        checkNotNullWithIAE(dsts, "dsts");
        if (dstsOffset >= dsts.length || dstsOffset + dstsLength > dsts.length) {
            throw new IndexOutOfBoundsException(
                    "offset: " + dstsOffset + ", length: " + dstsLength +
                            " (expected: offset <= offset + length <= dsts.length (" + dsts.length + "))");
        }
        long capacity = 0;
        final int dstsEndOffset = dstsOffset + dstsLength;
        for (int i = dstsOffset; i < dstsEndOffset; i ++) {
            ByteBuffer dst = checkNotNullArrayParam(dsts[i], i, "dsts");
            if (dst.isReadOnly()) {
                throw new ReadOnlyBufferException();
            }
            capacity += dst.remaining();
        }

        final int srcsEndOffset = srcsOffset + srcsLength;
        long len = 0;
        for (int i = srcsOffset; i < srcsEndOffset; i++) {
            ByteBuffer src = checkNotNullArrayParam(srcs[i], i, "srcs");
            len += src.remaining();
        }

        synchronized (this) {
            if (isInboundDone()) {
                return isOutboundDone() || isDestroyed() ? CLOSED_NOT_HANDSHAKING : NEED_WRAP_CLOSED;
            }

            SSLEngineResult.HandshakeStatus status = NOT_HANDSHAKING;
            // Prepare OpenSSL to work in server mode and receive handshake
            if (handshakeState != HandshakeState.FINISHED) {
                if (handshakeState != HandshakeState.STARTED_EXPLICITLY) {
                    // Update accepted so we know we triggered the handshake via wrap
                    handshakeState = HandshakeState.STARTED_IMPLICITLY;
                }

                status = handshake();

                if (status == NEED_TASK) {
                    return newResult(status, 0, 0);
                }

                if (status == NEED_WRAP) {
                    return NEED_WRAP_OK;
                }
                // Check if the inbound is considered to be closed if so let us try to wrap again.
                if (isInboundDone) {
                    return NEED_WRAP_CLOSED;
                }
            }

            int sslPending = sslPending0();
            int packetLength;
            // The JDK implies that only a single SSL packet should be processed per unwrap call [1]. If we are in
            // JDK compatibility mode then we should honor this, but if not we just wrap as much as possible. If there
            // are multiple records or partial records this may reduce thrashing events through the pipeline.
            // [1] https://docs.oracle.com/javase/7/docs/api/javax/net/ssl/SSLEngine.html
            if (jdkCompatibilityMode) {
                if (len < SSL_RECORD_HEADER_LENGTH) {
                    return newResultMayFinishHandshake(BUFFER_UNDERFLOW, status, 0, 0);
                }

                packetLength = SslUtils.getEncryptedPacketLength(srcs, srcsOffset);
                if (packetLength == SslUtils.NOT_ENCRYPTED) {
                    throw new NotSslRecordException("not an SSL/TLS record");
                }

                final int packetLengthDataOnly = packetLength - SSL_RECORD_HEADER_LENGTH;
                if (packetLengthDataOnly > capacity) {
                    // Not enough space in the destination buffer so signal the caller that the buffer needs to be
                    // increased.
                    if (packetLengthDataOnly > MAX_RECORD_SIZE) {
                        // The packet length MUST NOT exceed 2^14 [1]. However we do accommodate more data to support
                        // legacy use cases which may violate this condition (e.g. OpenJDK's SslEngineImpl). If the max
                        // length is exceeded we fail fast here to avoid an infinite loop due to the fact that we
                        // won't allocate a buffer large enough.
                        // [1] https://tools.ietf.org/html/rfc5246#section-6.2.1
                        throw new SSLException("Illegal packet length: " + packetLengthDataOnly + " > " +
                                                session.getApplicationBufferSize());
                    } else {
                        session.tryExpandApplicationBufferSize(packetLengthDataOnly);
                    }
                    return newResultMayFinishHandshake(BUFFER_OVERFLOW, status, 0, 0);
                }

                if (len < packetLength) {
                    // We either don't have enough data to read the packet length or not enough for reading the whole
                    // packet.
                    return newResultMayFinishHandshake(BUFFER_UNDERFLOW, status, 0, 0);
                }
            } else if (len == 0 && sslPending <= 0) {
                return newResultMayFinishHandshake(BUFFER_UNDERFLOW, status, 0, 0);
            } else if (capacity == 0) {
                return newResultMayFinishHandshake(BUFFER_OVERFLOW, status, 0, 0);
            } else {
                packetLength = (int) min(MAX_VALUE, len);
            }

            // This must always be the case when we reached here as if not we returned BUFFER_UNDERFLOW.
            assert srcsOffset < srcsEndOffset;

            // This must always be the case if we reached here.
            assert capacity > 0;

            // Number of produced bytes
            int bytesProduced = 0;
            int bytesConsumed = 0;
            try {
                srcLoop:
                for (;;) {
                    ByteBuffer src = srcs[srcsOffset];
                    int remaining = src.remaining();
                    final ByteBuf bioWriteCopyBuf;
                    int pendingEncryptedBytes;
                    if (remaining == 0) {
                        if (sslPending <= 0) {
                            // We must skip empty buffers as BIO_write will return 0 if asked to write something
                            // with length 0.
                            if (++srcsOffset >= srcsEndOffset) {
                                break;
                            }
                            continue;
                        } else {
                            bioWriteCopyBuf = null;
                            pendingEncryptedBytes = SSL.bioLengthByteBuffer(networkBIO);
                        }
                    } else {
                        // Write more encrypted data into the BIO. Ensure we only read one packet at a time as
                        // stated in the SSLEngine javadocs.
                        pendingEncryptedBytes = min(packetLength, remaining);
                        try {
                            bioWriteCopyBuf = writeEncryptedData(src, pendingEncryptedBytes);
                        } catch (SSLException e) {
                            // Ensure we correctly handle the error stack.
                            return handleUnwrapException(bytesConsumed, bytesProduced, e);
                        }
                    }
                    try {
                        for (;;) {
                            ByteBuffer dst = dsts[dstsOffset];
                            if (!dst.hasRemaining()) {
                                // No space left in the destination buffer, skip it.
                                if (++dstsOffset >= dstsEndOffset) {
                                    break srcLoop;
                                }
                                continue;
                            }

                            int bytesRead;
                            try {
                                bytesRead = readPlaintextData(dst);
                            } catch (SSLException e) {
                                // Ensure we correctly handle the error stack.
                                return handleUnwrapException(bytesConsumed, bytesProduced, e);
                            }
                            // We are directly using the ByteBuffer memory for the write, and so we only know what has
                            // been consumed after we let SSL decrypt the data. At this point we should update the
                            // number of bytes consumed, update the ByteBuffer position, and release temp ByteBuf.
                            int localBytesConsumed = pendingEncryptedBytes - SSL.bioLengthByteBuffer(networkBIO);
                            bytesConsumed += localBytesConsumed;
                            packetLength -= localBytesConsumed;
                            pendingEncryptedBytes -= localBytesConsumed;
                            src.position(src.position() + localBytesConsumed);

                            if (bytesRead > 0) {
                                bytesProduced += bytesRead;

                                if (!dst.hasRemaining()) {
                                    sslPending = sslPending0();
                                    // Move to the next dst buffer as this one is full.
                                    if (++dstsOffset >= dstsEndOffset) {
                                        return sslPending > 0 ?
                                                newResult(BUFFER_OVERFLOW, status, bytesConsumed, bytesProduced) :
                                                newResultMayFinishHandshake(isInboundDone() ? CLOSED : OK, status,
                                                        bytesConsumed, bytesProduced);
                                    }
                                } else if (packetLength == 0 || jdkCompatibilityMode) {
                                    // We either consumed all data or we are in jdkCompatibilityMode and have consumed
                                    // a single TLS packet and should stop consuming until this method is called again.
                                    break srcLoop;
                                }
                            } else {
                                int sslError = SSL.getError(ssl, bytesRead);
                                if (sslError == SSL.SSL_ERROR_WANT_READ || sslError == SSL.SSL_ERROR_WANT_WRITE) {
                                    // break to the outer loop as we want to read more data which means we need to
                                    // write more to the BIO.
                                    break;
                                } else if (sslError == SSL.SSL_ERROR_ZERO_RETURN) {
                                    // This means the connection was shutdown correctly, close inbound and outbound
                                    if (!receivedShutdown) {
                                        closeAll();
                                    }
                                    return newResultMayFinishHandshake(isInboundDone() ? CLOSED : OK, status,
                                            bytesConsumed, bytesProduced);
                                } else if (sslError == SSL.SSL_ERROR_WANT_X509_LOOKUP ||
                                        sslError == SSL.SSL_ERROR_WANT_CERTIFICATE_VERIFY ||
                                        sslError == SSL.SSL_ERROR_WANT_PRIVATE_KEY_OPERATION) {
                                    return newResult(isInboundDone() ? CLOSED : OK,
                                            NEED_TASK, bytesConsumed, bytesProduced);
                                } else {
                                    return sslReadErrorResult(sslError, SSL.getLastErrorNumber(), bytesConsumed,
                                            bytesProduced);
                                }
                            }
                        }

                        if (++srcsOffset >= srcsEndOffset) {
                            break;
                        }
                    } finally {
                        if (bioWriteCopyBuf != null) {
                            bioWriteCopyBuf.release();
                        }
                    }
                }
            } finally {
                SSL.bioClearByteBuffer(networkBIO);
                rejectRemoteInitiatedRenegotiation();
            }

            // Check to see if we received a close_notify message from the peer.
            if (!receivedShutdown && (SSL.getShutdown(ssl) & SSL.SSL_RECEIVED_SHUTDOWN) == SSL.SSL_RECEIVED_SHUTDOWN) {
                closeAll();
            }

            return newResultMayFinishHandshake(isInboundDone() ? CLOSED : OK, status, bytesConsumed, bytesProduced);
        }
    }

    private boolean needWrapAgain(int stackError) {
        // Check if we have a pending handshakeException and if so see if we need to consume all pending data from the
        // BIO first or can just shutdown and throw it now.
        // This is needed so we ensure close_notify etc is correctly send to the remote peer.
        // See https://github.com/netty/netty/issues/3900
        if (SSL.bioLengthNonApplication(networkBIO) > 0) {
            // we seems to have data left that needs to be transferred and so the user needs
            // call wrap(...). Store the error so we can pick it up later.
            String message = SSL.getErrorString(stackError);
            SSLException exception = handshakeState == HandshakeState.FINISHED ?
                    new SSLException(message) : new SSLHandshakeException(message);
            if (pendingException == null) {
                pendingException = exception;
            } else {
                ThrowableUtil.addSuppressed(pendingException, exception);
            }
            // We need to clear all errors so we not pick up anything that was left on the stack on the next
            // operation. Note that shutdownWithError(...) will cleanup the stack as well so its only needed here.
            SSL.clearError();
            return true;
        }
        return false;
    }

    private SSLEngineResult sslReadErrorResult(int error, int stackError, int bytesConsumed, int bytesProduced)
            throws SSLException {
        if (needWrapAgain(stackError)) {
            // There is something that needs to be send to the remote peer before we can teardown.
            // This is most likely some alert.
            return new SSLEngineResult(OK, NEED_WRAP, bytesConsumed, bytesProduced);
        }
        throw shutdownWithError("SSL_read", error, stackError);
    }

    private void closeAll() throws SSLException {
        receivedShutdown = true;
        closeOutbound();
        closeInbound();
    }

    private void rejectRemoteInitiatedRenegotiation() throws SSLHandshakeException {
        // As rejectRemoteInitiatedRenegotiation() is called in a finally block we also need to check if we shutdown
        // the engine before as otherwise SSL.getHandshakeCount(ssl) will throw an NPE if the passed in ssl is 0.
        // See https://github.com/netty/netty/issues/7353
        if (!isDestroyed() && SSL.getHandshakeCount(ssl) > 1 &&
            // As we may count multiple handshakes when TLSv1.3 is used we should just ignore this here as
            // renegotiation is not supported in TLSv1.3 as per spec.
            !SslProtocols.TLS_v1_3.equals(session.getProtocol()) && handshakeState == HandshakeState.FINISHED) {
            // TODO: In future versions me may also want to send a fatal_alert to the client and so notify it
            // that the renegotiation failed.
            shutdown();
            throw new SSLHandshakeException("remote-initiated renegotiation not allowed");
        }
    }

    public final SSLEngineResult unwrap(final ByteBuffer[] srcs, final ByteBuffer[] dsts) throws SSLException {
        return unwrap(srcs, 0, srcs.length, dsts, 0, dsts.length);
    }

    private ByteBuffer[] singleSrcBuffer(ByteBuffer src) {
        singleSrcBuffer[0] = src;
        return singleSrcBuffer;
    }

    private void resetSingleSrcBuffer() {
        singleSrcBuffer[0] = null;
    }

    private ByteBuffer[] singleDstBuffer(ByteBuffer src) {
        singleDstBuffer[0] = src;
        return singleDstBuffer;
    }

    private void resetSingleDstBuffer() {
        singleDstBuffer[0] = null;
    }

    @Override
    public final synchronized SSLEngineResult unwrap(
            final ByteBuffer src, final ByteBuffer[] dsts, final int offset, final int length) throws SSLException {
        try {
            return unwrap(singleSrcBuffer(src), 0, 1, dsts, offset, length);
        } finally {
            resetSingleSrcBuffer();
        }
    }

    @Override
    public final synchronized SSLEngineResult wrap(ByteBuffer src, ByteBuffer dst) throws SSLException {
        try {
            return wrap(singleSrcBuffer(src), dst);
        } finally {
            resetSingleSrcBuffer();
        }
    }

    @Override
    public final synchronized SSLEngineResult unwrap(ByteBuffer src, ByteBuffer dst) throws SSLException {
        try {
            return unwrap(singleSrcBuffer(src), singleDstBuffer(dst));
        } finally {
            resetSingleSrcBuffer();
            resetSingleDstBuffer();
        }
    }

    @Override
    public final synchronized SSLEngineResult unwrap(ByteBuffer src, ByteBuffer[] dsts) throws SSLException {
        try {
            return unwrap(singleSrcBuffer(src), dsts);
        } finally {
            resetSingleSrcBuffer();
        }
    }

    private class TaskDecorator<R extends Runnable> implements Runnable {
        protected final R task;
        TaskDecorator(R task) {
            this.task = task;
        }

        @Override
        public void run() {
            if (isDestroyed()) {
                // The engine was destroyed in the meantime, just return.
                return;
            }
            try {
                task.run();
            } finally {
                // The task was run, reset needTask to false so getHandshakeStatus() returns the correct value.
                needTask = false;
            }
        }
    }

    private final class AsyncTaskDecorator extends TaskDecorator<AsyncTask> implements AsyncRunnable {
        AsyncTaskDecorator(AsyncTask task) {
            super(task);
        }

        @Override
        public void run(Runnable runnable) {
            if (isDestroyed()) {
                // The engine was destroyed in the meantime, just return.
                runnable.run();
                return;
            }
            try {
                task.runAsync(runnable);
            } finally {
                // The task was run, reset needTask to false so getHandshakeStatus() returns the correct value.
                needTask = false;
            }
        }
    }

    @Override
    public final synchronized Runnable getDelegatedTask() {
        if (isDestroyed()) {
            return null;
        }
        final Runnable task = SSL.getTask(ssl);
        if (task == null) {
            return null;
        }
        if (task instanceof AsyncTask) {
            return new AsyncTaskDecorator((AsyncTask) task);
        }
        return new TaskDecorator<Runnable>(task);
    }

    @Override
    public final synchronized void closeInbound() throws SSLException {
        if (isInboundDone) {
            return;
        }

        isInboundDone = true;

        if (isOutboundDone()) {
            // Only call shutdown if there is no outbound data pending.
            // See https://github.com/netty/netty/issues/6167
            shutdown();
        }

        if (handshakeState != HandshakeState.NOT_STARTED && !receivedShutdown) {
            throw new SSLException(
                    "Inbound closed before receiving peer's close_notify: possible truncation attack?");
        }
    }

    @Override
    public final synchronized boolean isInboundDone() {
        return isInboundDone;
    }

    @Override
    public final synchronized void closeOutbound() {
        if (outboundClosed) {
            return;
        }

        outboundClosed = true;

        if (handshakeState != HandshakeState.NOT_STARTED && !isDestroyed()) {
            int mode = SSL.getShutdown(ssl);
            if ((mode & SSL.SSL_SENT_SHUTDOWN) != SSL.SSL_SENT_SHUTDOWN) {
                doSSLShutdown();
            }
        } else {
            // engine closing before initial handshake
            shutdown();
        }
    }

    /**
     * Attempt to call {@link SSL#shutdownSSL(long)}.
     * @return {@code false} if the call to {@link SSL#shutdownSSL(long)} was not attempted or returned an error.
     */
    private boolean doSSLShutdown() {
        if (SSL.isInInit(ssl) != 0) {
            // Only try to call SSL_shutdown if we are not in the init state anymore.
            // Otherwise we will see 'error:140E0197:SSL routines:SSL_shutdown:shutdown while in init' in our logs.
            //
            // See also https://hg.nginx.org/nginx/rev/062c189fee20
            return false;
        }
        int err = SSL.shutdownSSL(ssl);
        if (err < 0) {
            int sslErr = SSL.getError(ssl, err);
            if (sslErr == SSL.SSL_ERROR_SYSCALL || sslErr == SSL.SSL_ERROR_SSL) {
                if (logger.isDebugEnabled()) {
                    int error = SSL.getLastErrorNumber();
                    logger.debug("SSL_shutdown failed: OpenSSL error: {} {}", error, SSL.getErrorString(error));
                }
                // There was an internal error -- shutdown
                shutdown();
                return false;
            }
            SSL.clearError();
        }
        return true;
    }

    @Override
    public final synchronized boolean isOutboundDone() {
        // Check if there is anything left in the outbound buffer.
        // We need to ensure we only call SSL.pendingWrittenBytesInBIO(...) if the engine was not destroyed yet.
        return outboundClosed && (networkBIO == 0 || SSL.bioLengthNonApplication(networkBIO) == 0);
    }

    @Override
    public final String[] getSupportedCipherSuites() {
        return OpenSsl.AVAILABLE_CIPHER_SUITES.toArray(new String[0]);
    }

    @Override
    public final String[] getEnabledCipherSuites() {
        final String[] extraCiphers;
        final String[] enabled;
        final boolean tls13Enabled;
        synchronized (this) {
            if (!isDestroyed()) {
                enabled = SSL.getCiphers(ssl);
                int opts = SSL.getOptions(ssl);
                if (isProtocolEnabled(opts, SSL.SSL_OP_NO_TLSv1_3, SslProtocols.TLS_v1_3)) {
                    extraCiphers = OpenSsl.EXTRA_SUPPORTED_TLS_1_3_CIPHERS;
                    tls13Enabled = true;
                } else {
                    extraCiphers = EmptyArrays.EMPTY_STRINGS;
                    tls13Enabled = false;
                }
            } else {
                return EmptyArrays.EMPTY_STRINGS;
            }
        }
        if (enabled == null) {
            return EmptyArrays.EMPTY_STRINGS;
        } else {
            Set<String> enabledSet = new LinkedHashSet<String>(enabled.length + extraCiphers.length);
            synchronized (this) {
                for (int i = 0; i < enabled.length; i++) {
                    String mapped = toJavaCipherSuite(enabled[i]);
                    final String cipher = mapped == null ? enabled[i] : mapped;
                    if ((!tls13Enabled || !OpenSsl.isTlsv13Supported()) && SslUtils.isTLSv13Cipher(cipher)) {
                        continue;
                    }
                    enabledSet.add(cipher);
                }
                Collections.addAll(enabledSet, extraCiphers);
            }
            return enabledSet.toArray(new String[0]);
        }
    }

    @Override
    public final void setEnabledCipherSuites(String[] cipherSuites) {
        checkNotNull(cipherSuites, "cipherSuites");

        final StringBuilder buf = new StringBuilder();
        final StringBuilder bufTLSv13 = new StringBuilder();

        CipherSuiteConverter.convertToCipherStrings(Arrays.asList(cipherSuites), buf, bufTLSv13, OpenSsl.isBoringSSL());
        final String cipherSuiteSpec = buf.toString();
        final String cipherSuiteSpecTLSv13 = bufTLSv13.toString();

        if (!OpenSsl.isTlsv13Supported() && !cipherSuiteSpecTLSv13.isEmpty()) {
            throw new IllegalArgumentException("TLSv1.3 is not supported by this java version.");
        }
        synchronized (this) {
            if (!isDestroyed()) {
                try {
                    // Set non TLSv1.3 ciphers.
                    SSL.setCipherSuites(ssl, cipherSuiteSpec, false);
                    if (OpenSsl.isTlsv13Supported()) {
                        // Set TLSv1.3 ciphers.
                        SSL.setCipherSuites(ssl, OpenSsl.checkTls13Ciphers(logger, cipherSuiteSpecTLSv13), true);
                    }

                    // We also need to update the enabled protocols to ensure we disable the protocol if there are
                    // no compatible ciphers left.
                    Set<String> protocols = new HashSet<String>(explicitlyEnabledProtocols.length);
                    Collections.addAll(protocols, explicitlyEnabledProtocols);

                    // We have no ciphers that are compatible with none-TLSv1.3, let us explicit disable all other
                    // protocols.
                    if (cipherSuiteSpec.isEmpty()) {
                        protocols.remove(SslProtocols.TLS_v1);
                        protocols.remove(SslProtocols.TLS_v1_1);
                        protocols.remove(SslProtocols.TLS_v1_2);
                        protocols.remove(SslProtocols.SSL_v3);
                        protocols.remove(SslProtocols.SSL_v2);
                        protocols.remove(SslProtocols.SSL_v2_HELLO);
                    }
                    // We have no ciphers that are compatible with TLSv1.3, let us explicit disable it.
                    if (cipherSuiteSpecTLSv13.isEmpty()) {
                        protocols.remove(SslProtocols.TLS_v1_3);
                    }
                    // Update the protocols but not cache the value. We only cache when we call it from the user
                    // code or when we construct the engine.
                    setEnabledProtocols0(protocols.toArray(EmptyArrays.EMPTY_STRINGS), false);
                } catch (Exception e) {
                    throw new IllegalStateException("failed to enable cipher suites: " + cipherSuiteSpec, e);
                }
            } else {
                throw new IllegalStateException("failed to enable cipher suites: " + cipherSuiteSpec);
            }
        }
    }

    @Override
    public final String[] getSupportedProtocols() {
        return OpenSsl.SUPPORTED_PROTOCOLS_SET.toArray(new String[0]);
    }

    @Override
    public final String[] getEnabledProtocols() {
        List<String> enabled = new ArrayList<String>(6);
        // Seems like there is no way to explicit disable SSLv2Hello in openssl so it is always enabled
        enabled.add(SslProtocols.SSL_v2_HELLO);

        int opts;
        synchronized (this) {
            if (!isDestroyed()) {
                opts = SSL.getOptions(ssl);
            } else {
                return enabled.toArray(new String[0]);
            }
        }
        if (isProtocolEnabled(opts, SSL.SSL_OP_NO_TLSv1, SslProtocols.TLS_v1)) {
            enabled.add(SslProtocols.TLS_v1);
        }
        if (isProtocolEnabled(opts, SSL.SSL_OP_NO_TLSv1_1, SslProtocols.TLS_v1_1)) {
            enabled.add(SslProtocols.TLS_v1_1);
        }
        if (isProtocolEnabled(opts, SSL.SSL_OP_NO_TLSv1_2, SslProtocols.TLS_v1_2)) {
            enabled.add(SslProtocols.TLS_v1_2);
        }
        if (isProtocolEnabled(opts, SSL.SSL_OP_NO_TLSv1_3, SslProtocols.TLS_v1_3)) {
            enabled.add(SslProtocols.TLS_v1_3);
        }
        if (isProtocolEnabled(opts, SSL.SSL_OP_NO_SSLv2, SslProtocols.SSL_v2)) {
            enabled.add(SslProtocols.SSL_v2);
        }
        if (isProtocolEnabled(opts, SSL.SSL_OP_NO_SSLv3, SslProtocols.SSL_v3)) {
            enabled.add(SslProtocols.SSL_v3);
        }
        return enabled.toArray(new String[0]);
    }

    private static boolean isProtocolEnabled(int opts, int disableMask, String protocolString) {
        // We also need to check if the actual protocolString is supported as depending on the openssl API
        // implementations it may use a disableMask of 0 (BoringSSL is doing this for example).
        return (opts & disableMask) == 0 && OpenSsl.SUPPORTED_PROTOCOLS_SET.contains(protocolString);
    }

    /**
     * {@inheritDoc}
     * TLS doesn't support a way to advertise non-contiguous versions from the client's perspective, and the client
     * just advertises the max supported version. The TLS protocol also doesn't support all different combinations of
     * discrete protocols, and instead assumes contiguous ranges. OpenSSL has some unexpected behavior
     * (e.g. handshake failures) if non-contiguous protocols are used even where there is a compatible set of protocols
     * and ciphers. For these reasons this method will determine the minimum protocol and the maximum protocol and
     * enabled a contiguous range from [min protocol, max protocol] in OpenSSL.
     */
    @Override
    public final void setEnabledProtocols(String[] protocols) {
        setEnabledProtocols0(protocols, true);
    }

    private void setEnabledProtocols0(String[] protocols, boolean cache) {
        // This is correct from the API docs
        checkNotNullWithIAE(protocols, "protocols");
        int minProtocolIndex = OPENSSL_OP_NO_PROTOCOLS.length;
        int maxProtocolIndex = 0;
        for (String p: protocols) {
            if (!OpenSsl.SUPPORTED_PROTOCOLS_SET.contains(p)) {
                throw new IllegalArgumentException("Protocol " + p + " is not supported.");
            }
            if (p.equals(SslProtocols.SSL_v2)) {
                if (minProtocolIndex > OPENSSL_OP_NO_PROTOCOL_INDEX_SSLV2) {
                    minProtocolIndex = OPENSSL_OP_NO_PROTOCOL_INDEX_SSLV2;
                }
                if (maxProtocolIndex < OPENSSL_OP_NO_PROTOCOL_INDEX_SSLV2) { // lgtm[java/constant-comparison]
                    maxProtocolIndex = OPENSSL_OP_NO_PROTOCOL_INDEX_SSLV2;
                }
            } else if (p.equals(SslProtocols.SSL_v3)) {
                if (minProtocolIndex > OPENSSL_OP_NO_PROTOCOL_INDEX_SSLV3) {
                    minProtocolIndex = OPENSSL_OP_NO_PROTOCOL_INDEX_SSLV3;
                }
                if (maxProtocolIndex < OPENSSL_OP_NO_PROTOCOL_INDEX_SSLV3) {
                    maxProtocolIndex = OPENSSL_OP_NO_PROTOCOL_INDEX_SSLV3;
                }
            } else if (p.equals(SslProtocols.TLS_v1)) {
                if (minProtocolIndex > OPENSSL_OP_NO_PROTOCOL_INDEX_TLSv1) {
                    minProtocolIndex = OPENSSL_OP_NO_PROTOCOL_INDEX_TLSv1;
                }
                if (maxProtocolIndex < OPENSSL_OP_NO_PROTOCOL_INDEX_TLSv1) {
                    maxProtocolIndex = OPENSSL_OP_NO_PROTOCOL_INDEX_TLSv1;
                }
            } else if (p.equals(SslProtocols.TLS_v1_1)) {
                if (minProtocolIndex > OPENSSL_OP_NO_PROTOCOL_INDEX_TLSv1_1) {
                    minProtocolIndex = OPENSSL_OP_NO_PROTOCOL_INDEX_TLSv1_1;
                }
                if (maxProtocolIndex < OPENSSL_OP_NO_PROTOCOL_INDEX_TLSv1_1) {
                    maxProtocolIndex = OPENSSL_OP_NO_PROTOCOL_INDEX_TLSv1_1;
                }
            } else if (p.equals(SslProtocols.TLS_v1_2)) {
                if (minProtocolIndex > OPENSSL_OP_NO_PROTOCOL_INDEX_TLSv1_2) {
                    minProtocolIndex = OPENSSL_OP_NO_PROTOCOL_INDEX_TLSv1_2;
                }
                if (maxProtocolIndex < OPENSSL_OP_NO_PROTOCOL_INDEX_TLSv1_2) {
                    maxProtocolIndex = OPENSSL_OP_NO_PROTOCOL_INDEX_TLSv1_2;
                }
            } else if (p.equals(SslProtocols.TLS_v1_3)) {
                if (minProtocolIndex > OPENSSL_OP_NO_PROTOCOL_INDEX_TLSv1_3) {
                    minProtocolIndex = OPENSSL_OP_NO_PROTOCOL_INDEX_TLSv1_3;
                }
                if (maxProtocolIndex < OPENSSL_OP_NO_PROTOCOL_INDEX_TLSv1_3) {
                    maxProtocolIndex = OPENSSL_OP_NO_PROTOCOL_INDEX_TLSv1_3;
                }
            }
        }
        synchronized (this) {
            if (cache) {
                this.explicitlyEnabledProtocols = protocols;
            }
            if (!isDestroyed()) {
                // Clear out options which disable protocols
                SSL.clearOptions(ssl, SSL.SSL_OP_NO_SSLv2 | SSL.SSL_OP_NO_SSLv3 | SSL.SSL_OP_NO_TLSv1 |
                                      SSL.SSL_OP_NO_TLSv1_1 | SSL.SSL_OP_NO_TLSv1_2 | SSL.SSL_OP_NO_TLSv1_3);

                int opts = 0;
                for (int i = 0; i < minProtocolIndex; ++i) {
                    opts |= OPENSSL_OP_NO_PROTOCOLS[i];
                }
                assert maxProtocolIndex != MAX_VALUE;
                for (int i = maxProtocolIndex + 1; i < OPENSSL_OP_NO_PROTOCOLS.length; ++i) {
                    opts |= OPENSSL_OP_NO_PROTOCOLS[i];
                }

                // Disable protocols we do not want
                SSL.setOptions(ssl, opts);
            } else {
                throw new IllegalStateException("failed to enable protocols: " + Arrays.asList(protocols));
            }
        }
    }

    @Override
    public final SSLSession getSession() {
        return session;
    }

    @Override
    public final synchronized void beginHandshake() throws SSLException {
        switch (handshakeState) {
            case STARTED_IMPLICITLY:
                checkEngineClosed();

                // A user did not start handshake by calling this method by him/herself,
                // but handshake has been started already by wrap() or unwrap() implicitly.
                // Because it's the user's first time to call this method, it is unfair to
                // raise an exception.  From the user's standpoint, he or she never asked
                // for renegotiation.

                handshakeState = HandshakeState.STARTED_EXPLICITLY; // Next time this method is invoked by the user,
                calculateMaxWrapOverhead();
                // we should raise an exception.
                break;
            case STARTED_EXPLICITLY:
                // Nothing to do as the handshake is not done yet.
                break;
            case FINISHED:
                throw new SSLException("renegotiation unsupported");
            case NOT_STARTED:
                handshakeState = HandshakeState.STARTED_EXPLICITLY;
                if (handshake() == NEED_TASK) {
                    // Set needTask to true so getHandshakeStatus() will return the correct value.
                    needTask = true;
                }
                calculateMaxWrapOverhead();
                break;
            default:
                throw new Error();
        }
    }

    private void checkEngineClosed() throws SSLException {
        if (isDestroyed()) {
            throw new SSLException("engine closed");
        }
    }

    private static SSLEngineResult.HandshakeStatus pendingStatus(int pendingStatus) {
        // Depending on if there is something left in the BIO we need to WRAP or UNWRAP
        return pendingStatus > 0 ? NEED_WRAP : NEED_UNWRAP;
    }

    private static boolean isEmpty(Object[] arr) {
        return arr == null || arr.length == 0;
    }

    private static boolean isEmpty(byte[] cert) {
        return cert == null || cert.length == 0;
    }

    private SSLEngineResult.HandshakeStatus handshakeException() throws SSLException {
        if (SSL.bioLengthNonApplication(networkBIO) > 0) {
            // There is something pending, we need to consume it first via a WRAP so we don't loose anything.
            return NEED_WRAP;
        }

        Throwable exception = pendingException;
        assert exception != null;
        pendingException = null;
        shutdown();
        if (exception instanceof SSLHandshakeException) {
            throw (SSLHandshakeException) exception;
        }
        SSLHandshakeException e = new SSLHandshakeException("General OpenSslEngine problem");
        e.initCause(exception);
        throw e;
    }

    /**
     * Should be called if the handshake will be failed due a callback that throws an exception.
     * This cause will then be used to give more details as part of the {@link SSLHandshakeException}.
     */
    final void initHandshakeException(Throwable cause) {
        if (pendingException == null) {
            pendingException = cause;
        } else {
            ThrowableUtil.addSuppressed(pendingException, cause);
        }
    }

    private SSLEngineResult.HandshakeStatus handshake() throws SSLException {
        if (needTask) {
            return NEED_TASK;
        }
        if (handshakeState == HandshakeState.FINISHED) {
            return FINISHED;
        }

        checkEngineClosed();

        if (pendingException != null) {
            // Let's call SSL.doHandshake(...) again in case there is some async operation pending that would fill the
            // outbound buffer.
            if (SSL.doHandshake(ssl) <= 0) {
                // Clear any error that was put on the stack by the handshake
                SSL.clearError();
            }
            return handshakeException();
        }

        // Adding the OpenSslEngine to the OpenSslEngineMap so it can be used in the AbstractCertificateVerifier.
        engineMap.add(this);

        if (!sessionSet) {
            parentContext.sessionContext().setSessionFromCache(getPeerHost(), getPeerPort(), ssl);
            sessionSet = true;
        }

        if (lastAccessed == -1) {
            lastAccessed = System.currentTimeMillis();
        }

        int code = SSL.doHandshake(ssl);
        if (code <= 0) {
            int sslError = SSL.getError(ssl, code);
            if (sslError == SSL.SSL_ERROR_WANT_READ || sslError == SSL.SSL_ERROR_WANT_WRITE) {
                return pendingStatus(SSL.bioLengthNonApplication(networkBIO));
            }

            if (sslError == SSL.SSL_ERROR_WANT_X509_LOOKUP ||
                    sslError == SSL.SSL_ERROR_WANT_CERTIFICATE_VERIFY ||
                    sslError == SSL.SSL_ERROR_WANT_PRIVATE_KEY_OPERATION) {
                return NEED_TASK;
            }

            if (needWrapAgain(SSL.getLastErrorNumber())) {
                // There is something that needs to be send to the remote peer before we can teardown.
                // This is most likely some alert.
                return NEED_WRAP;
            }
            // Check if we have a pending exception that was created during the handshake and if so throw it after
            // shutdown the connection.
            if (pendingException != null) {
                return handshakeException();
            }

            // Everything else is considered as error
            throw shutdownWithError("SSL_do_handshake", sslError);
        }
        // We have produced more data as part of the handshake if this is the case the user should call wrap(...)
        if (SSL.bioLengthNonApplication(networkBIO) > 0) {
            return NEED_WRAP;
        }
        // if SSL_do_handshake returns > 0 or sslError == SSL.SSL_ERROR_NAME it means the handshake was finished.
        session.handshakeFinished(SSL.getSessionId(ssl), SSL.getCipherForSSL(ssl), SSL.getVersion(ssl),
                SSL.getPeerCertificate(ssl), SSL.getPeerCertChain(ssl),
                SSL.getTime(ssl) * 1000L, parentContext.sessionTimeout() * 1000L);
        selectApplicationProtocol();
        return FINISHED;
    }

    private SSLEngineResult.HandshakeStatus mayFinishHandshake(
            SSLEngineResult.HandshakeStatus hs, int bytesConsumed, int bytesProduced) throws SSLException {
        return hs == NEED_UNWRAP && bytesProduced > 0 || hs == NEED_WRAP && bytesConsumed > 0 ?
            handshake() : mayFinishHandshake(hs != FINISHED ? getHandshakeStatus() : FINISHED);
    }

    private SSLEngineResult.HandshakeStatus mayFinishHandshake(SSLEngineResult.HandshakeStatus status)
            throws SSLException {
        if (status == NOT_HANDSHAKING) {
            if (handshakeState != HandshakeState.FINISHED) {
                // If the status was NOT_HANDSHAKING and we not finished the handshake we need to call
                // SSL_do_handshake() again
                return handshake();
            }
            if (!isDestroyed() && SSL.bioLengthNonApplication(networkBIO) > 0) {
                // We have something left that needs to be wrapped.
                return NEED_WRAP;
            }
        }
        return status;
    }

    @Override
    public final synchronized SSLEngineResult.HandshakeStatus getHandshakeStatus() {
        // Check if we are in the initial handshake phase or shutdown phase
        if (needPendingStatus()) {
            if (needTask) {
                // There is a task outstanding
                return NEED_TASK;
            }
            return pendingStatus(SSL.bioLengthNonApplication(networkBIO));
        }
        return NOT_HANDSHAKING;
    }

    private SSLEngineResult.HandshakeStatus getHandshakeStatus(int pending) {
        // Check if we are in the initial handshake phase or shutdown phase
        if (needPendingStatus()) {
            if (needTask) {
                // There is a task outstanding
                return NEED_TASK;
            }
            return pendingStatus(pending);
        }
        return NOT_HANDSHAKING;
    }

    private boolean needPendingStatus() {
        return handshakeState != HandshakeState.NOT_STARTED && !isDestroyed()
                && (handshakeState != HandshakeState.FINISHED || isInboundDone() || isOutboundDone());
    }

    /**
     * Converts the specified OpenSSL cipher suite to the Java cipher suite.
     */
    private String toJavaCipherSuite(String openSslCipherSuite) {
        if (openSslCipherSuite == null) {
            return null;
        }

        String version = SSL.getVersion(ssl);
        String prefix = toJavaCipherSuitePrefix(version);
        return CipherSuiteConverter.toJava(openSslCipherSuite, prefix);
    }

    /**
     * Converts the protocol version string returned by {@link SSL#getVersion(long)} to protocol family string.
     */
    private static String toJavaCipherSuitePrefix(String protocolVersion) {
        final char c;
        if (protocolVersion == null || protocolVersion.isEmpty()) {
            c = 0;
        } else {
            c = protocolVersion.charAt(0);
        }

        switch (c) {
            case 'T':
                return "TLS";
            case 'S':
                return "SSL";
            default:
                return "UNKNOWN";
        }
    }

    @Override
    public final void setUseClientMode(boolean clientMode) {
        if (clientMode != this.clientMode) {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public final boolean getUseClientMode() {
        return clientMode;
    }

    @Override
    public final void setNeedClientAuth(boolean b) {
        setClientAuth(b ? ClientAuth.REQUIRE : ClientAuth.NONE);
    }

    @Override
    public final boolean getNeedClientAuth() {
        return clientAuth == ClientAuth.REQUIRE;
    }

    @Override
    public final void setWantClientAuth(boolean b) {
        setClientAuth(b ? ClientAuth.OPTIONAL : ClientAuth.NONE);
    }

    @Override
    public final boolean getWantClientAuth() {
        return clientAuth == ClientAuth.OPTIONAL;
    }

    /**
     * See <a href="https://www.openssl.org/docs/man1.0.2/ssl/SSL_set_verify.html">SSL_set_verify</a> and
     * {@link SSL#setVerify(long, int, int)}.
     */
    @UnstableApi
    public final synchronized void setVerify(int verifyMode, int depth) {
        if (!isDestroyed()) {
            SSL.setVerify(ssl, verifyMode, depth);
        }
    }

    private void setClientAuth(ClientAuth mode) {
        if (clientMode) {
            return;
        }
        synchronized (this) {
            if (clientAuth == mode) {
                // No need to issue any JNI calls if the mode is the same
                return;
            }
            if (!isDestroyed()) {
                switch (mode) {
                    case NONE:
                        SSL.setVerify(ssl, SSL.SSL_CVERIFY_NONE, ReferenceCountedOpenSslContext.VERIFY_DEPTH);
                        break;
                    case REQUIRE:
                        SSL.setVerify(ssl, SSL.SSL_CVERIFY_REQUIRED, ReferenceCountedOpenSslContext.VERIFY_DEPTH);
                        break;
                    case OPTIONAL:
                        SSL.setVerify(ssl, SSL.SSL_CVERIFY_OPTIONAL, ReferenceCountedOpenSslContext.VERIFY_DEPTH);
                        break;
                    default:
                        throw new Error(mode.toString());
                }
            }
            clientAuth = mode;
        }
    }

    @Override
    public final void setEnableSessionCreation(boolean b) {
        if (b) {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public final boolean getEnableSessionCreation() {
        return false;
    }

    @SuppressJava6Requirement(reason = "Usage guarded by java version check")
    @Override
    public final synchronized SSLParameters getSSLParameters() {
        SSLParameters sslParameters = super.getSSLParameters();

        int version = PlatformDependent.javaVersion();
        if (version >= 7) {
            sslParameters.setEndpointIdentificationAlgorithm(endPointIdentificationAlgorithm);
            Java7SslParametersUtils.setAlgorithmConstraints(sslParameters, algorithmConstraints);
            if (version >= 8) {
                if (sniHostNames != null) {
                    Java8SslUtils.setSniHostNames(sslParameters, sniHostNames);
                }
                if (!isDestroyed()) {
                    Java8SslUtils.setUseCipherSuitesOrder(
                            sslParameters, (SSL.getOptions(ssl) & SSL.SSL_OP_CIPHER_SERVER_PREFERENCE) != 0);
                }

                Java8SslUtils.setSNIMatchers(sslParameters, matchers);
            }
        }
        return sslParameters;
    }

    @SuppressJava6Requirement(reason = "Usage guarded by java version check")
    @Override
    public final synchronized void setSSLParameters(SSLParameters sslParameters) {
        int version = PlatformDependent.javaVersion();
        if (version >= 7) {
            if (sslParameters.getAlgorithmConstraints() != null) {
                throw new IllegalArgumentException("AlgorithmConstraints are not supported.");
            }

            boolean isDestroyed = isDestroyed();
            if (version >= 8) {
                if (!isDestroyed) {
                    if (clientMode) {
                        final List<String> sniHostNames = Java8SslUtils.getSniHostNames(sslParameters);
                        for (String name: sniHostNames) {
                            SSL.setTlsExtHostName(ssl, name);
                        }
                        this.sniHostNames = sniHostNames;
                    }
                    if (Java8SslUtils.getUseCipherSuitesOrder(sslParameters)) {
                        SSL.setOptions(ssl, SSL.SSL_OP_CIPHER_SERVER_PREFERENCE);
                    } else {
                        SSL.clearOptions(ssl, SSL.SSL_OP_CIPHER_SERVER_PREFERENCE);
                    }
                }
                matchers = sslParameters.getSNIMatchers();
            }

            final String endPointIdentificationAlgorithm = sslParameters.getEndpointIdentificationAlgorithm();
            if (!isDestroyed) {
                // If the user asks for hostname verification we must ensure we verify the peer.
                // If the user disables hostname verification we leave it up to the user to change the mode manually.
                if (clientMode && isEndPointVerificationEnabled(endPointIdentificationAlgorithm)) {
                    SSL.setVerify(ssl, SSL.SSL_CVERIFY_REQUIRED, -1);
                }
            }
            this.endPointIdentificationAlgorithm = endPointIdentificationAlgorithm;
            algorithmConstraints = sslParameters.getAlgorithmConstraints();
        }
        super.setSSLParameters(sslParameters);
    }

    private static boolean isEndPointVerificationEnabled(String endPointIdentificationAlgorithm) {
        return endPointIdentificationAlgorithm != null && !endPointIdentificationAlgorithm.isEmpty();
    }

    private boolean isDestroyed() {
        return destroyed;
    }

    final boolean checkSniHostnameMatch(byte[] hostname) {
        return Java8SslUtils.checkSniHostnameMatch(matchers, hostname);
    }

    @Override
    public String getNegotiatedApplicationProtocol() {
        return applicationProtocol;
    }

    private static long bufferAddress(ByteBuffer b) {
        assert b.isDirect();
        if (PlatformDependent.hasUnsafe()) {
            return PlatformDependent.directBufferAddress(b);
        }
        return Buffer.address(b);
    }

    /**
     * Select the application protocol used.
     */
    private void selectApplicationProtocol() throws SSLException {
        ApplicationProtocolConfig.SelectedListenerFailureBehavior behavior = apn.selectedListenerFailureBehavior();
        List<String> protocols = apn.protocols();
        String applicationProtocol;
        switch (apn.protocol()) {
            case NONE:
                break;
            // We always need to check for applicationProtocol == null as the remote peer may not support
            // the TLS extension or may have returned an empty selection.
            case ALPN:
                applicationProtocol = SSL.getAlpnSelected(ssl);
                if (applicationProtocol != null) {
                    ReferenceCountedOpenSslEngine.this.applicationProtocol = selectApplicationProtocol(
                            protocols, behavior, applicationProtocol);
                }
                break;
            case NPN:
                applicationProtocol = SSL.getNextProtoNegotiated(ssl);
                if (applicationProtocol != null) {
                    ReferenceCountedOpenSslEngine.this.applicationProtocol = selectApplicationProtocol(
                            protocols, behavior, applicationProtocol);
                }
                break;
            case NPN_AND_ALPN:
                applicationProtocol = SSL.getAlpnSelected(ssl);
                if (applicationProtocol == null) {
                    applicationProtocol = SSL.getNextProtoNegotiated(ssl);
                }
                if (applicationProtocol != null) {
                    ReferenceCountedOpenSslEngine.this.applicationProtocol = selectApplicationProtocol(
                            protocols, behavior, applicationProtocol);
                }
                break;
            default:
                throw new Error();
        }
    }

    private String selectApplicationProtocol(List<String> protocols,
                                             ApplicationProtocolConfig.SelectedListenerFailureBehavior behavior,
                                             String applicationProtocol) throws SSLException {
        if (behavior == ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT) {
            return applicationProtocol;
        } else {
            int size = protocols.size();
            assert size > 0;
            if (protocols.contains(applicationProtocol)) {
                return applicationProtocol;
            } else {
                if (behavior == ApplicationProtocolConfig.SelectedListenerFailureBehavior.CHOOSE_MY_LAST_PROTOCOL) {
                    return protocols.get(size - 1);
                } else {
                    throw new SSLException("unknown protocol " + applicationProtocol);
                }
            }
        }
    }

    final void setSessionId(OpenSslSessionId id) {
        session.setSessionId(id);
    }

    private final class DefaultOpenSslSession implements OpenSslSession  {
        private final OpenSslSessionContext sessionContext;

        // These are guarded by synchronized(OpenSslEngine.this) as handshakeFinished() may be triggered by any
        // thread.
        private X509Certificate[] x509PeerCerts;
        private Certificate[] peerCerts;

        private boolean valid = true;
        private String protocol;
        private String cipher;
        private OpenSslSessionId id = OpenSslSessionId.NULL_ID;
        private volatile long creationTime;
        private volatile int applicationBufferSize = MAX_PLAINTEXT_LENGTH;
        private volatile Certificate[] localCertificateChain;
        // lazy init for memory reasons
        private Map<String, Object> values;

        DefaultOpenSslSession(OpenSslSessionContext sessionContext) {
            this.sessionContext = sessionContext;
        }

        private SSLSessionBindingEvent newSSLSessionBindingEvent(String name) {
            return new SSLSessionBindingEvent(session, name);
        }

        @Override
        public void setSessionId(OpenSslSessionId sessionId) {
            synchronized (ReferenceCountedOpenSslEngine.this) {
                if (this.id == OpenSslSessionId.NULL_ID) {
                    this.id = sessionId;
                    creationTime = System.currentTimeMillis();
                }
            }
        }

        @Override
        public OpenSslSessionId sessionId() {
            synchronized (ReferenceCountedOpenSslEngine.this) {
                if (this.id == OpenSslSessionId.NULL_ID && !isDestroyed()) {
                    byte[] sessionId = SSL.getSessionId(ssl);
                    if (sessionId != null) {
                        id = new OpenSslSessionId(sessionId);
                    }
                }

                return id;
            }
        }

        @Override
        public void setLocalCertificate(Certificate[] localCertificate) {
            this.localCertificateChain = localCertificate;
        }

        @Override
        public byte[] getId() {
            return sessionId().cloneBytes();
        }

        @Override
        public OpenSslSessionContext getSessionContext() {
            return sessionContext;
        }

        @Override
        public long getCreationTime() {
            synchronized (ReferenceCountedOpenSslEngine.this) {
                return creationTime;
            }
        }

        @Override
        public long getLastAccessedTime() {
            long lastAccessed = ReferenceCountedOpenSslEngine.this.lastAccessed;
            // if lastAccessed is -1 we will just return the creation time as the handshake was not started yet.
            return lastAccessed == -1 ? getCreationTime() : lastAccessed;
        }

        @Override
        public void invalidate() {
            synchronized (ReferenceCountedOpenSslEngine.this) {
                valid = false;
                sessionContext.removeFromCache(id);
            }
        }

        @Override
        public boolean isValid() {
            synchronized (ReferenceCountedOpenSslEngine.this) {
                return valid || sessionContext.isInCache(id);
            }
        }

        @Override
        public void putValue(String name, Object value) {
            checkNotNull(name, "name");
            checkNotNull(value, "value");

            final Object old;
            synchronized (this) {
                Map<String, Object> values = this.values;
                if (values == null) {
                    // Use size of 2 to keep the memory overhead small
                    values = this.values = new HashMap<String, Object>(2);
                }
                old = values.put(name, value);
            }

            if (value instanceof SSLSessionBindingListener) {
                // Use newSSLSessionBindingEvent so we always use the wrapper if needed.
                ((SSLSessionBindingListener) value).valueBound(newSSLSessionBindingEvent(name));
            }
            notifyUnbound(old, name);
        }

        @Override
        public Object getValue(String name) {
            checkNotNull(name, "name");
            synchronized (this) {
                if (values == null) {
                    return null;
                }
                return values.get(name);
            }
        }

        @Override
        public void removeValue(String name) {
            checkNotNull(name, "name");

            final Object old;
            synchronized (this) {
                Map<String, Object> values = this.values;
                if (values == null) {
                    return;
                }
                old = values.remove(name);
            }

            notifyUnbound(old, name);
        }

        @Override
        public String[] getValueNames() {
            synchronized (this) {
                Map<String, Object> values = this.values;
                if (values == null || values.isEmpty()) {
                    return EmptyArrays.EMPTY_STRINGS;
                }
                return values.keySet().toArray(new String[0]);
            }
        }

        private void notifyUnbound(Object value, String name) {
            if (value instanceof SSLSessionBindingListener) {
                // Use newSSLSessionBindingEvent so we always use the wrapper if needed.
                ((SSLSessionBindingListener) value).valueUnbound(newSSLSessionBindingEvent(name));
            }
        }

        /**
         * Finish the handshake and so init everything in the {@link OpenSslSession} that should be accessible by
         * the user.
         */
        @Override
        public void handshakeFinished(byte[] id, String cipher, String protocol, byte[] peerCertificate,
                                      byte[][] peerCertificateChain, long creationTime, long timeout)
                throws SSLException {
            synchronized (ReferenceCountedOpenSslEngine.this) {
                if (!isDestroyed()) {
                    this.creationTime = creationTime;
                    if (this.id == OpenSslSessionId.NULL_ID) {
                        this.id = id == null ? OpenSslSessionId.NULL_ID : new OpenSslSessionId(id);
                    }
                    this.cipher = toJavaCipherSuite(cipher);
                    this.protocol = protocol;

                    if (clientMode) {
                        if (isEmpty(peerCertificateChain)) {
                            peerCerts = EmptyArrays.EMPTY_CERTIFICATES;
                            x509PeerCerts = EmptyArrays.EMPTY_JAVAX_X509_CERTIFICATES;
                        } else {
                            peerCerts = new Certificate[peerCertificateChain.length];
                            x509PeerCerts = new X509Certificate[peerCertificateChain.length];
                            initCerts(peerCertificateChain, 0);
                        }
                    } else {
                        // if used on the server side SSL_get_peer_cert_chain(...) will not include the remote peer
                        // certificate. We use SSL_get_peer_certificate to get it in this case and add it to our
                        // array later.
                        //
                        // See https://www.openssl.org/docs/ssl/SSL_get_peer_cert_chain.html
                        if (isEmpty(peerCertificate)) {
                            peerCerts = EmptyArrays.EMPTY_CERTIFICATES;
                            x509PeerCerts = EmptyArrays.EMPTY_JAVAX_X509_CERTIFICATES;
                        } else {
                            if (isEmpty(peerCertificateChain)) {
                                peerCerts = new Certificate[] {new LazyX509Certificate(peerCertificate)};
                                x509PeerCerts = new X509Certificate[] {new LazyJavaxX509Certificate(peerCertificate)};
                            } else {
                                peerCerts = new Certificate[peerCertificateChain.length + 1];
                                x509PeerCerts = new X509Certificate[peerCertificateChain.length + 1];
                                peerCerts[0] = new LazyX509Certificate(peerCertificate);
                                x509PeerCerts[0] = new LazyJavaxX509Certificate(peerCertificate);
                                initCerts(peerCertificateChain, 1);
                            }
                        }
                    }

                    calculateMaxWrapOverhead();

                    handshakeState = HandshakeState.FINISHED;
                } else {
                    throw new SSLException("Already closed");
                }
            }
        }

        private void initCerts(byte[][] chain, int startPos) {
            for (int i = 0; i < chain.length; i++) {
                int certPos = startPos + i;
                peerCerts[certPos] = new LazyX509Certificate(chain[i]);
                x509PeerCerts[certPos] = new LazyJavaxX509Certificate(chain[i]);
            }
        }

        @Override
        public Certificate[] getPeerCertificates() throws SSLPeerUnverifiedException {
            synchronized (ReferenceCountedOpenSslEngine.this) {
                if (isEmpty(peerCerts)) {
                    throw new SSLPeerUnverifiedException("peer not verified");
                }
                return peerCerts.clone();
            }
        }

        @Override
        public Certificate[] getLocalCertificates() {
            Certificate[] localCerts = this.localCertificateChain;
            if (localCerts == null) {
                return null;
            }
            return localCerts.clone();
        }

        @Override
        public X509Certificate[] getPeerCertificateChain() throws SSLPeerUnverifiedException {
            synchronized (ReferenceCountedOpenSslEngine.this) {
                if (isEmpty(x509PeerCerts)) {
                    throw new SSLPeerUnverifiedException("peer not verified");
                }
                return x509PeerCerts.clone();
            }
        }

        @Override
        public Principal getPeerPrincipal() throws SSLPeerUnverifiedException {
            Certificate[] peer = getPeerCertificates();
            // No need for null or length > 0 is needed as this is done in getPeerCertificates()
            // already.
            return ((java.security.cert.X509Certificate) peer[0]).getSubjectX500Principal();
        }

        @Override
        public Principal getLocalPrincipal() {
            Certificate[] local = this.localCertificateChain;
            if (local == null || local.length == 0) {
                return null;
            }
            return ((java.security.cert.X509Certificate) local[0]).getIssuerX500Principal();
        }

        @Override
        public String getCipherSuite() {
            synchronized (ReferenceCountedOpenSslEngine.this) {
                if (cipher == null) {
                    return SslUtils.INVALID_CIPHER;
                }
                return cipher;
            }
        }

        @Override
        public String getProtocol() {
            String protocol = this.protocol;
            if (protocol == null) {
                synchronized (ReferenceCountedOpenSslEngine.this) {
                    if (!isDestroyed()) {
                        protocol = SSL.getVersion(ssl);
                    } else {
                        protocol = StringUtil.EMPTY_STRING;
                    }
                }
            }
            return protocol;
        }

        @Override
        public String getPeerHost() {
            return ReferenceCountedOpenSslEngine.this.getPeerHost();
        }

        @Override
        public int getPeerPort() {
            return ReferenceCountedOpenSslEngine.this.getPeerPort();
        }

        @Override
        public int getPacketBufferSize() {
            return maxEncryptedPacketLength();
        }

        @Override
        public int getApplicationBufferSize() {
            return applicationBufferSize;
        }

        @Override
        public void tryExpandApplicationBufferSize(int packetLengthDataOnly) {
            if (packetLengthDataOnly > MAX_PLAINTEXT_LENGTH && applicationBufferSize != MAX_RECORD_SIZE) {
                applicationBufferSize = MAX_RECORD_SIZE;
            }
        }

        @Override
        public String toString() {
            return "DefaultOpenSslSession{" +
                    "sessionContext=" + sessionContext +
                    ", id=" + id +
                    '}';
        }
    }
}
