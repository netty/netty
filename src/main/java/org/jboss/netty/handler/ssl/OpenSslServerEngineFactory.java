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

import org.apache.tomcat.jni.SSL;
import org.apache.tomcat.jni.SSLContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import java.io.Closeable;

/**
 * Creates a new {@link OpenSslEngine}.  Internally, this factory keeps the SSL_CTX object of OpenSSL.
 * This factory is intended for a shared use by multiple channels:
 * <pre>
 * public class MyChannelPipelineFactory extends {@link ChannelPipelineFactory} {
 *
 *     private final {@link OpenSslServerEngineFactory} sslEngineFactory = ...;
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
public class OpenSslServerEngineFactory implements Closeable {

    private final long aprPool;
    private final OpenSslBufferPool bufPool;

    private String certPath;
    private String keyPath;
    private String cipherSpec;
    private String keyPassword;
    private String caPath;
    private String nextProtos;

    /** The OpenSSL SSL_CTX object */
    private long sslContext;
    private volatile boolean closed;

    /**
     * Create a new instance.
     */
    public OpenSslServerEngineFactory(long aprPool, OpenSslBufferPool bufPool) {
        if (aprPool == 0) {
            throw new NullPointerException("aprPool");
        }
        if (bufPool == null) {
            throw new NullPointerException("bufPool");
        }

        this.aprPool = aprPool;
        this.bufPool = bufPool;
    }

    public String certPath() {
        return certPath;
    }

    public void setCertPath(String certPath) {
        ensureUnfrozen();
        this.certPath = certPath;
    }

    public String keyPath() {
        return keyPath;
    }

    public void setKeyPath(String keyPath) {
        ensureUnfrozen();
        this.keyPath = keyPath;
    }

    public String cipherSpec() {
        return cipherSpec;
    }

    public void setCipherSpec(String cipherSpec) {
        ensureUnfrozen();
        this.cipherSpec = cipherSpec;
    }

    public String keyPassword() {
        return keyPassword;
    }

    public void setKeyPassword(String keyPassword) {
        ensureUnfrozen();
        this.keyPassword = keyPassword;
    }

    public String caPath() {
        return caPath;
    }

    public void setCaPath(String caPath) {
        ensureUnfrozen();
        this.caPath = caPath;
    }

    public String nextProtos() {
        return nextProtos;
    }

    public void setNextProtos(String nextProtos) {
        ensureUnfrozen();
        this.nextProtos = nextProtos;
    }

    private void ensureUnfrozen() {
        if (sslContext != 0 || closed) {
            throw new IllegalStateException("configuration frozen");
        }
    }

    /**
     * Configures and initializes OpenSSL.
     *
     * @throws SSLException if the required fields are not assigned
     */
    public void init() throws SSLException {
        synchronized (OpenSslServerEngineFactory.class) {
            if (sslContext != 0) {
                return;
            }

            verifyCorrectConstruction();

            try {
                sslContext = SSLContext.make(aprPool, SSL.SSL_PROTOCOL_ALL, SSL.SSL_MODE_SERVER);
            } catch (Exception e) {
                throw new SSLException("failed to create an SSL_CTX", e);
            }

            SSLContext.setOptions(sslContext, SSL.SSL_OP_ALL);
            SSLContext.setOptions(sslContext, SSL.SSL_OP_NO_SSLv2);
            SSLContext.setOptions(sslContext, SSL.SSL_OP_CIPHER_SERVER_PREFERENCE);
            SSLContext.setOptions(sslContext, SSL.SSL_OP_SINGLE_ECDH_USE);
            SSLContext.setOptions(sslContext, SSL.SSL_OP_SINGLE_DH_USE);
            SSLContext.setOptions(sslContext, SSL.SSL_OP_NO_SESSION_RESUMPTION_ON_RENEGOTIATION);

            /* List the ciphers that the client is permitted to negotiate. */
            try {
                SSLContext.setCipherSuite(sslContext, cipherSpec);
            } catch (SSLException e) {
                throw e;
            } catch (Exception e) {
                throw new SSLException("failed to set cipher suite: " + cipherSpec, e);
            }

            /* Set certificate verification policy. */
            SSLContext.setVerify(sslContext, SSL.SSL_CVERIFY_NONE, 10);


            /* Load the certificate file and private key. */
            try {
                if (!SSLContext.setCertificate(
                        sslContext, certPath, keyPath, keyPassword, SSL.SSL_AIDX_RSA)) {
                    throw new SSLException(
                            "failed to set certificate: " + certPath + " (" + SSL.getLastError() + ')');
                }
            } catch (SSLException e) {
                throw e;
            } catch (Exception e) {
                throw new SSLException("failed to set certificate: " + certPath, e);
            }

            /* Load certificate chain file, if specified */
            if (caPath != null && caPath.length() > 0) {
                /* If named same as cert file, we must skip the first cert since it was loaded above. */
                boolean skipFirstCert = certPath.equals(caPath);

                if (!SSLContext.setCertificateChainFile(sslContext, caPath, skipFirstCert)) {
                    throw new SSLException(
                            "failed to set certificate chain: " + caPath + " (" + SSL.getLastError() + ')');
                }
            }

            /* Set next protocols for next protocol negotiation extension, if specified */
            if (nextProtos != null && nextProtos.length() > 0) {
                SSLContext.setNextProtos(sslContext, nextProtos);
            }
        }
    }

    /**
     * Assert all required fields are present, and set defaults for unassigned optional fields.
     *
     * @throws SSLException if required fields are not assigned
     */
    private void verifyCorrectConstruction() throws SSLException {
        assertRequiredFieldsAssigned();
        assignDefaultsToUnassignedOptionalFields();
    }

    /**
     * Assert that all required fields have been assigned.
     *
     * @throws SSLException if a required value is not set.
     */
    private void assertRequiredFieldsAssigned() throws SSLException {
        if (certPath == null || certPath.isEmpty()) {
            throw new SSLException("missing: certPath");
        }

        if (keyPath == null || keyPath.isEmpty()) {
            throw new SSLException("missing: keyPath");
        }

        if (cipherSpec == null || cipherSpec.isEmpty()) {
            throw new SSLException("missing: cipherSpec");
        }
    }

    private void assignDefaultsToUnassignedOptionalFields() {
        if (keyPassword == null) {
            keyPassword = "";
        }
        if (caPath == null) {
            caPath = "";
        }
        if (nextProtos == null) {
            nextProtos = "";
        }
    }

    public void close() {
        synchronized (OpenSslServerEngineFactory.class) {
            if (closed) {
                return;
            }

            closed = true;
            long oldSslContext = sslContext;
            if (oldSslContext != 0) {
                sslContext = 0;
                SSLContext.free(oldSslContext);
            }
        }
    }

    /**
     * Returns a new server-side {@link SSLEngine} with the current configuration.
     */
    public SSLEngine newServerEngine() throws SSLException {
        if (closed) {
            throw new IllegalStateException("closed");
        }

        if (sslContext == 0) {
            init();
        }

        return new OpenSslEngine(sslContext, bufPool);
    }
}
