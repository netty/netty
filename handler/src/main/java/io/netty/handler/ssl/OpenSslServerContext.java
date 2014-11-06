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
import io.netty.buffer.ByteBufInputStream;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.apache.tomcat.jni.CertificateVerifier;
import org.apache.tomcat.jni.SSL;
import org.apache.tomcat.jni.SSLContext;

import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.File;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.List;

import static io.netty.util.internal.ObjectUtil.*;

/**
 * A server-side {@link SslContext} which uses OpenSSL's SSL/TLS implementation.
 */
public final class OpenSslServerContext extends OpenSslContext {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(OpenSslServerContext.class);

    private final OpenSslServerSessionContext sessionContext;

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
        this(certChainFile, keyFile, keyPassword, null, null,
            OpenSslDefaultApplicationProtocolNegotiator.INSTANCE, 0, 0);
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
        this(certChainFile, keyFile, keyPassword, null, ciphers,
            toNegotiator(apn, false), sessionCacheSize, sessionTimeout);
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
     * @param config Application protocol config.
     * @param sessionCacheSize the size of the cache used for storing SSL session objects.
     *                         {@code 0} to use the default value.
     * @param sessionTimeout the timeout for the cached SSL session objects, in seconds.
     *                       {@code 0} to use the default value.
     */
    public OpenSslServerContext(
            File certChainFile, File keyFile, String keyPassword, TrustManagerFactory trustManagerFactory,
            Iterable<String> ciphers, ApplicationProtocolConfig config,
            long sessionCacheSize, long sessionTimeout) throws SSLException {
        this(certChainFile, keyFile, keyPassword, trustManagerFactory, ciphers,
                toNegotiator(config, true), sessionCacheSize, sessionTimeout);
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
            File certChainFile, File keyFile, String keyPassword, TrustManagerFactory trustManagerFactory,
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
                    long error = SSL.getLastErrorNumber();
                    if (OpenSsl.isError(error)) {
                        String err = SSL.getErrorString(error);
                        throw new SSLException(
                                "failed to set certificate chain: " + certChainFile + " (" + err + ')');
                    }
                }

                /* Load the certificate file and private key. */
                try {
                    if (!SSLContext.setCertificate(
                            ctx, certChainFile.getPath(), keyFile.getPath(), keyPassword, SSL.SSL_AIDX_RSA)) {
                        long error = SSL.getLastErrorNumber();
                        if (OpenSsl.isError(error)) {
                            String err = SSL.getErrorString(error);
                            throw new SSLException("failed to set certificate: " +
                                    certChainFile + " and " + keyFile + " (" + err + ')');
                        }
                    }
                } catch (SSLException e) {
                    throw e;
                } catch (Exception e) {
                    throw new SSLException("failed to set certificate: " + certChainFile + " and " + keyFile, e);
                }
                try {
                    KeyStore ks = KeyStore.getInstance("JKS");
                    ks.load(null, null);
                    CertificateFactory cf = CertificateFactory.getInstance("X.509");
                    KeyFactory rsaKF = KeyFactory.getInstance("RSA");
                    KeyFactory dsaKF = KeyFactory.getInstance("DSA");

                    ByteBuf encodedKeyBuf = PemReader.readPrivateKey(keyFile);
                    byte[] encodedKey = new byte[encodedKeyBuf.readableBytes()];
                    encodedKeyBuf.readBytes(encodedKey).release();

                    char[] keyPasswordChars = keyPassword.toCharArray();
                    PKCS8EncodedKeySpec encodedKeySpec = generateKeySpec(keyPasswordChars, encodedKey);

                    PrivateKey key;
                    try {
                        key = rsaKF.generatePrivate(encodedKeySpec);
                    } catch (InvalidKeySpecException ignore) {
                        key = dsaKF.generatePrivate(encodedKeySpec);
                    }

                    List<Certificate> certChain = new ArrayList<Certificate>();
                    ByteBuf[] certs = PemReader.readCertificates(certChainFile);
                    try {
                        for (ByteBuf buf: certs) {
                            certChain.add(cf.generateCertificate(new ByteBufInputStream(buf)));
                        }
                    } finally {
                        for (ByteBuf buf: certs) {
                            buf.release();
                        }
                    }

                    ks.setKeyEntry("key", key, keyPasswordChars, certChain.toArray(new Certificate[certChain.size()]));

                    if (trustManagerFactory == null) {
                        // Mimic the way SSLContext.getInstance(KeyManager[], null, null) works
                        trustManagerFactory = TrustManagerFactory.getInstance(
                                TrustManagerFactory.getDefaultAlgorithm());
                        trustManagerFactory.init((KeyStore) null);
                    } else {
                        trustManagerFactory.init(ks);
                    }

                    final X509TrustManager manager = chooseTrustManager(trustManagerFactory.getTrustManagers());
                    SSLContext.setCertVerifyCallback(ctx, new CertificateVerifier() {
                        @Override
                        public boolean verify(long ssl, byte[][] chain, String auth) {
                            X509Certificate[] peerCerts = certificates(chain);
                            try {
                                manager.checkClientTrusted(peerCerts, auth);
                                return true;
                            } catch (Exception e) {
                                logger.debug("verification of certificate failed", e);
                            }
                            return false;
                        }
                    });
                } catch (Exception e) {
                    throw new SSLException("unable to setup trustmanager", e);
                }
            }
            sessionContext = new OpenSslServerSessionContext(ctx);
            success = true;
        } finally {
            if (!success) {
                destroyPools();
            }
        }
    }

    @Override
    public OpenSslServerSessionContext sessionContext() {
        return sessionContext;
    }
}
