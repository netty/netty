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

import org.apache.tomcat.jni.Pool;
import org.apache.tomcat.jni.SSL;
import org.apache.tomcat.jni.SSLContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Creates a new {@link OpenSslEngine}.  Internally, this factory keeps the SSL_CTX object of OpenSSL.
 * This factory is intended for a shared use by multiple channels:
 * <pre>
 * public class MyChannelPipelineFactory extends {@link ChannelPipelineFactory} {
 *
 *     private final {@link OpenSslServerContext} sslEngineFactory = ...;
 *
 *     public {@link ChannelPipeline} getPipeline() {
 *         {@link ChannelPipeline} p = {@link Channels#pipeline() Channels.pipeline()};
 *         p.addLast("ssl", new {@link SslHandler}(sslEngineFactory.newEngine()));
 *         ...
 *         return p;
 *     }
 * }
 * </pre>
 *
 */
public final class OpenSslServerContext {

    private final long aprPool;
    private final OpenSslBufferPool bufPool;
    private final boolean destroyAprPool;

    private final List<String> cipherSpec = new ArrayList<String>();
    private final List<String> unmodifiableCipherSpec = Collections.unmodifiableList(cipherSpec);
    private final String cipherSpecText;
    private final long sessionCacheSize;
    private final long sessionTimeout;
    private final String nextProtos;

    /** The OpenSSL SSL_CTX object */
    private final long ctx;
    private final OpenSslSessionStats stats;

    OpenSslServerContext(
            long aprPool, OpenSslBufferPool bufPool,
            String certPath, String keyPath, String keyPassword,
            String caPath, String nextProtos, Iterable<String> ciphers,
            long sessionCacheSize, long sessionTimeout) throws SSLException {

        if (certPath == null) {
            throw new NullPointerException("certPath");
        }
        if (certPath.length() == 0) {
            throw new IllegalArgumentException("certPath is empty.");
        }
        if (keyPath == null) {
            throw new NullPointerException("keyPath");
        }
        if (keyPath.length() == 0) {
            throw new IllegalArgumentException("keyPath is empty.");
        }
        if (ciphers == null) {
            throw new NullPointerException("ciphers");
        }

        if (keyPassword == null) {
            keyPassword = "";
        }
        if (caPath == null) {
            caPath = "";
        }
        if (nextProtos == null) {
            nextProtos = "";
        }

        for (String c: ciphers) {
            if (c == null) {
                break;
            }
            cipherSpec.add(c);
        }

        this.nextProtos = nextProtos;

        // Convert the cipher list into a colon-separated string.
        StringBuilder cipherSpecBuf = new StringBuilder();
        for (String c: cipherSpec) {
            cipherSpecBuf.append(c);
            cipherSpecBuf.append(':');
        }
        cipherSpecBuf.setLength(cipherSpecBuf.length() - 1);
        cipherSpecText = cipherSpecBuf.toString();

        // Allocate a new APR pool if necessary.
        if (aprPool == 0) {
            aprPool = Pool.create(0);
            destroyAprPool = true;
        } else {
            destroyAprPool = false;
        }

        // Allocate a new OpenSSL buffer pool if necessary.
        boolean success = false;
        try {
            if (bufPool == null) {
                bufPool = new OpenSslBufferPool(Runtime.getRuntime().availableProcessors() * 2);
            }
            success = true;
        } finally {
            if (!success && destroyAprPool) {
                Pool.destroy(aprPool);
            }
        }

        this.aprPool = aprPool;
        this.bufPool = bufPool;

        // Create a new SSL_CTX and configure it.
        success = false;
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
                    SSLContext.setCipherSuite(ctx, cipherSpecText);
                } catch (SSLException e) {
                    throw e;
                } catch (Exception e) {
                    throw new SSLException("failed to set cipher suite: " + cipherSpecText, e);
                }

                /* Set certificate verification policy. */
                SSLContext.setVerify(ctx, SSL.SSL_CVERIFY_NONE, 10);

                /* Load the certificate file and private key. */
                try {
                    if (!SSLContext.setCertificate(
                            ctx, certPath, keyPath, keyPassword, SSL.SSL_AIDX_RSA)) {
                        throw new SSLException(
                                "failed to set certificate: " + certPath + " (" + SSL.getLastError() + ')');
                    }
                } catch (SSLException e) {
                    throw e;
                } catch (Exception e) {
                    throw new SSLException("failed to set certificate: " + certPath, e);
                }

                /* Load certificate chain file, if specified */
                if (caPath.length() != 0) {
                    /* If named same as cert file, we must skip the first cert since it was loaded above. */
                    boolean skipFirstCert = certPath.equals(caPath);

                    if (!SSLContext.setCertificateChainFile(ctx, caPath, skipFirstCert)) {
                        throw new SSLException(
                                "failed to set certificate chain: " + caPath + " (" + SSL.getLastError() + ')');
                    }
                }

                /* Set next protocols for next protocol negotiation extension, if specified */
                if (nextProtos.length() != 0) {
                    SSLContext.setNextProtos(ctx, nextProtos);
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

    public List<String> cipherSpec() {
        return unmodifiableCipherSpec;
    }

    public String cipherSpecText() {
        return cipherSpecText;
    }

    public String nextProtos() {
        return nextProtos;
    }

    public int sessionCacheSize() {
        return sessionCacheSize > Integer.MAX_VALUE? Integer.MAX_VALUE : (int) sessionCacheSize;
    }

    public int sessionTimeout() {
        return sessionTimeout > Integer.MAX_VALUE? Integer.MAX_VALUE : (int) sessionTimeout;
    }

    public OpenSslSessionStats stats() {
        return stats;
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
        if (destroyAprPool && aprPool != 0) {
            Pool.destroy(aprPool);
        }
    }

    /**
     * Returns a new server-side {@link SSLEngine} with the current configuration.
     */
    public SSLEngine newEngine() {
        return new OpenSslEngine(ctx, bufPool);
    }
}
