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
package org.jboss.netty.handler.ssl;

import org.apache.tomcat.jni.Buffer;
import org.apache.tomcat.jni.SSL;
import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;
import org.jboss.netty.util.internal.EmptyArrays;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSessionContext;
import javax.security.cert.X509Certificate;
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.security.Principal;
import java.security.cert.Certificate;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import static javax.net.ssl.SSLEngineResult.HandshakeStatus.*;
import static javax.net.ssl.SSLEngineResult.Status.*;

/**
 * Implements a java.net.SSLEngine in terms of OpenSSL.
 *
 * Documentation on the dataflow and operation of SSLEngine and OpenSSL BIO abstractions
 * can be found at:
 *
 *   SSLEngine: http://download.oracle.com/javase/1,5.0/docs/api/javax/net/ssl/SSLEngine.html
 *   OpenSSL:   http://www.openssl.org/docs/crypto/BIO_s_bio.html#example
 */
public final class OpenSslEngine extends SSLEngine {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(OpenSslEngine.class);

    private static final Certificate[] EMPTY_CERTIFICATES = new Certificate[0];
    private static final X509Certificate[] EMPTY_X509_CERTIFICATES = new X509Certificate[0];

    private static final SSLException ENGINE_CLOSED = new SSLException("engine closed");
    private static final SSLException RENEGOTIATION_UNSUPPORTED = new SSLException("renegotiation unsupported");
    private static final SSLException ENCRYPTED_PACKET_OVERSIZED = new SSLException("encrypted packet oversized");

    static {
        ENGINE_CLOSED.setStackTrace(EmptyArrays.EMPTY_STACK_TRACE);
        RENEGOTIATION_UNSUPPORTED.setStackTrace(EmptyArrays.EMPTY_STACK_TRACE);
        ENCRYPTED_PACKET_OVERSIZED.setStackTrace(EmptyArrays.EMPTY_STACK_TRACE);
    }

    private static final int MAX_PLAINTEXT_LENGTH = 16 * 1024; // 2^14
    private static final int MAX_COMPRESSED_LENGTH = MAX_PLAINTEXT_LENGTH + 1024;
    private static final int MAX_CIPHERTEXT_LENGTH = MAX_COMPRESSED_LENGTH + 1024;

    // Header (5) + Data (2^14) + Compression (1024) + Encryption (1024) + MAC (20) + Padding (256)
    static final int MAX_ENCRYPTED_PACKET_LENGTH = MAX_CIPHERTEXT_LENGTH + 5 + 20 + 256;

    private static final String SSL_IGNORABLE_ERROR_PREFIX = "error:00000000:";

    private static final AtomicIntegerFieldUpdater<OpenSslEngine> DESTROYED_UPDATER =
            AtomicIntegerFieldUpdater.newUpdater(OpenSslEngine.class, "destroyed");

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

    private String cipher;
    private String protocol;

    // SSL Engine status variables
    private boolean isInboundDone;
    private boolean isOutboundDone;
    private boolean engineClosed;

    private int lastPrimingReadResult;

    private final OpenSslBufferPool bufPool;
    private SSLSession session;

    public OpenSslEngine(long sslContext, OpenSslBufferPool bufPool) {
        OpenSsl.ensureAvailability();
        if (sslContext == 0) {
            throw new NullPointerException("sslContext");
        }
        if (bufPool == null) {
            throw new NullPointerException("bufPool");
        }

        this.bufPool = bufPool;
        ssl = SSL.newSSL(sslContext, true);
        networkBIO = SSL.makeNetworkBIO(ssl);
    }

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
        final ByteBuffer buf = bufPool.acquire();
        final long addr = Buffer.address(buf);
        try {
            int position = src.position();
            int limit = src.limit();
            int len = Math.min(src.remaining(), MAX_PLAINTEXT_LENGTH);
            if (len > buf.capacity()) {
                throw new IllegalStateException("buffer pool write overflow");
            }
            src.limit(position + len);

            buf.put(src);
            src.limit(limit);

            final int sslWrote = SSL.writeToSSL(ssl, addr, len);
            if (sslWrote > 0) {
                src.position(position + sslWrote);
                return sslWrote;
            } else {
                src.position(position);
                throw new IllegalStateException("SSL.writeToSSL() returned a non-positive value: " + sslWrote);
            }
        } finally {
            bufPool.release(buf);
        }
    }

    /**
     * Write encrypted data to the OpenSSL network BIO
     */
    private int writeEncryptedData(final ByteBuffer src) {
        final ByteBuffer buf = bufPool.acquire();
        final long addr = Buffer.address(buf);
        try {
            int position = src.position();
            int len = src.remaining();
            if (len > buf.capacity()) {
                throw new IllegalStateException("buffer pool write overflow");
            }

            buf.put(src);

            final int netWrote = SSL.writeToBIO(networkBIO, addr, len);
            if (netWrote >= 0) {
                src.position(position + netWrote);
                lastPrimingReadResult = SSL.readFromSSL(ssl, addr, 0); // priming read
                return netWrote;
            } else {
                src.position(position);
                return 0;
            }
        } finally {
            bufPool.release(buf);
        }
    }

    /**
     * Read plaintext data from the OpenSSL internal BIO
     */
    private int readPlaintextData(final ByteBuffer dst) {
        final ByteBuffer buf = bufPool.acquire();
        final long addr = Buffer.address(buf);
        try {
            final int len = Math.min(buf.capacity(), dst.capacity());
            buf.limit(len);
            final int sslRead = SSL.readFromSSL(ssl, addr, len);
            if (sslRead > 0) {
                buf.limit(sslRead);
                dst.put(buf);
                return sslRead;
            } else {
                return 0;
            }
        } finally {
            bufPool.release(buf);
        }
    }

    /**
     * Read encrypted data from the OpenSSL network BIO
     */
    private int readEncryptedData(final ByteBuffer dst, final int pending) {
        final ByteBuffer buf = bufPool.acquire();
        final long addr = Buffer.address(buf);
        try {
            if (pending > buf.capacity()) {
                throw new IllegalStateException("network BIO read overflow " +
                        "(pending: " + pending + ", capacity: " + buf.capacity() + ')');
            }

            final int bioRead = SSL.readFromBIO(networkBIO, addr, pending);
            if (bioRead > 0) {
                buf.limit(bioRead);
                dst.put(buf);
                return bioRead;
            } else {
                return 0;
            }
        } finally {
            bufPool.release(buf);
        }
    }

    /**
     * Encrypt plaintext data from srcs buffers into dst buffer.
     *
     * This is called both to encrypt application data as well as
     * to retrieve handshake data destined for the peer.
     */
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
                        return new SSLEngineResult(BUFFER_OVERFLOW, getHandshakeStatus(), bytesConsumed, bytesProduced);
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

    /**
     * Decrypt encrypted data from src buffers into dsts buffers.
     */
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
        for (int i = offset; i < offset + length; ++i) {
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
        String error = SSL.getLastError();
        if (error != null && !error.startsWith(SSL_IGNORABLE_ERROR_PREFIX)) {
            if (logger.isInfoEnabled()) {
                logger.info(
                        "SSL_read failed: primingReadResult: " + lastPrimingReadResult +
                                "; OpenSSL error: '" + error + '\'');
            }

            // There was an internal error -- shutdown
            shutdown();
            throw new SSLException(error);
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
        for (;;) {
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

    /**
     * Currently we do not delegate SSL computation tasks
     * TODO: in the future, possibly create tasks to do encrypt / decrypt async
     */
    @Override
    public Runnable getDelegatedTask() {
        return null;
    }

    /**
     * This method is called on channel disconnection by SSLHandler when the server
     * did not initiate the closure process to detect against truncation attacks.
     */
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
                throw new SSLException("close_notify has not been received");
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

    /**
     * This method is called on channel disconnection to send close_notify
     */
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

    @Override
    public SSLSession getSession() {
        SSLSession session = this.session;
        if (session == null) {
            this.session = session = new SSLSession() {
                public byte[] getId() {
                    return String.valueOf(ssl).getBytes();
                }

                public SSLSessionContext getSessionContext() {
                    return null;
                }

                public long getCreationTime() {
                    return 0;
                }

                public long getLastAccessedTime() {
                    return 0;
                }

                public void invalidate() {
                }

                public boolean isValid() {
                    return false;
                }

                public void putValue(String s, Object o) {
                }

                public Object getValue(String s) {
                    return null;
                }

                public void removeValue(String s) {
                }

                public String[] getValueNames() {
                    return EmptyArrays.EMPTY_STRINGS;
                }

                public Certificate[] getPeerCertificates() {
                    return EMPTY_CERTIFICATES;
                }

                public Certificate[] getLocalCertificates() {
                    return EMPTY_CERTIFICATES;
                }

                public X509Certificate[] getPeerCertificateChain() {
                    return EMPTY_X509_CERTIFICATES;
                }

                public Principal getPeerPrincipal() {
                    return null;
                }

                public Principal getLocalPrincipal() {
                    return null;
                }

                public String getCipherSuite() {
                    return cipher;
                }

                public String getProtocol() {
                    return protocol;
                }

                public String getPeerHost() {
                    return null;
                }

                public int getPeerPort() {
                    return 0;
                }

                public int getPacketBufferSize() {
                    return MAX_ENCRYPTED_PACKET_LENGTH;
                }

                public int getApplicationBufferSize() {
                    return MAX_PLAINTEXT_LENGTH;
                }
            };
        }

        return session;
    }

    /**
     * This method causes the OpenSSL engine to accept connections
     */
    @Override
    public synchronized void beginHandshake() throws SSLException {
        if (engineClosed) {
            throw ENGINE_CLOSED;
        }

        switch (accepted) {
            case 0:
                SSL.doHandshake(ssl);
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
            SSL.doHandshake(ssl);
            accepted = 1;
        }
    }

    private SSLEngineResult.Status getEngineStatus() {
        return engineClosed? CLOSED : OK;
    }

    /**
     * Return the handshake status of the SSL Engine.
     */
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
                cipher = SSL.getCipherForSSL(ssl);
                protocol = SSL.getNextProtoNegotiated(ssl);
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
        if (clientMode) {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public boolean getUseClientMode() {
        return false;
    }

    @Override
    public void setNeedClientAuth(boolean b) {
        if (b) {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public boolean getNeedClientAuth() {
        return false;
    }

    @Override
    public void setWantClientAuth(boolean b) {
        if (b) {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public boolean getWantClientAuth() {
        return false;
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
