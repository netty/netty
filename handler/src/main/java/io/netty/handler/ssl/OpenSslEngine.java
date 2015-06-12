/*
 * Copyright 2014 The Netty Project
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
import io.netty.util.internal.EmptyArrays;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.apache.tomcat.jni.Buffer;
import org.apache.tomcat.jni.SSL;

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
import javax.security.cert.CertificateException;
import javax.security.cert.X509Certificate;
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.security.Principal;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import static io.netty.handler.ssl.ApplicationProtocolConfig.SelectedListenerFailureBehavior;
import static io.netty.util.internal.ObjectUtil.checkNotNull;
import static javax.net.ssl.SSLEngineResult.HandshakeStatus.*;
import static javax.net.ssl.SSLEngineResult.Status.*;

/**
 * Implements a {@link SSLEngine} using
 * <a href="https://www.openssl.org/docs/crypto/BIO_s_bio.html#EXAMPLE">OpenSSL BIO abstractions</a>.
 */
public final class OpenSslEngine extends SSLEngine {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(OpenSslEngine.class);

    private static final Certificate[] EMPTY_CERTIFICATES = EmptyArrays.EMPTY_CERTIFICATES;
    private static final X509Certificate[] EMPTY_X509_CERTIFICATES = EmptyArrays.EMPTY_JAVAX_X509_CERTIFICATES;

    private static final SSLException ENGINE_CLOSED = new SSLException("engine closed");
    private static final SSLException RENEGOTIATION_UNSUPPORTED = new SSLException("renegotiation unsupported");
    private static final SSLException ENCRYPTED_PACKET_OVERSIZED = new SSLException("encrypted packet oversized");
    static {
        ENGINE_CLOSED.setStackTrace(EmptyArrays.EMPTY_STACK_TRACE);
        RENEGOTIATION_UNSUPPORTED.setStackTrace(EmptyArrays.EMPTY_STACK_TRACE);
        ENCRYPTED_PACKET_OVERSIZED.setStackTrace(EmptyArrays.EMPTY_STACK_TRACE);

        AtomicIntegerFieldUpdater<OpenSslEngine> destroyedUpdater =
                PlatformDependent.newAtomicIntegerFieldUpdater(OpenSslEngine.class, "destroyed");
        if (destroyedUpdater == null) {
            destroyedUpdater = AtomicIntegerFieldUpdater.newUpdater(OpenSslEngine.class, "destroyed");
        }
        DESTROYED_UPDATER = destroyedUpdater;
    }

    private static final int MAX_PLAINTEXT_LENGTH = 16 * 1024; // 2^14
    private static final int MAX_COMPRESSED_LENGTH = MAX_PLAINTEXT_LENGTH + 1024;
    private static final int MAX_CIPHERTEXT_LENGTH = MAX_COMPRESSED_LENGTH + 1024;

    // Protocols
    private static final String PROTOCOL_SSL_V2_HELLO = "SSLv2Hello";
    private static final String PROTOCOL_SSL_V2 = "SSLv2";
    private static final String PROTOCOL_SSL_V3 = "SSLv3";
    private static final String PROTOCOL_TLS_V1 = "TLSv1";
    private static final String PROTOCOL_TLS_V1_1 = "TLSv1.1";
    private static final String PROTOCOL_TLS_V1_2 = "TLSv1.2";

    private static final String[] SUPPORTED_PROTOCOLS = {
            PROTOCOL_SSL_V2_HELLO,
            PROTOCOL_SSL_V2,
            PROTOCOL_SSL_V3,
            PROTOCOL_TLS_V1,
            PROTOCOL_TLS_V1_1,
            PROTOCOL_TLS_V1_2
    };
    private static final Set<String> SUPPORTED_PROTOCOLS_SET = new HashSet<String>(Arrays.asList(SUPPORTED_PROTOCOLS));

    // Header (5) + Data (2^14) + Compression (1024) + Encryption (1024) + MAC (20) + Padding (256)
    static final int MAX_ENCRYPTED_PACKET_LENGTH = MAX_CIPHERTEXT_LENGTH + 5 + 20 + 256;

    static final int MAX_ENCRYPTION_OVERHEAD_LENGTH = MAX_ENCRYPTED_PACKET_LENGTH - MAX_PLAINTEXT_LENGTH;

    enum ClientAuthMode {
        NONE,
        OPTIONAL,
        REQUIRE,
    }

    private static final AtomicIntegerFieldUpdater<OpenSslEngine> DESTROYED_UPDATER;

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

    /**
     * 0 - not accepted, 1 - accepted implicitly via wrap()/unwrap(), 2 - accepted explicitly via beginHandshake() call
     */
    private int accepted;
    private boolean handshakeFinished;
    private boolean receivedShutdown;
    @SuppressWarnings("UnusedDeclaration")
    private volatile int destroyed;

    // Use an invalid cipherSuite until the handshake is completed
    // See http://docs.oracle.com/javase/7/docs/api/javax/net/ssl/SSLEngine.html#getSession()
    private volatile String cipher;
    private volatile String applicationProtocol;

    // We store this outside of the SslSession so we not need to create an instance during verifyCertificates(...)
    private volatile Certificate[] peerCerts;
    private volatile ClientAuthMode clientAuth = ClientAuthMode.NONE;

    private volatile String endPointIdentificationAlgorithm;
    // Store as object as AlgorithmConstraints only exists since java 7.
    private volatile Object algorithmConstraints;

    // SSL Engine status variables
    private boolean isInboundDone;
    private boolean isOutboundDone;
    private boolean engineClosed;

    private final boolean clientMode;
    private final ByteBufAllocator alloc;
    private final OpenSslSessionContext sessionContext;
    private final OpenSslEngineMap engineMap;
    private final OpenSslApplicationProtocolNegotiator apn;
    private final boolean rejectRemoteInitiatedRenegation;
    private final SSLSession session = new OpenSslSession();

    // This is package-private as we set it from OpenSslContext if an exception is thrown during
    // the verification step.
    SSLHandshakeException handshakeException;

    /**
     * Creates a new instance
     *
     * @param sslCtx an OpenSSL {@code SSL_CTX} object
     * @param alloc the {@link ByteBufAllocator} that will be used by this engine
     */
    @Deprecated
    public OpenSslEngine(long sslCtx, ByteBufAllocator alloc,
                         @SuppressWarnings("unused") String fallbackApplicationProtocol) {
        this(sslCtx, alloc, false, null, OpenSslContext.NONE_PROTOCOL_NEGOTIATOR, OpenSslEngineMap.EMPTY, false);
    }

    /**
     * Creates a new instance
     *
     * @param sslCtx an OpenSSL {@code SSL_CTX} object
     * @param alloc the {@link ByteBufAllocator} that will be used by this engine
     * @param clientMode {@code true} if this is used for clients, {@code false} otherwise
     * @param sessionContext the {@link OpenSslSessionContext} this {@link SSLEngine} belongs to.
     */
    OpenSslEngine(long sslCtx, ByteBufAllocator alloc,
                  boolean clientMode, OpenSslSessionContext sessionContext,
                  OpenSslApplicationProtocolNegotiator apn, OpenSslEngineMap engineMap,
                  boolean rejectRemoteInitiatedRenegation) {
        this(sslCtx, alloc, clientMode, sessionContext, apn, engineMap, rejectRemoteInitiatedRenegation, null, -1);
    }

    OpenSslEngine(long sslCtx, ByteBufAllocator alloc,
                  boolean clientMode, OpenSslSessionContext sessionContext,
                  OpenSslApplicationProtocolNegotiator apn, OpenSslEngineMap engineMap,
                  boolean rejectRemoteInitiatedRenegation, String peerHost, int peerPort) {
        super(peerHost, peerPort);
        OpenSsl.ensureAvailability();
        if (sslCtx == 0) {
            throw new NullPointerException("sslCtx");
        }

        this.alloc = checkNotNull(alloc, "alloc");
        this.apn = checkNotNull(apn, "apn");
        ssl = SSL.newSSL(sslCtx, !clientMode);
        networkBIO = SSL.makeNetworkBIO(ssl);
        this.clientMode = clientMode;
        this.sessionContext = sessionContext;
        this.engineMap = engineMap;
        this.rejectRemoteInitiatedRenegation = rejectRemoteInitiatedRenegation;
    }

    @Override
    public SSLSession getHandshakeSession() {
        if (accepted > 0) {
            // handshake started we are able to return the session.
            return session;
        }
        // As stated by the javadocs of getHandshakeSession() we should return null if the handshake not started yet.
        return null;
    }

    /**
     * Returns the pointer to the {@code SSL} object for this {@link OpenSslEngine}.
     * Be aware that it is freed as soon as the {@link #finalize()} or {@link #shutdown} method is called.
     * At this point {@code 0} will be returned.
     */
    public synchronized long sslPointer() {
        return ssl;
    }

    /**
     * Destroys this engine.
     */
    public synchronized void shutdown() {
        if (DESTROYED_UPDATER.compareAndSet(this, 0, 1)) {
            engineMap.remove(ssl);
            SSL.freeSSL(ssl);
            SSL.freeBIO(networkBIO);
            ssl = networkBIO = 0;

            // internal errors can cause shutdown without marking the engine closed
            isInboundDone = isOutboundDone = engineClosed = true;
        }
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
                return sslWrote;
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
                    return sslWrote;
                } else {
                    src.position(pos);
                }
            } finally {
                buf.release();
            }
        }

        throw new IllegalStateException("SSL.writeToSSL() returned a non-positive value: " + sslWrote);
    }

    /**
     * Write encrypted data to the OpenSSL network BIO.
     */
    private int writeEncryptedData(final ByteBuffer src) {
        final int pos = src.position();
        final int len = src.remaining();
        if (src.isDirect()) {
            final long addr = Buffer.address(src) + pos;
            final int netWrote = SSL.writeToBIO(networkBIO, addr, len);
            if (netWrote >= 0) {
                src.position(pos + netWrote);
                return netWrote;
            }
        } else {
            final ByteBuf buf = alloc.directBuffer(len);
            try {
                final long addr = memoryAddress(buf);

                buf.setBytes(0, src);

                final int netWrote = SSL.writeToBIO(networkBIO, addr, len);
                if (netWrote >= 0) {
                    src.position(pos + netWrote);
                    return netWrote;
                } else {
                    src.position(pos);
                }
            } finally {
                buf.release();
            }
        }

        return -1;
    }

    /**
     * Read plaintext data from the OpenSSL internal BIO
     */
    private int readPlaintextData(final ByteBuffer dst) {
        if (dst.isDirect()) {
            final int pos = dst.position();
            final long addr = Buffer.address(dst) + pos;
            final int len = dst.limit() - pos;
            final int sslRead = SSL.readFromSSL(ssl, addr, len);
            if (sslRead > 0) {
                dst.position(pos + sslRead);
                return sslRead;
            }
        } else {
            final int pos = dst.position();
            final int limit = dst.limit();
            final int len = Math.min(MAX_ENCRYPTED_PACKET_LENGTH, limit - pos);
            final ByteBuf buf = alloc.directBuffer(len);
            try {
                final long addr = memoryAddress(buf);

                final int sslRead = SSL.readFromSSL(ssl, addr, len);
                if (sslRead > 0) {
                    dst.limit(pos + sslRead);
                    buf.getBytes(0, dst);
                    dst.limit(limit);
                    return sslRead;
                }
            } finally {
                buf.release();
            }
        }

        return 0;
    }

    /**
     * Read encrypted data from the OpenSSL network BIO
     */
    private int readEncryptedData(final ByteBuffer dst, final int pending) {
        if (dst.isDirect() && dst.remaining() >= pending) {
            final int pos = dst.position();
            final long addr = Buffer.address(dst) + pos;
            final int bioRead = SSL.readFromBIO(networkBIO, addr, pending);
            if (bioRead > 0) {
                dst.position(pos + bioRead);
                return bioRead;
            }
        } else {
            final ByteBuf buf = alloc.directBuffer(pending);
            try {
                final long addr = memoryAddress(buf);

                final int bioRead = SSL.readFromBIO(networkBIO, addr, pending);
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

        return 0;
    }

    @Override
    public synchronized SSLEngineResult wrap(
            final ByteBuffer[] srcs, final int offset, final int length, final ByteBuffer dst) throws SSLException {

        // Check to make sure the engine has not been closed
        if (destroyed != 0) {
            return CLOSED_NOT_HANDSHAKING;
        }

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

        // Prepare OpenSSL to work in server mode and receive handshake
        if (accepted == 0) {
            beginHandshakeImplicitly();
        }

        // In handshake or close_notify stages, check if call to wrap was made
        // without regard to the handshake status.
        SSLEngineResult.HandshakeStatus handshakeStatus = handshakeStatus0();

        if (handshakeStatus == NEED_UNWRAP) {
            if (!handshakeFinished) {
                return NEED_UNWRAP_OK;
            }
            if (engineClosed) {
                return NEED_UNWRAP_CLOSED;
            }
        }

        int bytesProduced = 0;
        int pendingNet;

        // Check for pending data in the network BIO
        pendingNet = SSL.pendingWrittenBytesInBIO(networkBIO);
        if (pendingNet > 0) {
            // Do we have enough room in dst to write encrypted data?
            int capacity = dst.remaining();
            if (capacity < pendingNet) {
                return new SSLEngineResult(BUFFER_OVERFLOW, handshakeStatus, 0, bytesProduced);
            }

            // Write the pending data from the network BIO into the dst buffer
            try {
                bytesProduced += readEncryptedData(dst, pendingNet);
            } catch (Exception e) {
                throw new SSLException(e);
            }

            // If isOuboundDone is set, then the data from the network BIO
            // was the close_notify message -- we are not required to wait
            // for the receipt the peer's close_notify message -- shutdown.
            if (isOutboundDone) {
                shutdown();
            }

            return new SSLEngineResult(getEngineStatus(), handshakeStatus0(), 0, bytesProduced);
        }

        // There was no pending data in the network BIO -- encrypt any application data
        int bytesConsumed = 0;
        int endOffset = offset + length;
        for (int i = offset; i < endOffset; ++ i) {
            final ByteBuffer src = srcs[i];
            if (src == null) {
                throw new IllegalArgumentException("srcs[" + i + "] is null");
            }
            while (src.hasRemaining()) {

                // Write plaintext application data to the SSL engine
                try {
                    bytesConsumed += writePlaintextData(src);
                } catch (Exception e) {
                    throw new SSLException(e);
                }

                // Check to see if the engine wrote data into the network BIO
                pendingNet = SSL.pendingWrittenBytesInBIO(networkBIO);
                if (pendingNet > 0) {
                    // Do we have enough room in dst to write encrypted data?
                    int capacity = dst.remaining();
                    if (capacity < pendingNet) {
                        return new SSLEngineResult(
                                BUFFER_OVERFLOW, handshakeStatus0(), bytesConsumed, bytesProduced);
                    }

                    // Write the pending data from the network BIO into the dst buffer
                    try {
                        bytesProduced += readEncryptedData(dst, pendingNet);
                    } catch (Exception e) {
                        throw new SSLException(e);
                    }

                    return new SSLEngineResult(getEngineStatus(), handshakeStatus0(), bytesConsumed, bytesProduced);
                }
            }
        }

        return new SSLEngineResult(getEngineStatus(), handshakeStatus0(), bytesConsumed, bytesProduced);
    }

    private SSLException newSSLException(String msg) {
        if (!handshakeFinished) {
            return new SSLHandshakeException(msg);
        }
        return new SSLException(msg);
    }

    private void checkPendingHandshakeException() throws SSLHandshakeException {
        if (handshakeException != null) {
            SSLHandshakeException exception = handshakeException;
            handshakeException = null;
            shutdown();
            throw exception;
        }
    }

    public synchronized SSLEngineResult unwrap(
            final ByteBuffer[] srcs, int srcsOffset, final int srcsLength,
            final ByteBuffer[] dsts, final int dstsOffset, final int dstsLength) throws SSLException {

        // Check to make sure the engine has not been closed
        if (destroyed != 0) {
            return CLOSED_NOT_HANDSHAKING;
        }

        // Throw requried runtime exceptions
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
        int capacity = 0;
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

        // Prepare OpenSSL to work in server mode and receive handshake
        if (accepted == 0) {
            beginHandshakeImplicitly();
        }

        // In handshake or close_notify stages, check if call to unwrap was made
        // without regard to the handshake status.
        SSLEngineResult.HandshakeStatus handshakeStatus = handshakeStatus0();
        if (handshakeStatus == NEED_WRAP) {
            if (!handshakeFinished) {
                return NEED_WRAP_OK;
            }
            if (engineClosed) {
                return NEED_WRAP_CLOSED;
            }
        }

        final int srcsEndOffset = srcsOffset + srcsLength;
        int len = 0;
        for (int i = srcsOffset; i < srcsEndOffset; i++) {
            ByteBuffer src = srcs[i];
            if (src == null) {
                throw new IllegalArgumentException("srcs[" + i + "] is null");
            }
            len += src.remaining();
        }

        // protect against protocol overflow attack vector
        if (len > MAX_ENCRYPTED_PACKET_LENGTH) {
            isInboundDone = true;
            isOutboundDone = true;
            engineClosed = true;
            shutdown();
            throw ENCRYPTED_PACKET_OVERSIZED;
        }

        // Write encrypted data to network BIO
        int bytesConsumed = -1;
        try {
            while (srcsOffset < srcsEndOffset) {
                ByteBuffer src = srcs[srcsOffset];
                int remaining = src.remaining();
                int written = writeEncryptedData(src);
                if (written >= 0) {
                    if (bytesConsumed == -1) {
                        bytesConsumed = written;
                    } else {
                        bytesConsumed += written;
                    }
                    if (written == remaining) {
                        srcsOffset ++;
                    } else if (written == 0) {
                        break;
                    }
                } else {
                    break;
                }
            }
        } catch (Exception e) {
            throw new SSLException(e);
        }
        if (bytesConsumed >= 0) {
            int lastPrimingReadResult = SSL.readFromSSL(ssl, EMPTY_ADDR, 0); // priming read

            // check if SSL_read returned <= 0. In this case we need to check the error and see if it was something
            // fatal.
            if (lastPrimingReadResult <= 0) {
                // Check for OpenSSL errors caused by the priming read
                long error = SSL.getLastErrorNumber();
                if (OpenSsl.isError(error)) {
                    String err = SSL.getErrorString(error);
                    if (logger.isDebugEnabled()) {
                        logger.debug(
                                "SSL_read failed: primingReadResult: " + lastPrimingReadResult +
                                        "; OpenSSL error: '" + err + '\'');
                    }

                    // There was an internal error -- shutdown
                    shutdown();
                    throw newSSLException(err);
                } else {
                    checkPendingHandshakeException();
                }
            }

            rejectRemoteInitiatedRenegation();
        } else {
            // Reset to 0 as -1 is used to signal that nothing was written and no priming read needs to be done
            bytesConsumed = 0;
        }

        // There won't be any application data until we're done handshaking
        //
        // We first check handshakeFinished to eliminate the overhead of extra JNI call if possible.
        int pendingApp = (handshakeFinished || SSL.isInInit(ssl) == 0) ? SSL.pendingReadableBytesInSSL(ssl) : 0;
        int bytesProduced = 0;

        if (pendingApp > 0) {
            // Do we have enough room in dsts to write decrypted data?
            if (capacity < pendingApp) {
                return new SSLEngineResult(BUFFER_OVERFLOW, handshakeStatus0(), bytesConsumed, 0);
            }

            // Write decrypted data to dsts buffers
            int idx = dstsOffset;
            while (idx < endOffset) {
                ByteBuffer dst = dsts[idx];
                if (!dst.hasRemaining()) {
                    idx ++;
                    continue;
                }

                if (pendingApp <= 0) {
                    break;
                }

                int bytesRead;
                try {
                    bytesRead = readPlaintextData(dst);
                } catch (Exception e) {
                    throw new SSLException(e);
                }

                rejectRemoteInitiatedRenegation();

                if (bytesRead == 0) {
                    break;
                }
                bytesProduced += bytesRead;
                pendingApp -= bytesRead;

                if (!dst.hasRemaining()) {
                    idx ++;
                }
            }
        }

        // Check to see if we received a close_notify message from the peer
        if (!receivedShutdown && (SSL.getShutdown(ssl) & SSL.SSL_RECEIVED_SHUTDOWN) == SSL.SSL_RECEIVED_SHUTDOWN) {
            receivedShutdown = true;
            closeOutbound();
            closeInbound();
        }

        return new SSLEngineResult(getEngineStatus(), handshakeStatus0(), bytesConsumed, bytesProduced);
    }

    private void rejectRemoteInitiatedRenegation() throws SSLHandshakeException {
        if (rejectRemoteInitiatedRenegation && SSL.getHandshakeCount(ssl) > 1) {
            // TODO: In future versions me may also want to send a fatal_alert to the client and so notify it
            // that the renegotiation failed.
            shutdown();
            throw new SSLHandshakeException("remote-initiated renegotation not allowed");
        }
    }

    public SSLEngineResult unwrap(final ByteBuffer[] srcs, final ByteBuffer[] dsts) throws SSLException {
        return unwrap(srcs, 0, srcs.length, dsts, 0, dsts.length);
    }

    @Override
    public SSLEngineResult unwrap(
            final ByteBuffer src, final ByteBuffer[] dsts, final int offset, final int length) throws SSLException {
        return unwrap(new ByteBuffer[] { src }, 0, 1, dsts, offset, length);
    }

    @Override
    public Runnable getDelegatedTask() {
        // Currently, we do not delegate SSL computation tasks
        // TODO: in the future, possibly create tasks to do encrypt / decrypt async

        return null;
    }

    @Override
    public synchronized void closeInbound() throws SSLException {
        if (isInboundDone) {
            return;
        }

        isInboundDone = true;
        engineClosed = true;

        shutdown();

        if (accepted != 0 && !receivedShutdown) {
            throw new SSLException(
                    "Inbound closed before receiving peer's close_notify: possible truncation attack?");
        }
    }

    @Override
    public synchronized boolean isInboundDone() {
        return isInboundDone || engineClosed;
    }

    @Override
    public synchronized void closeOutbound() {
        if (isOutboundDone) {
            return;
        }

        isOutboundDone = true;
        engineClosed = true;

        if (accepted != 0 && destroyed == 0) {
            int mode = SSL.getShutdown(ssl);
            if ((mode & SSL.SSL_SENT_SHUTDOWN) != SSL.SSL_SENT_SHUTDOWN) {
                SSL.shutdownSSL(ssl);
            }
        } else {
            // engine closing before initial handshake
            shutdown();
        }
    }

    @Override
    public synchronized boolean isOutboundDone() {
        return isOutboundDone;
    }

    @Override
    public String[] getSupportedCipherSuites() {
        Set<String> availableCipherSuites = OpenSsl.availableCipherSuites();
        return availableCipherSuites.toArray(new String[availableCipherSuites.size()]);
    }

    @Override
    public String[] getEnabledCipherSuites() {
        final String[] enabled;
        synchronized (this) {
            if (destroyed == 0) {
                enabled = SSL.getCiphers(ssl);
            } else {
                return EmptyArrays.EMPTY_STRINGS;
            }
        }
        if (enabled == null) {
            return EmptyArrays.EMPTY_STRINGS;
        } else {
            for (int i = 0; i < enabled.length; i++) {
                String mapped = toJavaCipherSuite(enabled[i]);
                if (mapped != null) {
                    enabled[i] = mapped;
                }
            }
            return enabled;
        }
    }

    @Override
    public void setEnabledCipherSuites(String[] cipherSuites) {
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
            if (destroyed == 0) {
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
    public String[] getSupportedProtocols() {
        return SUPPORTED_PROTOCOLS.clone();
    }

    @Override
    public String[] getEnabledProtocols() {
        List<String> enabled = new ArrayList<String>();
        // Seems like there is no way to explict disable SSLv2Hello in openssl so it is always enabled
        enabled.add(PROTOCOL_SSL_V2_HELLO);

        int opts;
        synchronized (this) {
            if (destroyed == 0) {
                opts = SSL.getOptions(ssl);
            } else {
                return enabled.toArray(new String[1]);
            }
        }
        if ((opts & SSL.SSL_OP_NO_TLSv1) == 0) {
            enabled.add(PROTOCOL_TLS_V1);
        }
        if ((opts & SSL.SSL_OP_NO_TLSv1_1) == 0) {
            enabled.add(PROTOCOL_TLS_V1_1);
        }
        if ((opts & SSL.SSL_OP_NO_TLSv1_2) == 0) {
            enabled.add(PROTOCOL_TLS_V1_2);
        }
        if ((opts & SSL.SSL_OP_NO_SSLv2) == 0) {
            enabled.add(PROTOCOL_SSL_V2);
        }
        if ((opts & SSL.SSL_OP_NO_SSLv3) == 0) {
            enabled.add(PROTOCOL_SSL_V3);
        }
        return enabled.toArray(new String[enabled.size()]);
    }

    @Override
    public void setEnabledProtocols(String[] protocols) {
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
            if (!SUPPORTED_PROTOCOLS_SET.contains(p)) {
                throw new IllegalArgumentException("Protocol " + p + " is not supported.");
            }
            if (p.equals(PROTOCOL_SSL_V2)) {
                sslv2 = true;
            } else if (p.equals(PROTOCOL_SSL_V3)) {
                sslv3 = true;
            } else if (p.equals(PROTOCOL_TLS_V1)) {
                tlsv1 = true;
            } else if (p.equals(PROTOCOL_TLS_V1_1)) {
                tlsv1_1 = true;
            } else if (p.equals(PROTOCOL_TLS_V1_2)) {
                tlsv1_2 = true;
            }
        }
        synchronized (this) {
            if (destroyed == 0) {
                // Enable all and then disable what we not want
                SSL.setOptions(ssl, SSL.SSL_OP_ALL);

                if (!sslv2) {
                    SSL.setOptions(ssl, SSL.SSL_OP_NO_SSLv2);
                }
                if (!sslv3) {
                    SSL.setOptions(ssl, SSL.SSL_OP_NO_SSLv3);
                }
                if (!tlsv1) {
                    SSL.setOptions(ssl, SSL.SSL_OP_NO_TLSv1);
                }
                if (!tlsv1_1) {
                    SSL.setOptions(ssl, SSL.SSL_OP_NO_TLSv1_1);
                }
                if (!tlsv1_2) {
                    SSL.setOptions(ssl, SSL.SSL_OP_NO_TLSv1_2);
                }
            } else {
                throw new IllegalStateException("failed to enable protocols: " + Arrays.asList(protocols));
            }
        }
    }

    @Override
    public SSLSession getSession() {
        return session;
    }

    @Override
    public synchronized void beginHandshake() throws SSLException {
        if (engineClosed || destroyed != 0) {
            throw ENGINE_CLOSED;
        }
        switch (accepted) {
            case 0:
                handshake();
                accepted = 2;
                break;
            case 1:
                // A user did not start handshake by calling this method by him/herself,
                // but handshake has been started already by wrap() or unwrap() implicitly.
                // Because it's the user's first time to call this method, it is unfair to
                // raise an exception.  From the user's standpoint, he or she never asked
                // for renegotiation.

                accepted = 2; // Next time this method is invoked by the user, we should raise an exception.
                break;
            case 2:
                throw RENEGOTIATION_UNSUPPORTED;
            default:
                throw new Error();
        }
    }

    private void beginHandshakeImplicitly() throws SSLException {
        if (engineClosed || destroyed != 0) {
            throw ENGINE_CLOSED;
        }

        if (accepted == 0) {
            handshake();
            accepted = 1;
        }
    }

    private void handshake() throws SSLException {
        int code = SSL.doHandshake(ssl);
        if (code <= 0) {
            // Check for OpenSSL errors caused by the handshake
            long error = SSL.getLastErrorNumber();
            if (OpenSsl.isError(error)) {
                String err = SSL.getErrorString(error);
                if (logger.isDebugEnabled()) {
                    logger.debug(
                            "SSL_do_handshake failed: OpenSSL error: '" + err + '\'');
                }

                // There was an internal error -- shutdown
                shutdown();
                throw newSSLException(err);
            }
            checkPendingHandshakeException();
        } else {
            // if SSL_do_handshake returns > 0 it means the handshake was finished. This means we can update
            // handshakeFinished directly and so eliminate uncessary calls to SSL.isInInit(...)
            handshakeFinished();
        }
    }

    private static long memoryAddress(ByteBuf buf) {
        if (buf.hasMemoryAddress()) {
            return buf.memoryAddress();
        } else {
            return Buffer.address(buf.nioBuffer());
        }
    }

    private void handshakeFinished() throws SSLException {
        SelectedListenerFailureBehavior behavior = apn.selectedListenerFailureBehavior();
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
                    this.applicationProtocol = selectApplicationProtocol(protocols, behavior, applicationProtocol);
                }
                break;
            case NPN:
                applicationProtocol = SSL.getNextProtoNegotiated(ssl);
                if (applicationProtocol != null) {
                    this.applicationProtocol = selectApplicationProtocol(protocols, behavior, applicationProtocol);
                }
                break;
            case NPN_AND_ALPN:
                applicationProtocol = SSL.getAlpnSelected(ssl);
                if (applicationProtocol == null) {
                    applicationProtocol = SSL.getNextProtoNegotiated(ssl);
                }
                if (applicationProtocol != null) {
                    this.applicationProtocol = selectApplicationProtocol(protocols, behavior, applicationProtocol);
                }
                break;
            default:
                throw new Error();
        }
        handshakeFinished = true;
    }

    private static String selectApplicationProtocol(List<String> protocols,
                                             SelectedListenerFailureBehavior behavior,
                                             String applicationProtocol) throws SSLException {
        if (behavior == SelectedListenerFailureBehavior.ACCEPT) {
            return applicationProtocol;
        } else {
            int size = protocols.size();
            assert size > 0;
            if (protocols.contains(applicationProtocol)) {
                return applicationProtocol;
            } else {
                if (behavior == SelectedListenerFailureBehavior.CHOOSE_MY_LAST_PROTOCOL) {
                    return protocols.get(size - 1);
                } else {
                    throw new SSLException("unknown protocol " + applicationProtocol);
                }
            }
        }
    }

    private SSLEngineResult.Status getEngineStatus() {
        return engineClosed? CLOSED : OK;
    }

    private SSLEngineResult.HandshakeStatus handshakeStatus0() throws SSLException {
        SSLEngineResult.HandshakeStatus status = getHandshakeStatus();
        if (status == FINISHED) {
            handshakeFinished();
        }
        checkPendingHandshakeException();

        return status;
    }

    @Override
    public synchronized SSLEngineResult.HandshakeStatus getHandshakeStatus() {
        if (accepted == 0 || destroyed != 0) {
            return NOT_HANDSHAKING;
        }

        // Check if we are in the initial handshake phase
        if (!handshakeFinished) {
            // There is pending data in the network BIO -- call wrap
            if (SSL.pendingWrittenBytesInBIO(networkBIO) != 0) {
                return NEED_WRAP;
            }

            // No pending data to be sent to the peer
            // Check to see if we have finished handshaking
            if (SSL.isInInit(ssl) == 0) {
                return FINISHED;
            }

            // No pending data and still handshaking
            // Must be waiting on the peer to send more data
            return NEED_UNWRAP;
        }

        // Check if we are in the shutdown phase
        if (engineClosed) {
            // Waiting to send the close_notify message
            if (SSL.pendingWrittenBytesInBIO(networkBIO) != 0) {
                return NEED_WRAP;
            }

            // Must be waiting to receive the close_notify message
            return NEED_UNWRAP;
        }

        return NOT_HANDSHAKING;
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
    public void setUseClientMode(boolean clientMode) {
        if (clientMode != this.clientMode) {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public boolean getUseClientMode() {
        return clientMode;
    }

    @Override
    public void setNeedClientAuth(boolean b) {
        setClientAuth(b ? ClientAuthMode.REQUIRE : ClientAuthMode.NONE);
    }

    @Override
    public boolean getNeedClientAuth() {
        return clientAuth == ClientAuthMode.REQUIRE;
    }

    @Override
    public void setWantClientAuth(boolean b) {
        setClientAuth(b ? ClientAuthMode.OPTIONAL : ClientAuthMode.NONE);
    }

    @Override
    public boolean getWantClientAuth() {
        return clientAuth == ClientAuthMode.OPTIONAL;
    }

    private void setClientAuth(ClientAuthMode mode) {
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
            }
            clientAuth = mode;
        }
    }

    @Override
    public void setEnableSessionCreation(boolean b) {
        if (b) {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public boolean getEnableSessionCreation() {
        return false;
    }

    @Override
    public SSLParameters getSSLParameters() {
        SSLParameters sslParameters = super.getSSLParameters();

        if (PlatformDependent.javaVersion() >= 7) {
            sslParameters.setEndpointIdentificationAlgorithm(endPointIdentificationAlgorithm);
            SslParametersUtils.setAlgorithmConstraints(sslParameters, algorithmConstraints);
        }
        return sslParameters;
    }

    @Override
    public void setSSLParameters(SSLParameters sslParameters) {
        super.setSSLParameters(sslParameters);

        if (PlatformDependent.javaVersion() >= 7) {
            endPointIdentificationAlgorithm = sslParameters.getEndpointIdentificationAlgorithm();
            algorithmConstraints = sslParameters.getAlgorithmConstraints();
        }
    }

    @Override
    @SuppressWarnings("FinalizeDeclaration")
    protected void finalize() throws Throwable {
        super.finalize();
        // Call shutdown as the user may have created the OpenSslEngine and not used it at all.
        shutdown();
    }

    private final class OpenSslSession implements SSLSession, ApplicationProtocolAccessor {
        // SSLSession implementation seems to not need to be thread-safe so no need for volatile etc.
        private X509Certificate[] x509PeerCerts;

        // lazy init for memory reasons
        private Map<String, Object> values;

        @Override
        public byte[] getId() {
            final byte[] id;
            synchronized (OpenSslEngine.this) {
                if (destroyed == 0) {
                    id = SSL.getSessionId(ssl);
                } else {
                    id = EmptyArrays.EMPTY_BYTES;
                }
            }
            // We don't cache that to keep memory usage to a minimum.
            if (id == null) {
                // The id should never be null, if it was null then the SESSION itself was not valid.
                throw new IllegalStateException("SSL session ID not available");
            }
            return id;
        }

        @Override
        public SSLSessionContext getSessionContext() {
            return sessionContext;
        }

        @Override
        public long getCreationTime() {
            synchronized (OpenSslEngine.this) {
                if (destroyed == 0) {
                    // We need ot multiple by 1000 as openssl uses seconds and we need milli-seconds.
                    return SSL.getTime(ssl) * 1000L;
                }
                return 0;
            }
        }

        @Override
        public long getLastAccessedTime() {
            // TODO: Add proper implementation
            return getCreationTime();
        }

        @Override
        public void invalidate() {
            // NOOP
        }

        @Override
        public boolean isValid() {
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

        @Override
        public Certificate[] getPeerCertificates() throws SSLPeerUnverifiedException {
            // these are lazy created to reduce memory overhead
            Certificate[] c = peerCerts;
            if (c == null) {
                synchronized (OpenSslEngine.this) {
                    if (destroyed == 0) {
                        if (SSL.isInInit(ssl) != 0) {
                            throw new SSLPeerUnverifiedException("peer not verified");
                        }
                        c = peerCerts = initPeerCertChain();
                    } else {
                        c = peerCerts = EMPTY_CERTIFICATES;
                    }
                }
            }
            return c;
        }

        @Override
        public Certificate[] getLocalCertificates() {
            // TODO: Find out how to get these
            return EMPTY_CERTIFICATES;
        }

        @Override
        public X509Certificate[] getPeerCertificateChain() throws SSLPeerUnverifiedException {
            // these are lazy created to reduce memory overhead
            X509Certificate[] c = x509PeerCerts;
            if (c == null) {
                final byte[][] chain;
                synchronized (OpenSslEngine.this) {
                    if (destroyed == 0) {
                        if (SSL.isInInit(ssl) != 0) {
                            throw new SSLPeerUnverifiedException("peer not verified");
                        }
                        chain = SSL.getPeerCertChain(ssl);
                    } else {
                        c = x509PeerCerts = EMPTY_X509_CERTIFICATES;
                        return c;
                    }
                }
                if (chain == null) {
                    throw new SSLPeerUnverifiedException("peer not verified");
                }
                X509Certificate[] peerCerts = new X509Certificate[chain.length];
                for (int i = 0; i < peerCerts.length; i++) {
                    try {
                        peerCerts[i] = X509Certificate.getInstance(chain[i]);
                    } catch (CertificateException e) {
                        throw new IllegalStateException(e);
                    }
                }
                c = x509PeerCerts = peerCerts;
            }
            return c;
        }

        @Override
        public Principal getPeerPrincipal() throws SSLPeerUnverifiedException {
            Certificate[] peer = getPeerCertificates();
            if (peer == null || peer.length == 0) {
                return null;
            }
            return ((java.security.cert.X509Certificate) peer[0]).getSubjectX500Principal();
        }

        @Override
        public Principal getLocalPrincipal() {
            Certificate[] local = getLocalCertificates();
            if (local == null || local.length == 0) {
                return null;
            }
            return ((java.security.cert.X509Certificate) local[0]).getIssuerX500Principal();
        }

        @Override
        public String getCipherSuite() {
            if (!handshakeFinished) {
                return INVALID_CIPHER;
            }
            if (cipher == null) {
                final String c;
                synchronized (OpenSslEngine.this) {
                    if (destroyed == 0) {
                        c = toJavaCipherSuite(SSL.getCipherForSSL(ssl));
                    } else {
                        c = INVALID_CIPHER;
                    }
                }
                if (c != null) {
                    cipher = c;
                }
            }
            return cipher;
        }

        @Override
        public String getProtocol() {
            synchronized (OpenSslEngine.this) {
                if (destroyed == 0) {
                    return SSL.getVersion(ssl);
                } else {
                    return StringUtil.EMPTY_STRING;
                }
            }
        }

        @Override
        public String getApplicationProtocol() {
            return applicationProtocol;
        }

        @Override
        public String getPeerHost() {
            return OpenSslEngine.this.getPeerHost();
        }

        @Override
        public int getPeerPort() {
            return OpenSslEngine.this.getPeerPort();
        }

        @Override
        public int getPacketBufferSize() {
            return MAX_ENCRYPTED_PACKET_LENGTH;
        }

        @Override
        public int getApplicationBufferSize() {
            return MAX_PLAINTEXT_LENGTH;
        }

        private Certificate[] initPeerCertChain() throws SSLPeerUnverifiedException {
            byte[][] chain = SSL.getPeerCertChain(ssl);
            final byte[] clientCert;
            if (!clientMode) {
                // if used on the server side SSL_get_peer_cert_chain(...) will not include the remote peer certificate.
                // We use SSL_get_peer_certificate to get it in this case and add it to our array later.
                //
                // See https://www.openssl.org/docs/ssl/SSL_get_peer_cert_chain.html
                clientCert = SSL.getPeerCertificate(ssl);
            } else {
                clientCert = null;
            }

            if (chain == null && clientCert == null) {
                throw new SSLPeerUnverifiedException("peer not verified");
            }
            int len = 0;
            if (chain != null) {
                len += chain.length;
            }

            int i = 0;
            Certificate[] peerCerts;
            if (clientCert != null) {
                len++;
                peerCerts = new Certificate[len];
                peerCerts[i++] = new OpenSslX509Certificate(clientCert);
            } else {
                peerCerts = new Certificate[len];
            }
            if (chain != null) {
                int a = 0;
                for (; i < peerCerts.length; i++) {
                    peerCerts[i] = new OpenSslX509Certificate(chain[a++]);
                }
            }
            return peerCerts;
        }
    }
}
