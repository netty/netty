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
import java.io.File;
import java.util.List;

import static io.netty.util.internal.ObjectUtil.*;

/**
 * A server-side {@link SslContext} which uses OpenSSL's SSL/TLS implementation.
 */
public final class OpenSslServerContext extends OpenSslContext {

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
        this(certChainFile, keyFile, keyPassword, null, OpenSslDefaultApplicationProtocolNegotiator.INSTANCE, 0, 0);
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
     * @param apn Provides a means to configure parameters related to application protocol negotiation.
     * @param sessionCacheSize the size of the cache used for storing SSL session objects.
     *                         {@code 0} to use the default value.
     * @param sessionTimeout the timeout for the cached SSL session objects, in seconds.
     *                       {@code 0} to use the default value.
     */
    public OpenSslServerContext(
            File certChainFile, File keyFile, String keyPassword,
            Iterable<String> ciphers, ApplicationProtocolConfig apn,
            long sessionCacheSize, long sessionTimeout) throws SSLException {
        this(certChainFile, keyFile, keyPassword, ciphers, toNegotiator(apn, false), sessionCacheSize, sessionTimeout);
    }

    /**
     * @deprecated Use the constructors that accepts {@link ApplicationProtocolConfig} or
     *             {@link ApplicationProtocolNegotiator} instead.
     *
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
    @Deprecated
    public OpenSslServerContext(
            File certChainFile, File keyFile, String keyPassword,
            Iterable<String> ciphers, Iterable<String> nextProtocols,
            long sessionCacheSize, long sessionTimeout) throws SSLException {
        this(certChainFile, keyFile, keyPassword, ciphers,
             toApplicationProtocolConfig(nextProtocols), sessionCacheSize, sessionTimeout);
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
     * @param apn Application protocol negotiator.
     * @param sessionCacheSize the size of the cache used for storing SSL session objects.
     *                         {@code 0} to use the default value.
     * @param sessionTimeout the timeout for the cached SSL session objects, in seconds.
     *                       {@code 0} to use the default value.
     */
    public OpenSslServerContext(
            File certChainFile, File keyFile, String keyPassword,
            Iterable<String> ciphers, OpenSslApplicationProtocolNegotiator apn,
            long sessionCacheSize, long sessionTimeout) throws SSLException {

        super(ciphers, apn, sessionCacheSize, sessionTimeout, SSL.SSL_MODE_SERVER);
        OpenSsl.ensureAvailability();

        checkNotNull(certChainFile, "certChainFile");
        if (!certChainFile.isFile()) {
            throw new IllegalArgumentException("certChainFile is not a file: " + certChainFile);
        }
        checkNotNull(keyFile, "keyFile");
        if (!keyFile.isFile()) {
            throw new IllegalArgumentException("keyPath is not a file: " + keyFile);
        }
        if (keyPassword == null) {
            keyPassword = "";
        }

        // Create a new SSL_CTX and configure it.
        boolean success = false;
        try {
            synchronized (OpenSslContext.class) {
                /* Set certificate verification policy. */
                SSLContext.setVerify(ctx, SSL.SSL_CVERIFY_NONE, VERIFY_DEPTH);

                /* Load the certificate chain. We must skip the first cert when server mode */
                if (!SSLContext.setCertificateChainFile(ctx, certChainFile.getPath(), true)) {
                    String error = SSL.getLastError();
                    if (OpenSsl.isNonIgnorableError(error)) {
                        throw new SSLException(
                                "failed to set certificate chain: " + certChainFile + " (" + SSL.getLastError() + ')');
                    }
                }

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
        List<String> protos = applicationProtocolNegotiator().protocols();
        if (protos.isEmpty()) {
            return new OpenSslEngine(ctx, alloc, null, isClient(), null);
        } else {
            return new OpenSslEngine(ctx, alloc, protos.get(protos.size() - 1), isClient(), null);
        }
    }
}
