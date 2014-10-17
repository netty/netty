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
import io.netty.util.internal.EmptyArrays;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.apache.tomcat.jni.Buffer;
import org.apache.tomcat.jni.SSL;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSessionBindingEvent;
import javax.net.ssl.SSLSessionBindingListener;
import javax.net.ssl.SSLSessionContext;
import javax.security.cert.X509Certificate;
import javax.security.cert.CertificateException;
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.security.Principal;
import java.security.cert.Certificate;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.regex.Pattern;

import static javax.net.ssl.SSLEngineResult.HandshakeStatus.*;
import static javax.net.ssl.SSLEngineResult.Status.*;

/**
 * Implements a {@link SSLEngine} using
 * <a href="https://www.openssl.org/docs/crypto/BIO_s_bio.html#EXAMPLE">OpenSSL BIO abstractions</a>.
 */
public final class OpenSslEngine extends SSLEngine {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(OpenSslEngine.class);

    private static final Certificate[] EMPTY_CERTIFICATES = new Certificate[0];
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
        AtomicReferenceFieldUpdater<OpenSslEngine, SSLSession> sessionUpdater =
                PlatformDependent.newAtomicReferenceFieldUpdater(OpenSslEngine.class, "session");
        if (sessionUpdater == null) {
            sessionUpdater = AtomicReferenceFieldUpdater.newUpdater(OpenSslEngine.class, SSLSession.class, "session");
        }
        SESSION_UPDATER = sessionUpdater;
    }

    private static final int MAX_PLAINTEXT_LENGTH = 16 * 1024; // 2^14
    private static final int MAX_COMPRESSED_LENGTH = MAX_PLAINTEXT_LENGTH + 1024;
    private static final int MAX_CIPHERTEXT_LENGTH = MAX_COMPRESSED_LENGTH + 1024;

    // Header (5) + Data (2^14) + Compression (1024) + Encryption (1024) + MAC (20) + Padding (256)
    static final int MAX_ENCRYPTED_PACKET_LENGTH = MAX_CIPHERTEXT_LENGTH + 5 + 20 + 256;

    static final int MAX_ENCRYPTION_OVERHEAD_LENGTH = MAX_ENCRYPTED_PACKET_LENGTH - MAX_PLAINTEXT_LENGTH;

    enum ClientAuthMode {
        NONE,
        WANT,
        REQUIRE,
    }

    private static final AtomicIntegerFieldUpdater<OpenSslEngine> DESTROYED_UPDATER;
    private static final AtomicReferenceFieldUpdater<OpenSslEngine, SSLSession> SESSION_UPDATER;

    private static final Pattern CIPHER_REPLACE_PATTERN = Pattern.compile("-");
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
    private volatile String cipher = "SSL_NULL_WITH_NULL_NULL";
    private volatile String applicationProtocol;

    // We store this outside of the SslSession so we not need to create an instance during verifyCertificates(...)
    private volatile Certificate[] peerCerts;
    private volatile ClientAuthMode clientAuth = ClientAuthMode.NONE;

    // SSL Engine status variables
    private boolean isInboundDone;
    private boolean isOutboundDone;
    private boolean engineClosed;

    private int lastPrimingReadResult;

    private final boolean clientMode;
    private final ByteBufAllocator alloc;
    private final String fallbackApplicationProtocol;

    @SuppressWarnings("unused")
    private volatile SSLSession session;

    /**
     * Creates a new instance
     *
     * @param sslCtx an OpenSSL {@code SSL_CTX} object
     * @param alloc the {@link ByteBufAllocator} that will be used by this engine
     */
    @Deprecated
    public OpenSslEngine(long sslCtx, ByteBufAllocator alloc, String fallbackApplicationProtocol) {
        this(sslCtx, alloc, fallbackApplicationProtocol, false);
    }

    /**
     * Creates a new instance
     *
     * @param sslCtx an OpenSSL {@code SSL_CTX} object
     * @param alloc the {@link ByteBufAllocator} that will be used by this engine
     * @param clientMode {@code true} if this is used for clients, {@code false} otherwise
     */
    OpenSslEngine(long sslCtx, ByteBufAllocator alloc, String fallbackApplicationProtocol,
                  boolean clientMode) {
        OpenSsl.ensureAvailability();
        if (sslCtx == 0) {
            throw new NullPointerException("sslContext");
        }
        if (alloc == null) {
            throw new NullPointerException("alloc");
        }

        this.alloc = alloc;
        ssl = SSL.newSSL(sslCtx, !clientMode);
        networkBIO = SSL.makeNetworkBIO(ssl);
        this.fallbackApplicationProtocol = fallbackApplicationProtocol;
        this.clientMode = clientMode;
    }

    /**
     * Destroys this engine.
     */
    public synchronized void shutdown() {
        if (DESTROYED_UPDATER.compareAndSet(this, 0, 1)) {
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
     * Write encrypted data to the OpenSSL network BIO
     */
    private int writeEncryptedData(final ByteBuffer src) {
        final int pos = src.position();
        final int len = src.remaining();
        if (src.isDirect()) {
            final long addr = Buffer.address(src) + pos;
            final int netWrote = SSL.writeToBIO(networkBIO, addr, len);
            if (netWrote >= 0) {
                src.position(pos + netWrote);
                lastPrimingReadResult = SSL.readFromSSL(ssl, addr, 0); // priming read
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
                    lastPrimingReadResult = SSL.readFromSSL(ssl, addr, 0); // priming read
                    return netWrote;
                } else {
                    src.position(pos);
                }
            } finally {
                buf.release();
            }
        }

        return 0;
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
            return new SSLEngineResult(CLOSED, NOT_HANDSHAKING, 0, 0);
        }

        // Throw required runtime exceptions
        if (srcs == null) {
            throw new NullPointerException("srcs");
        }
        if (dst == null) {
            throw new NullPointerException("dst");
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
        SSLEngineResult.HandshakeStatus handshakeStatus = getHandshakeStatus();

        if ((!handshakeFinished || engineClosed) && handshakeStatus == NEED_UNWRAP) {
            return new SSLEngineResult(getEngineStatus(), NEED_UNWRAP, 0, 0);
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

            return new SSLEngineResult(getEngineStatus(), getHandshakeStatus(), 0, bytesProduced);
        }

        // There was no pending data in the network BIO -- encrypt any application data
        int bytesConsumed = 0;
        for (int i = offset; i < length; ++ i) {
            final ByteBuffer src = srcs[i];
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
                                BUFFER_OVERFLOW, getHandshakeStatus(), bytesConsumed, bytesProduced);
                    }

                    // Write the pending data from the network BIO into the dst buffer
                    try {
                        bytesProduced += readEncryptedData(dst, pendingNet);
                    } catch (Exception e) {
                        throw new SSLException(e);
                    }

                    return new SSLEngineResult(getEngineStatus(), getHandshakeStatus(), bytesConsumed, bytesProduced);
                }
            }
        }

        return new SSLEngineResult(getEngineStatus(), getHandshakeStatus(), bytesConsumed, bytesProduced);
    }

    @Override
    public synchronized SSLEngineResult unwrap(
            final ByteBuffer src, final ByteBuffer[] dsts, final int offset, final int length) throws SSLException {

        // Check to make sure the engine has not been closed
        if (destroyed != 0) {
            return new SSLEngineResult(CLOSED, NOT_HANDSHAKING, 0, 0);
        }

        // Throw requried runtime exceptions
        if (src == null) {
            throw new NullPointerException("src");
        }
        if (dsts == null) {
            throw new NullPointerException("dsts");
        }
        if (offset >= dsts.length || offset + length > dsts.length) {
            throw new IndexOutOfBoundsException(
                    "offset: " + offset + ", length: " + length +
                            " (expected: offset <= offset + length <= dsts.length (" + dsts.length + "))");
        }

        int capacity = 0;
        final int endOffset = offset + length;
        for (int i = offset; i < endOffset; i ++) {
            ByteBuffer dst = dsts[i];
            if (dst == null) {
                throw new IllegalArgumentException();
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
        SSLEngineResult.HandshakeStatus handshakeStatus = getHandshakeStatus();
        if ((!handshakeFinished || engineClosed) && handshakeStatus == NEED_WRAP) {
            return new SSLEngineResult(getEngineStatus(), NEED_WRAP, 0, 0);
        }

        // protect against protocol overflow attack vector
        if (src.remaining() > MAX_ENCRYPTED_PACKET_LENGTH) {
            isInboundDone = true;
            isOutboundDone = true;
            engineClosed = true;
            shutdown();
            throw ENCRYPTED_PACKET_OVERSIZED;
        }

        // Write encrypted data to network BIO
        int bytesConsumed = 0;
        lastPrimingReadResult = 0;
        try {
            bytesConsumed += writeEncryptedData(src);
        } catch (Exception e) {
            throw new SSLException(e);
        }

        // Check for OpenSSL errors caused by the priming read
        long error = SSL.getLastErrorNumber();
        if (OpenSsl.isError(error)) {
            String err = SSL.getErrorString(error);
            if (logger.isInfoEnabled()) {
                logger.info(
                        "SSL_read failed: primingReadResult: " + lastPrimingReadResult +
                                "; OpenSSL error: '" + err + '\'');
            }

            // There was an internal error -- shutdown
            shutdown();
            throw new SSLException(err);
        }

        // There won't be any application data until we're done handshaking
        int pendingApp = SSL.isInInit(ssl) == 0 ? SSL.pendingReadableBytesInSSL(ssl) : 0;

        // Do we have enough room in dsts to write decrypted data?
        if (capacity < pendingApp) {
            return new SSLEngineResult(BUFFER_OVERFLOW, getHandshakeStatus(), bytesConsumed, 0);
        }

        // Write decrypted data to dsts buffers
        int bytesProduced = 0;
        int idx = offset;
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

            if (bytesRead == 0) {
                break;
            }

            bytesProduced += bytesRead;
            pendingApp -= bytesRead;

            if (!dst.hasRemaining()) {
                idx ++;
            }
        }

        // Check to see if we received a close_notify message from the peer
        if (!receivedShutdown && (SSL.getShutdown(ssl) & SSL.SSL_RECEIVED_SHUTDOWN) == SSL.SSL_RECEIVED_SHUTDOWN) {
            receivedShutdown = true;
            closeOutbound();
            closeInbound();
        }

        return new SSLEngineResult(getEngineStatus(), getHandshakeStatus(), bytesConsumed, bytesProduced);
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

        if (accepted != 0) {
            if (!receivedShutdown) {
                shutdown();
                throw new SSLException(
                        "Inbound closed before receiving peer's close_notify: possible truncation attack?");
            }
        } else {
            // engine closing before initial handshake
            shutdown();
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
        return EmptyArrays.EMPTY_STRINGS;
    }

    @Override
    public String[] getEnabledCipherSuites() {
        return EmptyArrays.EMPTY_STRINGS;
    }

    @Override
    public void setEnabledCipherSuites(String[] strings) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String[] getSupportedProtocols() {
        return EmptyArrays.EMPTY_STRINGS;
    }

    @Override
    public String[] getEnabledProtocols() {
        return EmptyArrays.EMPTY_STRINGS;
    }

    @Override
    public void setEnabledProtocols(String[] strings) {
        throw new UnsupportedOperationException();
    }

    private Certificate[] initPeerCertChain() throws SSLPeerUnverifiedException {
        byte[][] chain = SSL.getPeerCertChain(ssl);
        byte[] clientCert;
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
            throw new SSLPeerUnverifiedException("Peer was not verified");
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

    @Override
    public SSLSession getSession() {
        // A other methods on SSLEngine are thread-safe we also need to make this thread-safe...
        SSLSession session = this.session;
        if (session == null) {
            session = new SSLSession() {
                // SSLSession implementation seems to not need to be thread-safe so no need for volatile etc.
                private byte[] id;
                private X509Certificate[] x509PeerCerts;

                // lazy init for memory reasons
                private Map<String, Object> values;

                @Override
                public byte[] getId() {
                    // these are lazy created to reduce memory overhead but cached for performance reasons.
                    if (id == null) {
                        id = String.valueOf(ssl).getBytes();
                    }
                    return id;
                }

                @Override
                public SSLSessionContext getSessionContext() {
                    return null;
                }

                @Override
                public long getCreationTime() {
                    // We need ot multiple by 1000 as openssl uses seconds and we need milli-seconds.
                    return SSL.getTime(ssl) * 1000L;
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
                        if (SSL.isInInit(ssl) != 0) {
                            throw new SSLPeerUnverifiedException("peer not verified");
                        }
                        c = peerCerts = initPeerCertChain();
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
                        if (SSL.isInInit(ssl) != 0) {
                            throw new SSLPeerUnverifiedException("peer not verified");
                        }
                        byte[][] chain = SSL.getPeerCertChain(ssl);
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
                    return principal(peer);
                }

                @Override
                public Principal getLocalPrincipal() {
                    Certificate[] local = getLocalCertificates();
                    if (local == null || local.length == 0) {
                        return null;
                    }
                    return principal(local);
                }

                private Principal principal(Certificate[] certs) {
                    return ((java.security.cert.X509Certificate) certs[0]).getIssuerX500Principal();
                }

                @Override
                public String getCipherSuite() {
                    return cipher;
                }

                @Override
                public String getProtocol() {
                    String applicationProtocol = OpenSslEngine.this.applicationProtocol;
                    String version = SSL.getVersion(ssl);
                    if (applicationProtocol == null) {
                        return version;
                    } else {
                        return version + ':' + applicationProtocol;
                    }
                }

                @Override
                public String getPeerHost() {
                    return null;
                }

                @Override
                public int getPeerPort() {
                    return 0;
                }

                @Override
                public int getPacketBufferSize() {
                    return MAX_ENCRYPTED_PACKET_LENGTH;
                }

                @Override
                public int getApplicationBufferSize() {
                    return MAX_PLAINTEXT_LENGTH;
                }
            };

            if (!SESSION_UPDATER.compareAndSet(this, null, session)) {
                // Was lazy created in the meantime so get the current reference.
                session = this.session;
            }
        }

        return session;
    }

    @Override
    public synchronized void beginHandshake() throws SSLException {
        if (engineClosed) {
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

    private synchronized void beginHandshakeImplicitly() throws SSLException {
        if (engineClosed) {
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
                if (logger.isInfoEnabled()) {
                    logger.info(
                            "SSL_do_handshake failed: OpenSSL error: '" + err + '\'');
                }

                // There was an internal error -- shutdown
                shutdown();
                throw new SSLException(err);
            }
        }
    }

    private static long memoryAddress(ByteBuf buf) {
        if (buf.hasMemoryAddress()) {
            return buf.memoryAddress();
        } else {
            return Buffer.address(buf.nioBuffer());
        }
    }

    private SSLEngineResult.Status getEngineStatus() {
        return engineClosed? CLOSED : OK;
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
                handshakeFinished = true;
                String c = SSL.getCipherForSSL(ssl);
                if (c != null) {
                    // OpenSSL returns the ciphers seperated by '-' but the JDK SSLEngine does by '_', so replace '-'
                    // with '_' to match the behaviour.
                    cipher = CIPHER_REPLACE_PATTERN.matcher(c).replaceAll("_");
                }
                String applicationProtocol = SSL.getNextProtoNegotiated(ssl);
                if (applicationProtocol == null) {
                    applicationProtocol = fallbackApplicationProtocol;
                }
                if (applicationProtocol != null) {
                    this.applicationProtocol = applicationProtocol.replace(':', '_');
                } else {
                    this.applicationProtocol = null;
                }
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
        setClientAuth(b ? ClientAuthMode.WANT : ClientAuthMode.NONE);
    }

    @Override
    public boolean getWantClientAuth() {
        return clientAuth == ClientAuthMode.WANT;
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
                case WANT:
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
}
