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

import io.netty.buffer.ByteBufAllocator;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.apache.tomcat.jni.Pool;
import org.apache.tomcat.jni.SSL;
import org.apache.tomcat.jni.SSLContext;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A server-side {@link SslContext} which uses OpenSSL's SSL/TLS implementation.
 */
public final class OpenSslServerContext extends SslContext {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(OpenSslServerContext.class);
    private static final List<String> DEFAULT_CIPHERS;

    static {
        List<String> ciphers = new ArrayList<String>();
        // XXX: Make sure to sync this list with JdkSslEngineFactory.
        Collections.addAll(
                ciphers,
                "ECDHE-RSA-AES128-GCM-SHA256",
                "ECDHE-RSA-RC4-SHA",
                "ECDHE-RSA-AES128-SHA",
                "ECDHE-RSA-AES256-SHA",
                "AES128-GCM-SHA256",
                "RC4-SHA",
                "RC4-MD5",
                "AES128-SHA",
                "AES256-SHA",
                "DES-CBC3-SHA");
        DEFAULT_CIPHERS = Collections.unmodifiableList(ciphers);

        if (logger.isDebugEnabled()) {
            logger.debug("Default cipher suite (OpenSSL): " + ciphers);
        }
    }

    private final long aprPool;

    private final List<String> ciphers = new ArrayList<String>();
    private final List<String> unmodifiableCiphers = Collections.unmodifiableList(ciphers);
    private final long sessionCacheSize;
    private final long sessionTimeout;
    private final List<String> nextProtocols;

    /** The OpenSSL SSL_CTX object */
    private final long ctx;
    private final OpenSslSessionStats stats;

    /**
     * Creates a new instance.
     *
     * @param certChainFile an X.509 certificate chain file in PEM format
     * @param keyFile a PKCS#8 private key file in PEM format
     */
    public OpenSslServerContext(File certChainFile, File keyFile) throws SSLException {
        this(certChainFile, keyFile, null);
    }

    /**
     * Creates a new instance.
     *
     * @param certChainFile an X.509 certificate chain file in PEM format
     * @param keyFile a PKCS#8 private key file in PEM format
     * @param keyPassword the password of the {@code keyFile}.
     *                    {@code null} if it's not password-protected.
     */
    public OpenSslServerContext(File certChainFile, File keyFile, String keyPassword) throws SSLException {
        this(certChainFile, keyFile, keyPassword, null, null, 0, 0);
    }

    /**
     * Creates a new instance.
     *
     * @param certChainFile an X.509 certificate chain file in PEM format
     * @param keyFile a PKCS#8 private key file in PEM format
     * @param keyPassword the password of the {@code keyFile}.
     *                    {@code null} if it's not password-protected.
     * @param ciphers the cipher suites to enable, in the order of preference.
     *                {@code null} to use the default cipher suites.
     * @param nextProtocols the application layer protocols to accept, in the order of preference.
     *                      {@code null} to disable TLS NPN/ALPN extension.
     * @param sessionCacheSize the size of the cache used for storing SSL session objects.
     *                         {@code 0} to use the default value.
     * @param sessionTimeout the timeout for the cached SSL session objects, in seconds.
     *                       {@code 0} to use the default value.
     */
    public OpenSslServerContext(
            File certChainFile, File keyFile, String keyPassword,
            Iterable<String> ciphers, Iterable<String> nextProtocols,
            long sessionCacheSize, long sessionTimeout) throws SSLException {

        OpenSsl.ensureAvailability();

        if (certChainFile == null) {
            throw new NullPointerException("certChainFile");
        }
        if (!certChainFile.isFile()) {
            throw new IllegalArgumentException("certChainFile is not a file: " + certChainFile);
        }
        if (keyFile == null) {
            throw new NullPointerException("keyPath");
        }
        if (!keyFile.isFile()) {
            throw new IllegalArgumentException("keyPath is not a file: " + keyFile);
        }
        if (ciphers == null) {
            ciphers = DEFAULT_CIPHERS;
        }

        if (keyPassword == null) {
            keyPassword = "";
        }
        if (nextProtocols == null) {
            nextProtocols = Collections.emptyList();
        }

        for (String c: ciphers) {
            if (c == null) {
                break;
            }
            this.ciphers.add(c);
        }

        List<String> nextProtoList = new ArrayList<String>();
        for (String p: nextProtocols) {
            if (p == null) {
                break;
            }
            nextProtoList.add(p);
        }
        this.nextProtocols = Collections.unmodifiableList(nextProtoList);

        // Allocate a new APR pool.
        aprPool = Pool.create(0);

        // Create a new SSL_CTX and configure it.
        boolean success = false;
        try {
            synchronized (OpenSslServerContext.class) {
                try {
                    ctx = SSLContext.make(aprPool, SSL.SSL_PROTOCOL_ALL, SSL.SSL_MODE_SERVER);
                } catch (Exception e) {
                    throw new SSLException("failed to create an SSL_CTX", e);
                }

                SSLContext.setOptions(ctx, SSL.SSL_OP_ALL);
                SSLContext.setOptions(ctx, SSL.SSL_OP_NO_SSLv2);
                SSLContext.setOptions(ctx, SSL.SSL_OP_CIPHER_SERVER_PREFERENCE);
                SSLContext.setOptions(ctx, SSL.SSL_OP_SINGLE_ECDH_USE);
                SSLContext.setOptions(ctx, SSL.SSL_OP_SINGLE_DH_USE);
                SSLContext.setOptions(ctx, SSL.SSL_OP_NO_SESSION_RESUMPTION_ON_RENEGOTIATION);

                /* List the ciphers that the client is permitted to negotiate. */
                try {
                    // Convert the cipher list into a colon-separated string.
                    StringBuilder cipherBuf = new StringBuilder();
                    for (String c: this.ciphers) {
                        cipherBuf.append(c);
                        cipherBuf.append(':');
                    }
                    cipherBuf.setLength(cipherBuf.length() - 1);

                    SSLContext.setCipherSuite(ctx, cipherBuf.toString());
                } catch (SSLException e) {
                    throw e;
                } catch (Exception e) {
                    throw new SSLException("failed to set cipher suite: " + this.ciphers, e);
                }

                /* Set certificate verification policy. */
                SSLContext.setVerify(ctx, SSL.SSL_CVERIFY_NONE, 10);

                /* Load the certificate file and private key. */
                try {
                    if (!SSLContext.setCertificate(
                            ctx, certChainFile.getPath(), keyFile.getPath(), keyPassword, SSL.SSL_AIDX_RSA)) {
                        throw new SSLException("failed to set certificate: " +
                                certChainFile + " and " + keyFile + " (" + SSL.getLastError() + ')');
                    }
                } catch (SSLException e) {
                    throw e;
                } catch (Exception e) {
                    throw new SSLException("failed to set certificate: " + certChainFile + " and " + keyFile, e);
                }

                /* Load the certificate chain. We must skip the first cert since it was loaded above. */
                if (!SSLContext.setCertificateChainFile(ctx, certChainFile.getPath(), true)) {
                    String error = SSL.getLastError();
                    if (!error.startsWith(OpenSsl.IGNORABLE_ERROR_PREFIX)) {
                        throw new SSLException(
                                "failed to set certificate chain: " + certChainFile + " (" + SSL.getLastError() + ')');
                    }
                }

                /* Set next protocols for next protocol negotiation extension, if specified */
                if (!nextProtoList.isEmpty()) {
                    // Convert the protocol list into a comma-separated string.
                    StringBuilder nextProtocolBuf = new StringBuilder();
                    for (String p: nextProtoList) {
                        nextProtocolBuf.append(p);
                        nextProtocolBuf.append(',');
                    }
                    nextProtocolBuf.setLength(nextProtocolBuf.length() - 1);

                    SSLContext.setNextProtos(ctx, nextProtocolBuf.toString());
                }

                /* Set session cache size, if specified */
                if (sessionCacheSize > 0) {
                    this.sessionCacheSize = sessionCacheSize;
                    SSLContext.setSessionCacheSize(ctx, sessionCacheSize);
                } else {
                    // Get the default session cache size using SSLContext.setSessionCacheSize()
                    this.sessionCacheSize = sessionCacheSize = SSLContext.setSessionCacheSize(ctx, 20480);
                    // Revert the session cache size to the default value.
                    SSLContext.setSessionCacheSize(ctx, sessionCacheSize);
                }

                /* Set session timeout, if specified */
                if (sessionTimeout > 0) {
                    this.sessionTimeout = sessionTimeout;
                    SSLContext.setSessionCacheTimeout(ctx, sessionTimeout);
                } else {
                    // Get the default session timeout using SSLContext.setSessionCacheTimeout()
                    this.sessionTimeout = sessionTimeout = SSLContext.setSessionCacheTimeout(ctx, 300);
                    // Revert the session timeout to the default value.
                    SSLContext.setSessionCacheTimeout(ctx, sessionTimeout);
                }
            }
            success = true;
        } finally {
            if (!success) {
                destroyPools();
            }
        }

        stats = new OpenSslSessionStats(ctx);
    }

    @Override
    public boolean isClient() {
        return false;
    }

    @Override
    public List<String> cipherSuites() {
        return unmodifiableCiphers;
    }

    @Override
    public long sessionCacheSize() {
        return sessionCacheSize;
    }

    @Override
    public long sessionTimeout() {
        return sessionTimeout;
    }

    @Override
    public List<String> nextProtocols() {
        return nextProtocols;
    }

    /**
     * Returns the {@code SSL_CTX} object of this context.
     */
    public long context() {
        return ctx;
    }

    /**
     * Returns the stats of this context.
     */
    public OpenSslSessionStats stats() {
        return stats;
    }

    /**
     * Returns a new server-side {@link javax.net.ssl.SSLEngine} with the current configuration.
     */
    @Override
    public SSLEngine newEngine(ByteBufAllocator alloc) {
        if (nextProtocols.isEmpty()) {
            return new OpenSslEngine(ctx, alloc, null);
        } else {
            return new OpenSslEngine(ctx, alloc, nextProtocols.get(nextProtocols.size() - 1));
        }
    }

    @Override
    public SSLEngine newEngine(ByteBufAllocator alloc, String peerHost, int peerPort) {
        throw new UnsupportedOperationException();
    }

    /**
     * Sets the SSL session ticket keys of this context.
     */
    public void setTicketKeys(byte[] keys) {
        if (keys == null) {
            throw new NullPointerException("keys");
        }
        SSLContext.setSessionTicketKeys(ctx, keys);
    }

    @Override
    @SuppressWarnings("FinalizeDeclaration")
    protected void finalize() throws Throwable {
        super.finalize();
        synchronized (OpenSslServerContext.class) {
            if (ctx != 0) {
                SSLContext.free(ctx);
            }
        }

        destroyPools();
    }

    private void destroyPools() {
        if (aprPool != 0) {
            Pool.destroy(aprPool);
        }
    }
}
