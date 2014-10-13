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
import org.apache.tomcat.jni.SSL;
import org.apache.tomcat.jni.SSLContext;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.File;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;

/**
 * A client-side {@link SslContext} which uses OpenSSL's SSL/TLS implementation.
 */
public final class OpenSslClientContext extends OpenSslContext {

    private final X509TrustManager[] managers;

    /**
     * Creates a new instance.
     */
    public OpenSslClientContext() throws SSLException {
        this(null, null, null, null, 0, 0);
    }

    /**
     * Creates a new instance.
     *
     * @param certChainFile an X.509 certificate chain file in PEM format.
     *                      {@code null} to use the system default
     */
    public OpenSslClientContext(File certChainFile) throws SSLException {
        this(certChainFile, null);
    }

    /**
     * Creates a new instance.
     *
     * @param trustManagerFactory the {@link TrustManagerFactory} that provides the {@link TrustManager}s
     *                            that verifies the certificates sent from servers.
     *                            {@code null} to use the default.
     */
    public OpenSslClientContext(TrustManagerFactory trustManagerFactory) throws SSLException {
        this(null, trustManagerFactory);
    }

    /**
     * Creates a new instance.
     *
     * @param certChainFile an X.509 certificate chain file in PEM format.
     *                      {@code null} to use the system default
     * @param trustManagerFactory the {@link TrustManagerFactory} that provides the {@link TrustManager}s
     *                            that verifies the certificates sent from servers.
     *                            {@code null} to use the default.
     */
    public OpenSslClientContext(File certChainFile, TrustManagerFactory trustManagerFactory) throws SSLException {
        this(certChainFile, trustManagerFactory, null, null, 0, 0);
    }

    /**
     * Creates a new instance.
     *
     * @param certChainFile an X.509 certificate chain file in PEM format
     * @param trustManagerFactory the {@link TrustManagerFactory} that provides the {@link TrustManager}s
     *                            that verifies the certificates sent from servers.
     *                            {@code null} to use the default..
     * @param ciphers the cipher suites to enable, in the order of preference.
     *                {@code null} to use the default cipher suites.
     * @param apn Provides a means to configure parameters related to application protocol negotiation.
     * @param sessionCacheSize the size of the cache used for storing SSL session objects.
     *                         {@code 0} to use the default value.
     * @param sessionTimeout the timeout for the cached SSL session objects, in seconds.
     *                       {@code 0} to use the default value.
     */
    public OpenSslClientContext(File certChainFile, TrustManagerFactory trustManagerFactory, Iterable<String> ciphers,
                                ApplicationProtocolConfig apn, long sessionCacheSize, long sessionTimeout)
            throws SSLException {
        super(ciphers, apn, sessionCacheSize, sessionTimeout, SSL.SSL_MODE_CLIENT);
        boolean success = false;
        try {
            if (certChainFile != null && !certChainFile.isFile()) {
                throw new IllegalArgumentException("certChainFile is not a file: " + certChainFile);
            }

            synchronized (OpenSslContext.class) {
                if (certChainFile != null) {
                    /* Load the certificate chain. We must skip the first cert when server mode */
                    if (!SSLContext.setCertificateChainFile(ctx, certChainFile.getPath(), true)) {
                        String error = SSL.getLastError();
                        if (OpenSsl.isNonIgnorableError(error)) {
                            throw new SSLException(
                                    "failed to set certificate chain: "
                                            + certChainFile + " (" + SSL.getLastError() + ')');
                        }
                    }
                }
                SSLContext.setVerify(ctx, SSL.SSL_VERIFY_NONE, VERIFY_DEPTH);

                // check if verification should take place or not.
                if (trustManagerFactory != null) {
                    try {
                        if (certChainFile == null) {
                            trustManagerFactory.init((KeyStore) null);
                        } else {
                            initTrustManagerFactory(certChainFile, trustManagerFactory);
                        }
                    } catch (Exception e) {
                        throw new SSLException("failed to initialize the client-side SSL context", e);
                    }
                    TrustManager[] tms = trustManagerFactory.getTrustManagers();
                    if (tms == null || tms.length == 0) {
                        managers = null;
                    } else {
                        List<X509TrustManager> managerList = new ArrayList<X509TrustManager>(tms.length);
                        for (TrustManager tm: tms) {
                            if (tm instanceof X509TrustManager) {
                                managerList.add((X509TrustManager) tm);
                            }
                        }
                        managers = managerList.toArray(new X509TrustManager[managerList.size()]);
                    }
                } else {
                    managers = null;
               }
            }
            success = true;
        } finally {
            if (!success) {
                destroyPools();
            }
        }
    }

    /**
     * Returns a new server-side {@link javax.net.ssl.SSLEngine} with the current configuration.
     */
    @Override
    public SSLEngine newEngine(ByteBufAllocator alloc) {
        List<String> protos = nextProtocols();
        if (protos.isEmpty()) {
            return new OpenSslEngine(ctx, alloc, null, isClient(), managers);
        } else {
            return new OpenSslEngine(ctx, alloc, protos.get(protos.size() - 1), isClient(), managers);
        }
    }
}
