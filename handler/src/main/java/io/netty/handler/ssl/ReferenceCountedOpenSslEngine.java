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
package io.netty.handler.ssl;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.util.AbstractReferenceCounted;
import io.netty.util.ReferenceCounted;
import io.netty.util.ResourceLeak;
import io.netty.util.ResourceLeakDetector;
import io.netty.util.ResourceLeakDetectorFactory;
import io.netty.util.internal.EmptyArrays;
import io.netty.util.internal.InternalThreadLocalMap;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.ThrowableUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.apache.tomcat.jni.Buffer;
import org.apache.tomcat.jni.SSL;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.security.Principal;
import java.security.cert.Certificate;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSessionBindingEvent;
import javax.net.ssl.SSLSessionBindingListener;
import javax.net.ssl.SSLSessionContext;
import javax.security.cert.X509Certificate;

import static io.netty.handler.ssl.OpenSsl.memoryAddress;
import static io.netty.util.internal.ObjectUtil.checkNotNull;
import static javax.net.ssl.SSLEngineResult.HandshakeStatus.FINISHED;
import static javax.net.ssl.SSLEngineResult.HandshakeStatus.NEED_UNWRAP;
import static javax.net.ssl.SSLEngineResult.HandshakeStatus.NEED_WRAP;
import static javax.net.ssl.SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING;
import static javax.net.ssl.SSLEngineResult.Status.BUFFER_OVERFLOW;
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
public class ReferenceCountedOpenSslEngine extends SSLEngine implements ReferenceCounted {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(ReferenceCountedOpenSslEngine.class);

    private static final SSLException BEGIN_HANDSHAKE_ENGINE_CLOSED = ThrowableUtil.unknownStackTrace(
            new SSLException("engine closed"), ReferenceCountedOpenSslEngine.class, "beginHandshake()");
    private static final SSLException HANDSHAKE_ENGINE_CLOSED = ThrowableUtil.unknownStackTrace(
            new SSLException("engine closed"), ReferenceCountedOpenSslEngine.class, "handshake()");
    private static final SSLException RENEGOTIATION_UNSUPPORTED =  ThrowableUtil.unknownStackTrace(
            new SSLException("renegotiation unsupported"), ReferenceCountedOpenSslEngine.class, "beginHandshake()");
    private static final SSLException ENCRYPTED_PACKET_OVERSIZED = ThrowableUtil.unknownStackTrace(
            new SSLException("encrypted packet oversized"), ReferenceCountedOpenSslEngine.class, "unwrap(...)");
    private static final Class<?> SNI_HOSTNAME_CLASS;
    private static final Method GET_SERVER_NAMES_METHOD;
    private static final Method SET_SERVER_NAMES_METHOD;
    private static final Method GET_ASCII_NAME_METHOD;
    private static final Method GET_USE_CIPHER_SUITES_ORDER_METHOD;
    private static final Method SET_USE_CIPHER_SUITES_ORDER_METHOD;
    private static final ResourceLeakDetector<ReferenceCountedOpenSslEngine> leakDetector =
            ResourceLeakDetectorFactory.instance().newResourceLeakDetector(ReferenceCountedOpenSslEngine.class);

    static {
        AtomicIntegerFieldUpdater<ReferenceCountedOpenSslEngine> destroyedUpdater =
                PlatformDependent.newAtomicIntegerFieldUpdater(ReferenceCountedOpenSslEngine.class, "destroyed");
        if (destroyedUpdater == null) {
            destroyedUpdater = AtomicIntegerFieldUpdater.newUpdater(ReferenceCountedOpenSslEngine.class, "destroyed");
        }
        DESTROYED_UPDATER = destroyedUpdater;

        Method getUseCipherSuitesOrderMethod = null;
        Method setUseCipherSuitesOrderMethod = null;
        Class<?> sniHostNameClass = null;
        Method getAsciiNameMethod = null;
        Method getServerNamesMethod = null;
        Method setServerNamesMethod = null;
        if (PlatformDependent.javaVersion() >= 8) {
            try {
                getUseCipherSuitesOrderMethod = SSLParameters.class.getDeclaredMethod("getUseCipherSuitesOrder");
                SSLParameters parameters = new SSLParameters();
                @SuppressWarnings("unused")
                Boolean order = (Boolean) getUseCipherSuitesOrderMethod.invoke(parameters);
                setUseCipherSuitesOrderMethod = SSLParameters.class.getDeclaredMethod("setUseCipherSuitesOrder",
                        boolean.class);
                setUseCipherSuitesOrderMethod.invoke(parameters, true);
            } catch (Throwable ignore) {
                getUseCipherSuitesOrderMethod = null;
                setUseCipherSuitesOrderMethod = null;
            }
            try {
                sniHostNameClass = Class.forName("javax.net.ssl.SNIHostName", false,
                        PlatformDependent.getClassLoader(ReferenceCountedOpenSslEngine.class));
                Object sniHostName = sniHostNameClass.getConstructor(String.class).newInstance("netty.io");
                getAsciiNameMethod = sniHostNameClass.getDeclaredMethod("getAsciiName");
                @SuppressWarnings("unused")
                String name = (String) getAsciiNameMethod.invoke(sniHostName);

                getServerNamesMethod = SSLParameters.class.getDeclaredMethod("getServerNames");
                setServerNamesMethod = SSLParameters.class.getDeclaredMethod("setServerNames", List.class);
                SSLParameters parameters = new SSLParameters();
                @SuppressWarnings({ "rawtypes", "unused" })
                List serverNames = (List) getServerNamesMethod.invoke(parameters);
                setServerNamesMethod.invoke(parameters, Collections.emptyList());
            } catch (Throwable ingore) {
                sniHostNameClass = null;
                getAsciiNameMethod = null;
                getServerNamesMethod = null;
                setServerNamesMethod = null;
            }
        }
        GET_USE_CIPHER_SUITES_ORDER_METHOD = getUseCipherSuitesOrderMethod;
        SET_USE_CIPHER_SUITES_ORDER_METHOD = setUseCipherSuitesOrderMethod;
        SNI_HOSTNAME_CLASS = sniHostNameClass;
        GET_ASCII_NAME_METHOD = getAsciiNameMethod;
        GET_SERVER_NAMES_METHOD = getServerNamesMethod;
        SET_SERVER_NAMES_METHOD = setServerNamesMethod;
    }

    private static final int MAX_PLAINTEXT_LENGTH = 16 * 1024; // 2^14
    private static final int MAX_COMPRESSED_LENGTH = MAX_PLAINTEXT_LENGTH + 1024;
    private static final int MAX_CIPHERTEXT_LENGTH = MAX_COMPRESSED_LENGTH + 1024;

    // Header (5) + Data (2^14) + Compression (1024) + Encryption (1024) + MAC (20) + Padding (256)
    static final int MAX_ENCRYPTED_PACKET_LENGTH = MAX_CIPHERTEXT_LENGTH + 5 + 20 + 256;

    static final int MAX_ENCRYPTION_OVERHEAD_LENGTH = MAX_ENCRYPTED_PACKET_LENGTH - MAX_PLAINTEXT_LENGTH;

    private static final AtomicIntegerFieldUpdater<ReferenceCountedOpenSslEngine> DESTROYED_UPDATER;

    private static final String INVALID_CIPHER = "SSL_NULL_WITH_NULL_NULL";

    private static final long EMPTY_ADDR = Buffer.address(Unpooled.EMPTY_BUFFER.nioBuffer());

    private static final SSLEngineResult NEED_UNWRAP_OK = new SSLEngineResult(OK, NEED_UNWRAP, 0, 0);
    private static final SSLEngineResult NEED_UNWRAP_CLOSED = new SSLEngineResult(CLOSED, NEED_UNWRAP, 0, 0);
    private static final SSLEngineResult NEED_WRAP_OK = new SSLEngineResult(OK, NEED_WRAP, 0, 0);
    private static final SSLEngineResult NEED_WRAP_CLOSED = new SSLEngineResult(CLOSED, NEED_WRAP, 0, 0);
    private static final SSLEngineResult CLOSED_NOT_HANDSHAKING = new SSLEngineResult(CLOSED, NOT_HANDSHAKING, 0, 0);

    // OpenSSL state
    private long ssl;
    private long networkBIO;
    private boolean certificateSet;

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
    private volatile int destroyed;

    // Reference Counting
    private final ResourceLeak leak;
    private final AbstractReferenceCounted refCnt = new AbstractReferenceCounted() {
        @Override
        protected void deallocate() {
            shutdown();
            if (leak != null) {
                leak.close();
            }
        }
    };

    private volatile ClientAuth clientAuth = ClientAuth.NONE;

    // Updated once a new handshake is started and so the SSLSession reused.
    private volatile long lastAccessed = -1;

    private String endPointIdentificationAlgorithm;
    // Store as object as AlgorithmConstraints only exists since java 7.
    private Object algorithmConstraints;
    private List<?> sniHostNames;

    // SSL Engine status variables
    private boolean isInboundDone;
    private boolean isOutboundDone;
    private boolean engineClosed;

    private final boolean clientMode;
    private final ByteBufAllocator alloc;
    private final OpenSslEngineMap engineMap;
    private final OpenSslApplicationProtocolNegotiator apn;
    private final boolean rejectRemoteInitiatedRenegation;
    private final OpenSslSession session;
    private final Certificate[] localCerts;
    private final ByteBuffer[] singleSrcBuffer = new ByteBuffer[1];
    private final ByteBuffer[] singleDstBuffer = new ByteBuffer[1];
    private final OpenSslKeyMaterialManager keyMaterialManager;

    // This is package-private as we set it from OpenSslContext if an exception is thrown during
    // the verification step.
    SSLHandshakeException handshakeException;

    /**
     * Create a new instance.
     * @param context Reference count release responsibility is not transferred! The callee still owns this object.
     * @param alloc The allocator to use.
     * @param peerHost The peer host name.
     * @param peerPort The peer port.
     * @param leakDetection {@code true} to enable leak detection of this object.
     */
    ReferenceCountedOpenSslEngine(ReferenceCountedOpenSslContext context, ByteBufAllocator alloc, String peerHost,
                                  int peerPort, boolean leakDetection) {
        super(peerHost, peerPort);
        OpenSsl.ensureAvailability();
        leak = leakDetection ? leakDetector.open(this) : null;
        this.alloc = checkNotNull(alloc, "alloc");
        apn = (OpenSslApplicationProtocolNegotiator) context.applicationProtocolNegotiator();
        ssl = SSL.newSSL(context.ctx, !context.isClient());
        session = new ReferenceCountedOpenSslEngine.OpenSslSession(context.sessionContext());
        networkBIO = SSL.makeNetworkBIO(ssl);
        clientMode = context.isClient();
        engineMap = context.engineMap;
        rejectRemoteInitiatedRenegation = context.rejectRemoteInitiatedRenegotiation;
        localCerts = context.keyCertChain;

        // Set the client auth mode, this needs to be done via setClientAuth(...) method so we actually call the
        // needed JNI methods.
        setClientAuth(clientMode ? ClientAuth.NONE : context.clientAuth);

        // Use SNI if peerHost was specified
        // See https://github.com/netty/netty/issues/4746
        if (clientMode && peerHost != null) {
            SSL.setTlsExtHostName(ssl, peerHost);
        }
        keyMaterialManager = context.keyMaterialManager();
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
    public final boolean release() {
        return refCnt.release();
    }

    @Override
    public final boolean release(int decrement) {
        return refCnt.release(decrement);
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
        if (DESTROYED_UPDATER.compareAndSet(this, 0, 1)) {
            engineMap.remove(ssl);
            SSL.freeSSL(ssl);
            SSL.freeBIO(networkBIO);
            ssl = networkBIO = 0;

            // internal errors can cause shutdown without marking the engine closed
            isInboundDone = isOutboundDone = engineClosed = true;
        }

        // On shutdown clear all errors
        SSL.clearError();
    }

    /**
     * Write plaintext data to the OpenSSL internal BIO
     *
     * Calling this function with src.remaining == 0 is undefined.
     */
    private int writePlaintextData(final ByteBuffer src) {
        final int pos = src.position();
        final int limit = src.limit();
        final int len = Math.min(limit - pos, MAX_PLAINTEXT_LENGTH);
        final int sslWrote;

        if (src.isDirect()) {
            final long addr = Buffer.address(src) + pos;
            sslWrote = SSL.writeToSSL(ssl, addr, len);
            if (sslWrote > 0) {
                src.position(pos + sslWrote);
            }
        } else {
            ByteBuf buf = alloc.directBuffer(len);
            try {
                final long addr = memoryAddress(buf);

                src.limit(pos + len);

                buf.setBytes(0, src);
                src.limit(limit);

                sslWrote = SSL.writeToSSL(ssl, addr, len);
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
    private int writeEncryptedData(final ByteBuffer src) {
        final int pos = src.position();
        final int len = src.remaining();
        final int netWrote;
        if (src.isDirect()) {
            final long addr = Buffer.address(src) + pos;
            netWrote = SSL.writeToBIO(networkBIO, addr, len);
            if (netWrote >= 0) {
                src.position(pos + netWrote);
            }
        } else {
            final ByteBuf buf = alloc.directBuffer(len);
            try {
                final long addr = memoryAddress(buf);

                buf.setBytes(0, src);

                netWrote = SSL.writeToBIO(networkBIO, addr, len);
                if (netWrote >= 0) {
                    src.position(pos + netWrote);
                } else {
                    src.position(pos);
                }
            } finally {
                buf.release();
            }
        }

        return netWrote;
    }

    /**
     * Read plaintext data from the OpenSSL internal BIO
     */
    private int readPlaintextData(final ByteBuffer dst) {
        final int sslRead;
        if (dst.isDirect()) {
            final int pos = dst.position();
            final long addr = Buffer.address(dst) + pos;
            final int len = dst.limit() - pos;
            sslRead = SSL.readFromSSL(ssl, addr, len);
            if (sslRead > 0) {
                dst.position(pos + sslRead);
            }
        } else {
            final int pos = dst.position();
            final int limit = dst.limit();
            final int len = Math.min(MAX_ENCRYPTED_PACKET_LENGTH, limit - pos);
            final ByteBuf buf = alloc.directBuffer(len);
            try {
                final long addr = memoryAddress(buf);

                sslRead = SSL.readFromSSL(ssl, addr, len);
                if (sslRead > 0) {
                    dst.limit(pos + sslRead);
                    buf.getBytes(0, dst);
                    dst.limit(limit);
                }
            } finally {
                buf.release();
            }
        }

        return sslRead;
    }

    /**
     * Read encrypted data from the OpenSSL network BIO
     */
    private int readEncryptedData(final ByteBuffer dst, final int pending) {
        final int bioRead;

        if (dst.isDirect() && dst.remaining() >= pending) {
            final int pos = dst.position();
            final long addr = Buffer.address(dst) + pos;
            bioRead = SSL.readFromBIO(networkBIO, addr, pending);
            if (bioRead > 0) {
                dst.position(pos + bioRead);
                return bioRead;
            }
        } else {
            final ByteBuf buf = alloc.directBuffer(pending);
            try {
                final long addr = memoryAddress(buf);

                bioRead = SSL.readFromBIO(networkBIO, addr, pending);
                if (bioRead > 0) {
                    int oldLimit = dst.limit();
                    dst.limit(dst.position() + bioRead);
                    buf.getBytes(0, dst);
                    dst.limit(oldLimit);
                    return bioRead;
                }
            } finally {
                buf.release();
            }
        }

        return bioRead;
    }

    private SSLEngineResult readPendingBytesFromBIO(ByteBuffer dst, int bytesConsumed, int bytesProduced,
                                                    SSLEngineResult.HandshakeStatus status) throws SSLException {
        // Check to see if the engine wrote data into the network BIO
        int pendingNet = SSL.pendingWrittenBytesInBIO(networkBIO);
        if (pendingNet > 0) {

            // Do we have enough room in dst to write encrypted data?
            int capacity = dst.remaining();
            if (capacity < pendingNet) {
                return new SSLEngineResult(BUFFER_OVERFLOW,
                        mayFinishHandshake(status != FINISHED ? getHandshakeStatus(pendingNet) : status),
                        bytesConsumed, bytesProduced);
            }

            // Write the pending data from the network BIO into the dst buffer
            int produced = readEncryptedData(dst, pendingNet);

            if (produced <= 0) {
                // We ignore BIO_* errors here as we use in memory BIO anyway and will do another SSL_* call later
                // on in which we will produce an exception in case of an error
                SSL.clearError();
            } else {
                bytesProduced += produced;
                pendingNet -= produced;
            }
            // If isOuboundDone is set, then the data from the network BIO
            // was the close_notify message -- we are not required to wait
            // for the receipt the peer's close_notify message -- shutdown.
            if (isOutboundDone) {
                shutdown();
            }

            return new SSLEngineResult(getEngineStatus(),
                    mayFinishHandshake(status != FINISHED ? getHandshakeStatus(pendingNet) : status),
                    bytesConsumed, bytesProduced);
        }
        return null;
    }

    @Override
    public final SSLEngineResult wrap(
            final ByteBuffer[] srcs, final int offset, final int length, final ByteBuffer dst) throws SSLException {
        // Throw required runtime exceptions
        if (srcs == null) {
            throw new IllegalArgumentException("srcs is null");
        }
        if (dst == null) {
            throw new IllegalArgumentException("dst is null");
        }

        if (offset >= srcs.length || offset + length > srcs.length) {
            throw new IndexOutOfBoundsException(
                    "offset: " + offset + ", length: " + length +
                            " (expected: offset <= offset + length <= srcs.length (" + srcs.length + "))");
        }

        if (dst.isReadOnly()) {
            throw new ReadOnlyBufferException();
        }

        synchronized (this) {
            // Check to make sure the engine has not been closed
            if (isDestroyed()) {
                return CLOSED_NOT_HANDSHAKING;
            }

            SSLEngineResult.HandshakeStatus status = NOT_HANDSHAKING;
            // Prepare OpenSSL to work in server mode and receive handshake
            if (handshakeState != HandshakeState.FINISHED) {
                if (handshakeState != HandshakeState.STARTED_EXPLICITLY) {
                    // Update accepted so we know we triggered the handshake via wrap
                    handshakeState = HandshakeState.STARTED_IMPLICITLY;
                }

                status = handshake();
                if (status == NEED_UNWRAP) {
                    return NEED_UNWRAP_OK;
                }

                if (engineClosed) {
                    return NEED_UNWRAP_CLOSED;
                }
            }

            // There was no pending data in the network BIO -- encrypt any application data
            int bytesProduced = 0;
            int bytesConsumed = 0;
            int endOffset = offset + length;
            for (int i = offset; i < endOffset; ++i) {
                final ByteBuffer src = srcs[i];
                if (src == null) {
                    throw new IllegalArgumentException("srcs[" + i + "] is null");
                }
                while (src.hasRemaining()) {
                    final SSLEngineResult pendingNetResult;
                    // Write plaintext application data to the SSL engine
                    int result = writePlaintextData(src);
                    if (result > 0) {
                        bytesConsumed += result;

                        pendingNetResult = readPendingBytesFromBIO(dst, bytesConsumed, bytesProduced, status);
                        if (pendingNetResult != null) {
                            if (pendingNetResult.getStatus() != OK) {
                                return pendingNetResult;
                            }
                            bytesProduced = pendingNetResult.bytesProduced();
                        }
                    } else {
                        int sslError = SSL.getError(ssl, result);
                        switch (sslError) {
                            case SSL.SSL_ERROR_ZERO_RETURN:
                                // This means the connection was shutdown correctly, close inbound and outbound
                                if (!receivedShutdown) {
                                    closeAll();
                                }
                                pendingNetResult = readPendingBytesFromBIO(dst, bytesConsumed, bytesProduced, status);
                                return pendingNetResult != null ? pendingNetResult : CLOSED_NOT_HANDSHAKING;
                            case SSL.SSL_ERROR_WANT_READ:
                                // If there is no pending data to read from BIO we should go back to event loop and try
                                // to read more data [1]. It is also possible that event loop will detect the socket
                                // has been closed. [1] https://www.openssl.org/docs/manmaster/ssl/SSL_write.html
                                pendingNetResult = readPendingBytesFromBIO(dst, bytesConsumed, bytesProduced, status);
                                return pendingNetResult != null ? pendingNetResult :
                                        new SSLEngineResult(getEngineStatus(),
                                                NEED_UNWRAP, bytesConsumed, bytesProduced);
                            case SSL.SSL_ERROR_WANT_WRITE:
                                // SSL_ERROR_WANT_WRITE typically means that the underlying transport is not writable
                                // and we should set the "want write" flag on the selector and try again when the
                                // underlying transport is writable [1]. However we are not directly writing to the
                                // underlying transport and instead writing to a BIO buffer. The OpenSsl documentation
                                // says we should do the following [1]:
                                //
                                // "When using a buffering BIO, like a BIO pair, data must be written into or retrieved
                                // out of the BIO before being able to continue."
                                //
                                // So we attempt to drain the BIO buffer below, but if there is no data this condition
                                // is undefined and we assume their is a fatal error with the openssl engine and close.
                                // [1] https://www.openssl.org/docs/manmaster/ssl/SSL_write.html
                                pendingNetResult = readPendingBytesFromBIO(dst, bytesConsumed, bytesProduced, status);
                                return pendingNetResult != null ? pendingNetResult : NEED_WRAP_CLOSED;
                            default:
                                // Everything else is considered as error
                                throw shutdownWithError("SSL_write");
                        }
                    }
                }
            }
            // We need to check if pendingWrittenBytesInBIO was checked yet, as we may not checked if the srcs was
            // empty, or only contained empty buffers.
            if (bytesConsumed == 0) {
                SSLEngineResult pendingNetResult = readPendingBytesFromBIO(dst, 0, bytesProduced, status);
                if (pendingNetResult != null) {
                    return pendingNetResult;
                }
            }

            return newResult(bytesConsumed, bytesProduced, status);
        }
    }

    /**
     * Log the error, shutdown the engine and throw an exception.
     */
    private SSLException shutdownWithError(String operations) {
        String err = SSL.getLastError();
        return shutdownWithError(operations, err);
    }

    private SSLException shutdownWithError(String operation, String err) {
        if (logger.isDebugEnabled()) {
            logger.debug("{} failed: OpenSSL error: {}", operation, err);
        }

        // There was an internal error -- shutdown
        shutdown();
        if (handshakeState == HandshakeState.FINISHED) {
            return new SSLException(err);
        }
        return new SSLHandshakeException(err);
    }

    public final SSLEngineResult unwrap(
            final ByteBuffer[] srcs, int srcsOffset, final int srcsLength,
            final ByteBuffer[] dsts, final int dstsOffset, final int dstsLength) throws SSLException {

        // Throw required runtime exceptions
        if (srcs == null) {
            throw new NullPointerException("srcs");
        }
        if (srcsOffset >= srcs.length
                || srcsOffset + srcsLength > srcs.length) {
            throw new IndexOutOfBoundsException(
                    "offset: " + srcsOffset + ", length: " + srcsLength +
                            " (expected: offset <= offset + length <= srcs.length (" + srcs.length + "))");
        }
        if (dsts == null) {
            throw new IllegalArgumentException("dsts is null");
        }
        if (dstsOffset >= dsts.length || dstsOffset + dstsLength > dsts.length) {
            throw new IndexOutOfBoundsException(
                    "offset: " + dstsOffset + ", length: " + dstsLength +
                            " (expected: offset <= offset + length <= dsts.length (" + dsts.length + "))");
        }
        long capacity = 0;
        final int endOffset = dstsOffset + dstsLength;
        for (int i = dstsOffset; i < endOffset; i ++) {
            ByteBuffer dst = dsts[i];
            if (dst == null) {
                throw new IllegalArgumentException("dsts[" + i + "] is null");
            }
            if (dst.isReadOnly()) {
                throw new ReadOnlyBufferException();
            }
            capacity += dst.remaining();
        }

        final int srcsEndOffset = srcsOffset + srcsLength;
        long len = 0;
        for (int i = srcsOffset; i < srcsEndOffset; i++) {
            ByteBuffer src = srcs[i];
            if (src == null) {
                throw new IllegalArgumentException("srcs[" + i + "] is null");
            }
            len += src.remaining();
        }

        synchronized (this) {
            // Check to make sure the engine has not been closed
            if (isDestroyed()) {
                return CLOSED_NOT_HANDSHAKING;
            }

            // protect against protocol overflow attack vector
            if (len > MAX_ENCRYPTED_PACKET_LENGTH) {
                isInboundDone = true;
                isOutboundDone = true;
                engineClosed = true;
                shutdown();
                throw ENCRYPTED_PACKET_OVERSIZED;
            }

            SSLEngineResult.HandshakeStatus status = NOT_HANDSHAKING;
            // Prepare OpenSSL to work in server mode and receive handshake
            if (handshakeState != HandshakeState.FINISHED) {
                if (handshakeState != HandshakeState.STARTED_EXPLICITLY) {
                    // Update accepted so we know we triggered the handshake via wrap
                    handshakeState = HandshakeState.STARTED_IMPLICITLY;
                }

                status = handshake();
                if (status == NEED_WRAP) {
                    return NEED_WRAP_OK;
                }
                if (engineClosed) {
                    return NEED_WRAP_CLOSED;
                }
            }

            // Write encrypted data to network BIO
            int bytesConsumed = 0;
            if (srcsOffset < srcsEndOffset) {
                do {
                    ByteBuffer src = srcs[srcsOffset];
                    int remaining = src.remaining();
                    if (remaining == 0) {
                        // We must skip empty buffers as BIO_write will return 0 if asked to write something
                        // with length 0.
                        srcsOffset++;
                        continue;
                    }
                    int written = writeEncryptedData(src);
                    if (written > 0) {
                        bytesConsumed += written;

                        if (written == remaining) {
                            srcsOffset++;
                        } else {
                            // We were not able to write everything into the BIO so break the write loop as otherwise
                            // we will produce an error on the next write attempt, which will trigger a SSL.clearError()
                            // later.
                            break;
                        }
                    } else {
                        // BIO_write returned a negative or zero number, this means we could not complete the write
                        // operation and should retry later.
                        // We ignore BIO_* errors here as we use in memory BIO anyway and will do another SSL_* call
                        // later on in which we will produce an exception in case of an error
                        SSL.clearError();
                        break;
                    }
                } while (srcsOffset < srcsEndOffset);
            }

            // Number of produced bytes
            int bytesProduced = 0;

            if (capacity > 0) {
                // Write decrypted data to dsts buffers
                int idx = dstsOffset;
                while (idx < endOffset) {
                    ByteBuffer dst = dsts[idx];
                    if (!dst.hasRemaining()) {
                        idx++;
                        continue;
                    }

                    int bytesRead = readPlaintextData(dst);

                    // TODO: We may want to consider if we move this check and only do it in a less often called place
                    // at the price of not being 100% accurate, like for example when calling SSL.getError(...).
                    rejectRemoteInitiatedRenegation();

                    if (bytesRead > 0) {
                        bytesProduced += bytesRead;

                        if (!dst.hasRemaining()) {
                            idx++;
                        } else {
                            // We read everything return now.
                            return newResult(bytesConsumed, bytesProduced, status);
                        }
                    } else {
                        int sslError = SSL.getError(ssl, bytesRead);
                        switch (sslError) {
                            case SSL.SSL_ERROR_ZERO_RETURN:
                                // This means the connection was shutdown correctly, close inbound and outbound
                                if (!receivedShutdown) {
                                    closeAll();
                                }
                                // fall-trough!
                            case SSL.SSL_ERROR_WANT_READ:
                            case SSL.SSL_ERROR_WANT_WRITE:
                                // break to the outer loop
                                return newResult(bytesConsumed, bytesProduced, status);
                            default:
                                return sslReadErrorResult(SSL.getLastErrorNumber(), bytesConsumed, bytesProduced);
                        }
                    }
                }
            } else {
                // If the capacity of all destination buffers is 0 we need to trigger a SSL_read anyway to ensure
                // everything is flushed in the BIO pair and so we can detect it in the pendingAppData() call.
                if (SSL.readFromSSL(ssl, EMPTY_ADDR, 0) <= 0) {
                    // We do not check SSL_get_error as we are not interested in any error that is not fatal.
                    int err = SSL.getLastErrorNumber();
                    if (OpenSsl.isError(err)) {
                        return sslReadErrorResult(err, bytesConsumed, bytesProduced);
                    }
                }
            }
            if (pendingAppData() > 0) {
                // We filled all buffers but there is still some data pending in the BIO buffer, return BUFFER_OVERFLOW.
                return new SSLEngineResult(
                        BUFFER_OVERFLOW, mayFinishHandshake(status != FINISHED ? getHandshakeStatus() : status),
                        bytesConsumed, bytesProduced);
            }

            // Check to see if we received a close_notify message from the peer.
            if (!receivedShutdown && (SSL.getShutdown(ssl) & SSL.SSL_RECEIVED_SHUTDOWN) == SSL.SSL_RECEIVED_SHUTDOWN) {
                closeAll();
            }

            return newResult(bytesConsumed, bytesProduced, status);
        }
    }

    private SSLEngineResult sslReadErrorResult(int err, int bytesConsumed, int bytesProduced) throws SSLException {
        String errStr = SSL.getErrorString(err);

        // Check if we have a pending handshakeException and if so see if we need to consume all pending data from the
        // BIO first or can just shutdown and throw it now.
        // This is needed so we ensure close_notify etc is correctly send to the remote peer.
        // See https://github.com/netty/netty/issues/3900
        if (SSL.pendingWrittenBytesInBIO(networkBIO) > 0) {
            if (handshakeException == null && handshakeState != HandshakeState.FINISHED) {
                // we seems to have data left that needs to be transfered and so the user needs
                // call wrap(...). Store the error so we can pick it up later.
                handshakeException = new SSLHandshakeException(errStr);
            }
            return new SSLEngineResult(OK, NEED_WRAP, bytesConsumed, bytesProduced);
        }
        throw shutdownWithError("SSL_read", errStr);
    }

    private int pendingAppData() {
        // There won't be any application data until we're done handshaking.
        // We first check handshakeFinished to eliminate the overhead of extra JNI call if possible.
        return handshakeState == HandshakeState.FINISHED ? SSL.pendingReadableBytesInSSL(ssl) : 0;
    }

    private SSLEngineResult newResult(
            int bytesConsumed, int bytesProduced, SSLEngineResult.HandshakeStatus status) throws SSLException {
        return new SSLEngineResult(
                getEngineStatus(), mayFinishHandshake(status != FINISHED ? getHandshakeStatus() : status)
                , bytesConsumed, bytesProduced);
    }

    private void closeAll() throws SSLException {
        receivedShutdown = true;
        closeOutbound();
        closeInbound();
    }

    private void rejectRemoteInitiatedRenegation() throws SSLHandshakeException {
        if (rejectRemoteInitiatedRenegation && SSL.getHandshakeCount(ssl) > 1) {
            // TODO: In future versions me may also want to send a fatal_alert to the client and so notify it
            // that the renegotiation failed.
            shutdown();
            throw new SSLHandshakeException("remote-initiated renegotation not allowed");
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

    @Override
    public final Runnable getDelegatedTask() {
        // Currently, we do not delegate SSL computation tasks
        // TODO: in the future, possibly create tasks to do encrypt / decrypt async

        return null;
    }

    @Override
    public final synchronized void closeInbound() throws SSLException {
        if (isInboundDone) {
            return;
        }

        isInboundDone = true;
        engineClosed = true;

        shutdown();

        if (handshakeState != HandshakeState.NOT_STARTED && !receivedShutdown) {
            throw new SSLException(
                    "Inbound closed before receiving peer's close_notify: possible truncation attack?");
        }
    }

    @Override
    public final synchronized boolean isInboundDone() {
        return isInboundDone || engineClosed;
    }

    @Override
    public final synchronized void closeOutbound() {
        if (isOutboundDone) {
            return;
        }

        isOutboundDone = true;
        engineClosed = true;

        if (handshakeState != HandshakeState.NOT_STARTED && !isDestroyed()) {
            int mode = SSL.getShutdown(ssl);
            if ((mode & SSL.SSL_SENT_SHUTDOWN) != SSL.SSL_SENT_SHUTDOWN) {
                int err = SSL.shutdownSSL(ssl);
                if (err < 0) {
                    int sslErr = SSL.getError(ssl, err);
                    switch (sslErr) {
                        case SSL.SSL_ERROR_NONE:
                        case SSL.SSL_ERROR_WANT_ACCEPT:
                        case SSL.SSL_ERROR_WANT_CONNECT:
                        case SSL.SSL_ERROR_WANT_WRITE:
                        case SSL.SSL_ERROR_WANT_READ:
                        case SSL.SSL_ERROR_WANT_X509_LOOKUP:
                        case SSL.SSL_ERROR_ZERO_RETURN:
                            // Nothing to do here
                            break;
                        case SSL.SSL_ERROR_SYSCALL:
                        case SSL.SSL_ERROR_SSL:
                            if (logger.isDebugEnabled()) {
                                logger.debug("SSL_shutdown failed: OpenSSL error: {}", SSL.getLastError());
                            }
                            // There was an internal error -- shutdown
                            shutdown();
                            break;
                        default:
                            SSL.clearError();
                            break;
                    }
                }
            }
        } else {
            // engine closing before initial handshake
            shutdown();
        }
    }

    @Override
    public final synchronized boolean isOutboundDone() {
        return isOutboundDone;
    }

    @Override
    public final String[] getSupportedCipherSuites() {
        return OpenSsl.AVAILABLE_CIPHER_SUITES.toArray(new String[OpenSsl.AVAILABLE_CIPHER_SUITES.size()]);
    }

    @Override
    public final String[] getEnabledCipherSuites() {
        final String[] enabled;
        synchronized (this) {
            if (!isDestroyed()) {
                enabled = SSL.getCiphers(ssl);
            } else {
                return EmptyArrays.EMPTY_STRINGS;
            }
        }
        if (enabled == null) {
            return EmptyArrays.EMPTY_STRINGS;
        } else {
            synchronized (this) {
                for (int i = 0; i < enabled.length; i++) {
                    String mapped = toJavaCipherSuite(enabled[i]);
                    if (mapped != null) {
                        enabled[i] = mapped;
                    }
                }
            }
            return enabled;
        }
    }

    @Override
    public final void setEnabledCipherSuites(String[] cipherSuites) {
        checkNotNull(cipherSuites, "cipherSuites");

        final StringBuilder buf = new StringBuilder();
        for (String c: cipherSuites) {
            if (c == null) {
                break;
            }

            String converted = CipherSuiteConverter.toOpenSsl(c);
            if (converted == null) {
                converted = c;
            }

            if (!OpenSsl.isCipherSuiteAvailable(converted)) {
                throw new IllegalArgumentException("unsupported cipher suite: " + c + '(' + converted + ')');
            }

            buf.append(converted);
            buf.append(':');
        }

        if (buf.length() == 0) {
            throw new IllegalArgumentException("empty cipher suites");
        }
        buf.setLength(buf.length() - 1);

        final String cipherSuiteSpec = buf.toString();

        synchronized (this) {
            if (!isDestroyed()) {
                try {
                    SSL.setCipherSuites(ssl, cipherSuiteSpec);
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
        return OpenSsl.SUPPORTED_PROTOCOLS_SET.toArray(new String[OpenSsl.SUPPORTED_PROTOCOLS_SET.size()]);
    }

    @Override
    public final String[] getEnabledProtocols() {
        List<String> enabled = InternalThreadLocalMap.get().arrayList();
        // Seems like there is no way to explict disable SSLv2Hello in openssl so it is always enabled
        enabled.add(OpenSsl.PROTOCOL_SSL_V2_HELLO);

        int opts;
        synchronized (this) {
            if (!isDestroyed()) {
                opts = SSL.getOptions(ssl);
            } else {
                return enabled.toArray(new String[1]);
            }
        }
        if ((opts & SSL.SSL_OP_NO_TLSv1) == 0) {
            enabled.add(OpenSsl.PROTOCOL_TLS_V1);
        }
        if ((opts & SSL.SSL_OP_NO_TLSv1_1) == 0) {
            enabled.add(OpenSsl.PROTOCOL_TLS_V1_1);
        }
        if ((opts & SSL.SSL_OP_NO_TLSv1_2) == 0) {
            enabled.add(OpenSsl.PROTOCOL_TLS_V1_2);
        }
        if ((opts & SSL.SSL_OP_NO_SSLv2) == 0) {
            enabled.add(OpenSsl.PROTOCOL_SSL_V2);
        }
        if ((opts & SSL.SSL_OP_NO_SSLv3) == 0) {
            enabled.add(OpenSsl.PROTOCOL_SSL_V3);
        }
        return enabled.toArray(new String[enabled.size()]);
    }

    @Override
    public final void setEnabledProtocols(String[] protocols) {
        if (protocols == null) {
            // This is correct from the API docs
            throw new IllegalArgumentException();
        }
        boolean sslv2 = false;
        boolean sslv3 = false;
        boolean tlsv1 = false;
        boolean tlsv1_1 = false;
        boolean tlsv1_2 = false;
        for (String p: protocols) {
            if (!OpenSsl.SUPPORTED_PROTOCOLS_SET.contains(p)) {
                throw new IllegalArgumentException("Protocol " + p + " is not supported.");
            }
            if (p.equals(OpenSsl.PROTOCOL_SSL_V2)) {
                sslv2 = true;
            } else if (p.equals(OpenSsl.PROTOCOL_SSL_V3)) {
                sslv3 = true;
            } else if (p.equals(OpenSsl.PROTOCOL_TLS_V1)) {
                tlsv1 = true;
            } else if (p.equals(OpenSsl.PROTOCOL_TLS_V1_1)) {
                tlsv1_1 = true;
            } else if (p.equals(OpenSsl.PROTOCOL_TLS_V1_2)) {
                tlsv1_2 = true;
            }
        }
        synchronized (this) {
            if (!isDestroyed()) {
                // Enable all and then disable what we not want
                SSL.setOptions(ssl, SSL.SSL_OP_ALL);

                // Clear out options which disable protocols
                SSL.clearOptions(ssl, SSL.SSL_OP_NO_SSLv2 | SSL.SSL_OP_NO_SSLv3 | SSL.SSL_OP_NO_TLSv1 |
                        SSL.SSL_OP_NO_TLSv1_1 | SSL.SSL_OP_NO_TLSv1_2);

                int opts = 0;
                if (!sslv2) {
                    opts |= SSL.SSL_OP_NO_SSLv2;
                }
                if (!sslv3) {
                    opts |= SSL.SSL_OP_NO_SSLv3;
                }
                if (!tlsv1) {
                    opts |= SSL.SSL_OP_NO_TLSv1;
                }
                if (!tlsv1_1) {
                    opts |= SSL.SSL_OP_NO_TLSv1_1;
                }
                if (!tlsv1_2) {
                    opts |= SSL.SSL_OP_NO_TLSv1_2;
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
                checkEngineClosed(BEGIN_HANDSHAKE_ENGINE_CLOSED);

                // A user did not start handshake by calling this method by him/herself,
                // but handshake has been started already by wrap() or unwrap() implicitly.
                // Because it's the user's first time to call this method, it is unfair to
                // raise an exception.  From the user's standpoint, he or she never asked
                // for renegotiation.

                handshakeState = HandshakeState.STARTED_EXPLICITLY; // Next time this method is invoked by the user,
                // we should raise an exception.
                break;
            case STARTED_EXPLICITLY:
                // Nothing to do as the handshake is not done yet.
                break;
            case FINISHED:
                if (clientMode) {
                    // Only supported for server mode at the moment.
                    throw RENEGOTIATION_UNSUPPORTED;
                }
                // For renegotiate on the server side we need to issue the following command sequence with openssl:
                //
                // SSL_renegotiate(ssl)
                // SSL_do_handshake(ssl)
                // ssl->state = SSL_ST_ACCEPT
                // SSL_do_handshake(ssl)
                //
                // Bcause of this we fall-through to call handshake() after setting the state, as this will also take
                // care of updating the internal OpenSslSession object.
                //
                // See also:
                // https://github.com/apache/httpd/blob/2.4.16/modules/ssl/ssl_engine_kernel.c#L812
                // http://h71000.www7.hp.com/doc/83final/ba554_90007/ch04s03.html
                if (SSL.renegotiate(ssl) != 1 || SSL.doHandshake(ssl) != 1) {
                    throw shutdownWithError("renegotiation failed");
                }

                SSL.setState(ssl, SSL.SSL_ST_ACCEPT);

                lastAccessed = System.currentTimeMillis();

                // fall-through
            case NOT_STARTED:
                handshakeState = HandshakeState.STARTED_EXPLICITLY;
                handshake();
                break;
            default:
                throw new Error();
        }
    }

    private void checkEngineClosed(SSLException cause) throws SSLException {
        if (engineClosed || isDestroyed()) {
            throw cause;
        }
    }

    private static SSLEngineResult.HandshakeStatus pendingStatus(int pendingStatus) {
        // Depending on if there is something left in the BIO we need to WRAP or UNWRAP
        return pendingStatus > 0 ? NEED_WRAP : NEED_UNWRAP;
    }

    private SSLEngineResult.HandshakeStatus handshake() throws SSLException {
        if (handshakeState == HandshakeState.FINISHED) {
            return FINISHED;
        }
        checkEngineClosed(HANDSHAKE_ENGINE_CLOSED);

        // Check if we have a pending handshakeException and if so see if we need to consume all pending data from the
        // BIO first or can just shutdown and throw it now.
        // This is needed so we ensure close_notify etc is correctly send to the remote peer.
        // See https://github.com/netty/netty/issues/3900
        SSLHandshakeException exception = handshakeException;
        if (exception != null) {
            if (SSL.pendingWrittenBytesInBIO(networkBIO) > 0) {
                // There is something pending, we need to consume it first via a WRAP so we not loose anything.
                return NEED_WRAP;
            }
            // No more data left to send to the remote peer, so null out the exception field, shutdown and throw
            // the exception.
            handshakeException = null;
            shutdown();
            throw exception;
        }

        // Adding the OpenSslEngine to the OpenSslEngineMap so it can be used in the AbstractCertificateVerifier.
        engineMap.add(this);
        if (lastAccessed == -1) {
            lastAccessed = System.currentTimeMillis();
        }

        if (!certificateSet && keyMaterialManager != null) {
            certificateSet = true;
            keyMaterialManager.setKeyMaterial(this);
        }

        int code = SSL.doHandshake(ssl);
        if (code <= 0) {
            // Check if we have a pending exception that was created during the handshake and if so throw it after
            // shutdown the connection.
            if (handshakeException != null) {
                exception = handshakeException;
                handshakeException = null;
                shutdown();
                throw exception;
            }

            int sslError = SSL.getError(ssl, code);

            switch (sslError) {
                case SSL.SSL_ERROR_WANT_READ:
                case SSL.SSL_ERROR_WANT_WRITE:
                    return pendingStatus(SSL.pendingWrittenBytesInBIO(networkBIO));
                default:
                    // Everything else is considered as error
                    throw shutdownWithError("SSL_do_handshake");
            }
        }
        // if SSL_do_handshake returns > 0 or sslError == SSL.SSL_ERROR_NAME it means the handshake was finished.
        session.handshakeFinished();
        engineMap.remove(ssl);
        return FINISHED;
    }

    private SSLEngineResult.Status getEngineStatus() {
        return engineClosed? CLOSED : OK;
    }

    private SSLEngineResult.HandshakeStatus mayFinishHandshake(SSLEngineResult.HandshakeStatus status)
            throws SSLException {
        if (status == NOT_HANDSHAKING && handshakeState != HandshakeState.FINISHED) {
            // If the status was NOT_HANDSHAKING and we not finished the handshake we need to call
            // SSL_do_handshake() again
            return handshake();
        }
        return status;
    }

    @Override
    public final synchronized SSLEngineResult.HandshakeStatus getHandshakeStatus() {
        // Check if we are in the initial handshake phase or shutdown phase
        return needPendingStatus() ? pendingStatus(SSL.pendingWrittenBytesInBIO(networkBIO)) : NOT_HANDSHAKING;
    }

    private SSLEngineResult.HandshakeStatus getHandshakeStatus(int pending) {
        // Check if we are in the initial handshake phase or shutdown phase
        return needPendingStatus() ? pendingStatus(pending) : NOT_HANDSHAKING;
    }

    private boolean needPendingStatus() {
        return handshakeState != HandshakeState.NOT_STARTED && !isDestroyed()
                && (handshakeState != HandshakeState.FINISHED || engineClosed);
    }

    /**
     * Converts the specified OpenSSL cipher suite to the Java cipher suite.
     */
    private String toJavaCipherSuite(String openSslCipherSuite) {
        if (openSslCipherSuite == null) {
            return null;
        }

        String prefix = toJavaCipherSuitePrefix(SSL.getVersion(ssl));
        return CipherSuiteConverter.toJava(openSslCipherSuite, prefix);
    }

    /**
     * Converts the protocol version string returned by {@link SSL#getVersion(long)} to protocol family string.
     */
    private static String toJavaCipherSuitePrefix(String protocolVersion) {
        final char c;
        if (protocolVersion == null || protocolVersion.length() == 0) {
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

    private void setClientAuth(ClientAuth mode) {
        if (clientMode) {
            return;
        }
        synchronized (this) {
            if (clientAuth == mode) {
                // No need to issue any JNI calls if the mode is the same
                return;
            }
            switch (mode) {
                case NONE:
                    SSL.setVerify(ssl, SSL.SSL_CVERIFY_NONE, OpenSslContext.VERIFY_DEPTH);
                    break;
                case REQUIRE:
                    SSL.setVerify(ssl, SSL.SSL_CVERIFY_REQUIRE, OpenSslContext.VERIFY_DEPTH);
                    break;
                case OPTIONAL:
                    SSL.setVerify(ssl, SSL.SSL_CVERIFY_OPTIONAL, OpenSslContext.VERIFY_DEPTH);
                    break;
                default:
                    throw new Error(mode.toString());
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

    @Override
    public final synchronized SSLParameters getSSLParameters() {
        SSLParameters sslParameters = super.getSSLParameters();

        int version = PlatformDependent.javaVersion();
        if (version >= 7) {
            sslParameters.setEndpointIdentificationAlgorithm(endPointIdentificationAlgorithm);
            SslParametersUtils.setAlgorithmConstraints(sslParameters, algorithmConstraints);
            if (version >= 8) {
                if (SET_SERVER_NAMES_METHOD != null && sniHostNames != null) {
                    try {
                        SET_SERVER_NAMES_METHOD.invoke(sslParameters, sniHostNames);
                    } catch (IllegalAccessException e) {
                        throw new Error(e);
                    } catch (InvocationTargetException e) {
                        throw new Error(e);
                    }
                }
                if (SET_USE_CIPHER_SUITES_ORDER_METHOD != null && !isDestroyed()) {
                    try {
                        SET_USE_CIPHER_SUITES_ORDER_METHOD.invoke(sslParameters,
                                (SSL.getOptions(ssl) & SSL.SSL_OP_CIPHER_SERVER_PREFERENCE) != 0);
                    } catch (IllegalAccessException e) {
                        throw new Error(e);
                    } catch (InvocationTargetException e) {
                        throw new Error(e);
                    }
                }
            }
        }
        return sslParameters;
    }

    @Override
    public final synchronized void setSSLParameters(SSLParameters sslParameters) {
        super.setSSLParameters(sslParameters);

        int version = PlatformDependent.javaVersion();
        if (version >= 7) {
            endPointIdentificationAlgorithm = sslParameters.getEndpointIdentificationAlgorithm();
            algorithmConstraints = sslParameters.getAlgorithmConstraints();
            if (version >= 8) {
                if (SNI_HOSTNAME_CLASS != null && clientMode && !isDestroyed()) {
                    assert GET_SERVER_NAMES_METHOD != null;
                    assert GET_ASCII_NAME_METHOD != null;
                    try {
                        List<?> servernames = (List<?>) GET_SERVER_NAMES_METHOD.invoke(sslParameters);
                        if (servernames != null) {
                            for (Object serverName : servernames) {
                                if (SNI_HOSTNAME_CLASS.isInstance(serverName)) {
                                    SSL.setTlsExtHostName(ssl, (String) GET_ASCII_NAME_METHOD.invoke(serverName));
                                } else {
                                    throw new IllegalArgumentException("Only " + SNI_HOSTNAME_CLASS.getName()
                                            + " instances are supported, but found: " +
                                            serverName);
                                }
                            }
                        }
                        sniHostNames = servernames;
                    } catch (IllegalAccessException e) {
                        throw new Error(e);
                    } catch (InvocationTargetException e) {
                        throw new Error(e);
                    }
                }
                if (GET_USE_CIPHER_SUITES_ORDER_METHOD != null && !isDestroyed()) {
                    try {
                        if ((Boolean) GET_USE_CIPHER_SUITES_ORDER_METHOD.invoke(sslParameters)) {
                            SSL.setOptions(ssl, SSL.SSL_OP_CIPHER_SERVER_PREFERENCE);
                        } else {
                            SSL.clearOptions(ssl, SSL.SSL_OP_CIPHER_SERVER_PREFERENCE);
                        }
                    } catch (IllegalAccessException e) {
                        throw new Error(e);
                    } catch (InvocationTargetException e) {
                        throw new Error(e);
                    }
                }
            }
        }
    }

    private boolean isDestroyed() {
        return destroyed != 0;
    }

    private final class OpenSslSession implements SSLSession, ApplicationProtocolAccessor {
        private final OpenSslSessionContext sessionContext;

        // These are guarded by synchronized(OpenSslEngine.this) as handshakeFinished() may be triggered by any
        // thread.
        private X509Certificate[] x509PeerCerts;
        private String protocol;
        private String applicationProtocol;
        private Certificate[] peerCerts;
        private String cipher;
        private byte[] id;
        private long creationTime;

        // lazy init for memory reasons
        private Map<String, Object> values;

        OpenSslSession(OpenSslSessionContext sessionContext) {
            this.sessionContext = sessionContext;
        }

        @Override
        public byte[] getId() {
            synchronized (ReferenceCountedOpenSslEngine.this) {
                if (id == null) {
                    return EmptyArrays.EMPTY_BYTES;
                }
                return id.clone();
            }
        }

        @Override
        public SSLSessionContext getSessionContext() {
            return sessionContext;
        }

        @Override
        public long getCreationTime() {
            synchronized (ReferenceCountedOpenSslEngine.this) {
                if (creationTime == 0 && !isDestroyed()) {
                    creationTime = SSL.getTime(ssl) * 1000L;
                }
            }
            return creationTime;
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
                if (!isDestroyed()) {
                    SSL.setTimeout(ssl, 0);
                }
            }
        }

        @Override
        public boolean isValid() {
            synchronized (ReferenceCountedOpenSslEngine.this) {
                if (!isDestroyed()) {
                    return System.currentTimeMillis() - (SSL.getTimeout(ssl) * 1000L) < (SSL.getTime(ssl) * 1000L);
                }
            }
            return false;
        }

        @Override
        public void putValue(String name, Object value) {
            if (name == null) {
                throw new NullPointerException("name");
            }
            if (value == null) {
                throw new NullPointerException("value");
            }
            Map<String, Object> values = this.values;
            if (values == null) {
                // Use size of 2 to keep the memory overhead small
                values = this.values = new HashMap<String, Object>(2);
            }
            Object old = values.put(name, value);
            if (value instanceof SSLSessionBindingListener) {
                ((SSLSessionBindingListener) value).valueBound(new SSLSessionBindingEvent(this, name));
            }
            notifyUnbound(old, name);
        }

        @Override
        public Object getValue(String name) {
            if (name == null) {
                throw new NullPointerException("name");
            }
            if (values == null) {
                return null;
            }
            return values.get(name);
        }

        @Override
        public void removeValue(String name) {
            if (name == null) {
                throw new NullPointerException("name");
            }
            Map<String, Object> values = this.values;
            if (values == null) {
                return;
            }
            Object old = values.remove(name);
            notifyUnbound(old, name);
        }

        @Override
        public String[] getValueNames() {
            Map<String, Object> values = this.values;
            if (values == null || values.isEmpty()) {
                return EmptyArrays.EMPTY_STRINGS;
            }
            return values.keySet().toArray(new String[values.size()]);
        }

        private void notifyUnbound(Object value, String name) {
            if (value instanceof SSLSessionBindingListener) {
                ((SSLSessionBindingListener) value).valueUnbound(new SSLSessionBindingEvent(this, name));
            }
        }

        /**
         * Finish the handshake and so init everything in the {@link OpenSslSession} that should be accessible by
         * the user.
         */
        void handshakeFinished() throws SSLException {
            synchronized (ReferenceCountedOpenSslEngine.this) {
                if (!isDestroyed()) {
                    id = SSL.getSessionId(ssl);
                    cipher = toJavaCipherSuite(SSL.getCipherForSSL(ssl));
                    protocol = SSL.getVersion(ssl);

                    initPeerCerts();
                    selectApplicationProtocol();

                    handshakeState = HandshakeState.FINISHED;
                } else {
                    throw new SSLException("Already closed");
                }
            }
        }

        /**
         * Init peer certificates that can be obtained via {@link #getPeerCertificateChain()}
         * and {@link #getPeerCertificates()}.
         */
        private void initPeerCerts() {
            // Return the full chain from the JNI layer.
            byte[][] chain = SSL.getPeerCertChain(ssl);
            final byte[] clientCert;
            if (!clientMode) {
                // if used on the server side SSL_get_peer_cert_chain(...) will not include the remote peer
                // certificate. We use SSL_get_peer_certificate to get it in this case and add it to our
                // array later.
                //
                // See https://www.openssl.org/docs/ssl/SSL_get_peer_cert_chain.html
                clientCert = SSL.getPeerCertificate(ssl);
            } else {
                clientCert = null;
            }

            if (chain == null || chain.length == 0) {
                if (clientCert == null || clientCert.length == 0) {
                    peerCerts = EmptyArrays.EMPTY_CERTIFICATES;
                    x509PeerCerts = EmptyArrays.EMPTY_JAVAX_X509_CERTIFICATES;
                } else {
                    peerCerts = new Certificate[1];
                    x509PeerCerts = new X509Certificate[1];

                    peerCerts[0] = new OpenSslX509Certificate(clientCert);
                    x509PeerCerts[0] = new OpenSslJavaxX509Certificate(clientCert);
                }
            } else if (clientCert == null || clientCert.length == 0) {
                peerCerts = new Certificate[chain.length];
                x509PeerCerts = new X509Certificate[chain.length];

                for (int a = 0; a < chain.length; ++a) {
                    byte[] bytes = chain[a];
                    peerCerts[a] = new OpenSslX509Certificate(bytes);
                    x509PeerCerts[a] = new OpenSslJavaxX509Certificate(bytes);
                }
            } else {
                int len = clientCert.length + 1;
                peerCerts = new Certificate[len];
                x509PeerCerts = new X509Certificate[len];

                peerCerts[0] = new OpenSslX509Certificate(clientCert);
                x509PeerCerts[0] = new OpenSslJavaxX509Certificate(clientCert);

                for (int a = 0, i = 1; a < chain.length; ++a, ++i) {
                    byte[] bytes = chain[a];
                    peerCerts[i] = new OpenSslX509Certificate(bytes);
                    x509PeerCerts[i] = new OpenSslJavaxX509Certificate(bytes);
                }
            }
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
                        this.applicationProtocol = selectApplicationProtocol(
                                protocols, behavior, applicationProtocol);
                    }
                    break;
                case NPN:
                    applicationProtocol = SSL.getNextProtoNegotiated(ssl);
                    if (applicationProtocol != null) {
                        this.applicationProtocol = selectApplicationProtocol(
                                protocols, behavior, applicationProtocol);
                    }
                    break;
                case NPN_AND_ALPN:
                    applicationProtocol = SSL.getAlpnSelected(ssl);
                    if (applicationProtocol == null) {
                        applicationProtocol = SSL.getNextProtoNegotiated(ssl);
                    }
                    if (applicationProtocol != null) {
                        this.applicationProtocol = selectApplicationProtocol(
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

        @Override
        public Certificate[] getPeerCertificates() throws SSLPeerUnverifiedException {
            synchronized (ReferenceCountedOpenSslEngine.this) {
                if (peerCerts == null || peerCerts.length == 0) {
                    throw new SSLPeerUnverifiedException("peer not verified");
                }
                return peerCerts.clone();
            }
        }

        @Override
        public Certificate[] getLocalCertificates() {
            if (localCerts == null) {
                return null;
            }
            return localCerts.clone();
        }

        @Override
        public X509Certificate[] getPeerCertificateChain() throws SSLPeerUnverifiedException {
            synchronized (ReferenceCountedOpenSslEngine.this) {
                if (x509PeerCerts == null || x509PeerCerts.length == 0) {
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
            Certificate[] local = localCerts;
            if (local == null || local.length == 0) {
                return null;
            }
            return ((java.security.cert.X509Certificate) local[0]).getIssuerX500Principal();
        }

        @Override
        public String getCipherSuite() {
            synchronized (ReferenceCountedOpenSslEngine.this) {
                if (cipher == null) {
                    return INVALID_CIPHER;
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
        public String getApplicationProtocol() {
            synchronized (ReferenceCountedOpenSslEngine.this) {
                return applicationProtocol;
            }
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
            return MAX_ENCRYPTED_PACKET_LENGTH;
        }

        @Override
        public int getApplicationBufferSize() {
            return MAX_PLAINTEXT_LENGTH;
        }
    }
}

