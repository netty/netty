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
import io.netty.buffer.ByteBufInputStream;
import io.netty.util.internal.EmptyArrays;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import javax.crypto.NoSuchPaddingException;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.TrustManagerFactory;
import javax.security.auth.x500.X500Principal;
import java.io.File;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static io.netty.util.internal.ObjectUtil.*;

/**
 * An {@link SslContext} which uses JDK's SSL/TLS implementation.
 */
public abstract class JdkSslContext extends SslContext {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(JdkSslContext.class);

    static final String PROTOCOL = "TLS";
    static final String[] PROTOCOLS;
    static final List<String> DEFAULT_CIPHERS;
    static final Set<String> SUPPORTED_CIPHERS;

    static {
        SSLContext context;
        int i;
        try {
            context = SSLContext.getInstance(PROTOCOL);
            context.init(null, null, null);
        } catch (Exception e) {
            throw new Error("failed to initialize the default SSL context", e);
        }

        SSLEngine engine = context.createSSLEngine();

        // Choose the sensible default list of protocols.
        final String[] supportedProtocols = engine.getSupportedProtocols();
        Set<String> supportedProtocolsSet = new HashSet<String>(supportedProtocols.length);
        for (i = 0; i < supportedProtocols.length; ++i) {
            supportedProtocolsSet.add(supportedProtocols[i]);
        }
        List<String> protocols = new ArrayList<String>();
        addIfSupported(
                supportedProtocolsSet, protocols,
                "TLSv1.2", "TLSv1.1", "TLSv1");

        if (!protocols.isEmpty()) {
            PROTOCOLS = protocols.toArray(new String[protocols.size()]);
        } else {
            PROTOCOLS = engine.getEnabledProtocols();
        }

        // Choose the sensible default list of cipher suites.
        final String[] supportedCiphers = engine.getSupportedCipherSuites();
        SUPPORTED_CIPHERS = new HashSet<String>(supportedCiphers.length);
        for (i = 0; i < supportedCiphers.length; ++i) {
            SUPPORTED_CIPHERS.add(supportedCiphers[i]);
        }
        List<String> ciphers = new ArrayList<String>();
        addIfSupported(
                SUPPORTED_CIPHERS, ciphers,
                // XXX: Make sure to sync this list with OpenSslEngineFactory.
                // GCM (Galois/Counter Mode) requires JDK 8.
                "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
                "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA",
                // AES256 requires JCE unlimited strength jurisdiction policy files.
                "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA",
                // GCM (Galois/Counter Mode) requires JDK 8.
                "TLS_RSA_WITH_AES_128_GCM_SHA256",
                "TLS_RSA_WITH_AES_128_CBC_SHA",
                // AES256 requires JCE unlimited strength jurisdiction policy files.
                "TLS_RSA_WITH_AES_256_CBC_SHA",
                "SSL_RSA_WITH_3DES_EDE_CBC_SHA",
                "SSL_RSA_WITH_RC4_128_SHA");

        if (!ciphers.isEmpty()) {
            DEFAULT_CIPHERS = Collections.unmodifiableList(ciphers);
        } else {
            // Use the default from JDK as fallback.
            DEFAULT_CIPHERS = Collections.unmodifiableList(Arrays.asList(engine.getEnabledCipherSuites()));
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Default protocols (JDK): {} ", Arrays.asList(PROTOCOLS));
            logger.debug("Default cipher suites (JDK): {}", DEFAULT_CIPHERS);
        }
    }

    private static void addIfSupported(Set<String> supported, List<String> enabled, String... names) {
        for (String n: names) {
            if (supported.contains(n)) {
                enabled.add(n);
            }
        }
    }

    private final String[] cipherSuites;
    private final List<String> unmodifiableCipherSuites;
    private final JdkApplicationProtocolNegotiator apn;

    JdkSslContext(Iterable<String> ciphers, CipherSuiteFilter cipherFilter, ApplicationProtocolConfig config,
            boolean isServer) {
        this(ciphers, cipherFilter, toNegotiator(config, isServer));
    }

    JdkSslContext(Iterable<String> ciphers, CipherSuiteFilter cipherFilter, JdkApplicationProtocolNegotiator apn) {
        this.apn = checkNotNull(apn, "apn");
        cipherSuites = checkNotNull(cipherFilter, "cipherFilter").filterCipherSuites(
                ciphers, DEFAULT_CIPHERS, SUPPORTED_CIPHERS);
        unmodifiableCipherSuites = Collections.unmodifiableList(Arrays.asList(cipherSuites));
    }

    /**
     * Returns the JDK {@link SSLContext} object held by this context.
     */
    public abstract SSLContext context();

    /**
     * Returns the JDK {@link SSLSessionContext} object held by this context.
     */
    @Override
    public final SSLSessionContext sessionContext() {
        if (isServer()) {
            return context().getServerSessionContext();
        } else {
            return context().getClientSessionContext();
        }
    }

    @Override
    public final List<String> cipherSuites() {
        return unmodifiableCipherSuites;
    }

    @Override
    public final long sessionCacheSize() {
        return sessionContext().getSessionCacheSize();
    }

    @Override
    public final long sessionTimeout() {
        return sessionContext().getSessionTimeout();
    }

    @Override
    public final SSLEngine newEngine(ByteBufAllocator alloc) {
        SSLEngine engine = context().createSSLEngine();
        engine.setEnabledCipherSuites(cipherSuites);
        engine.setEnabledProtocols(PROTOCOLS);
        engine.setUseClientMode(isClient());
        return wrapEngine(engine);
    }

    @Override
    public final SSLEngine newEngine(ByteBufAllocator alloc, String peerHost, int peerPort) {
        SSLEngine engine = context().createSSLEngine(peerHost, peerPort);
        engine.setEnabledCipherSuites(cipherSuites);
        engine.setEnabledProtocols(PROTOCOLS);
        engine.setUseClientMode(isClient());
        return wrapEngine(engine);
    }

    private SSLEngine wrapEngine(SSLEngine engine) {
        return apn.wrapperFactory().wrapSslEngine(engine, apn, isServer());
    }

    @Override
    public JdkApplicationProtocolNegotiator applicationProtocolNegotiator() {
        return apn;
    }

    /**
     * Translate a {@link ApplicationProtocolConfig} object to a {@link JdkApplicationProtocolNegotiator} object.
     * @param config The configuration which defines the translation
     * @param isServer {@code true} if a server {@code false} otherwise.
     * @return The results of the translation
     */
    static JdkApplicationProtocolNegotiator toNegotiator(ApplicationProtocolConfig config, boolean isServer) {
        if (config == null) {
            return JdkDefaultApplicationProtocolNegotiator.INSTANCE;
        }

        switch(config.protocol()) {
        case NONE:
            return JdkDefaultApplicationProtocolNegotiator.INSTANCE;
        case ALPN:
            if (isServer) {
                switch(config.selectorFailureBehavior()) {
                case FATAL_ALERT:
                    return new JdkAlpnApplicationProtocolNegotiator(true, config.supportedProtocols());
                case NO_ADVERTISE:
                    return new JdkAlpnApplicationProtocolNegotiator(false, config.supportedProtocols());
                default:
                    throw new UnsupportedOperationException(new StringBuilder("JDK provider does not support ")
                    .append(config.selectorFailureBehavior()).append(" failure behavior").toString());
                }
            } else {
                switch(config.selectedListenerFailureBehavior()) {
                case ACCEPT:
                    return new JdkAlpnApplicationProtocolNegotiator(false, config.supportedProtocols());
                case FATAL_ALERT:
                    return new JdkAlpnApplicationProtocolNegotiator(true, config.supportedProtocols());
                default:
                    throw new UnsupportedOperationException(new StringBuilder("JDK provider does not support ")
                    .append(config.selectedListenerFailureBehavior()).append(" failure behavior").toString());
                }
            }
        case NPN:
            if (isServer) {
                switch(config.selectedListenerFailureBehavior()) {
                case ACCEPT:
                    return new JdkNpnApplicationProtocolNegotiator(false, config.supportedProtocols());
                case FATAL_ALERT:
                    return new JdkNpnApplicationProtocolNegotiator(true, config.supportedProtocols());
                default:
                    throw new UnsupportedOperationException(new StringBuilder("JDK provider does not support ")
                    .append(config.selectedListenerFailureBehavior()).append(" failure behavior").toString());
                }
            } else {
                switch(config.selectorFailureBehavior()) {
                case FATAL_ALERT:
                    return new JdkNpnApplicationProtocolNegotiator(true, config.supportedProtocols());
                case NO_ADVERTISE:
                    return new JdkNpnApplicationProtocolNegotiator(false, config.supportedProtocols());
                default:
                    throw new UnsupportedOperationException(new StringBuilder("JDK provider does not support ")
                    .append(config.selectorFailureBehavior()).append(" failure behavior").toString());
                }
            }
        default:
            throw new UnsupportedOperationException(new StringBuilder("JDK provider does not support ")
            .append(config.protocol()).append(" protocol").toString());
        }
    }

    /**
     * Build a {@link KeyManagerFactory} based upon a key file, key file password, and a certificate chain.
     * @param certChainFile a X.509 certificate chain file in PEM format
     * @param keyFile a PKCS#8 private key file in PEM format
     * @param keyPassword the password of the {@code keyFile}.
     *                    {@code null} if it's not password-protected.
     * @param kmf The existing {@link KeyManagerFactory} that will be used if not {@code null}
     * @return A {@link KeyManagerFactory} based upon a key file, key file password, and a certificate chain.
     */
    protected static KeyManagerFactory buildKeyManagerFactory(File certChainFile, File keyFile, String keyPassword,
            KeyManagerFactory kmf)
                    throws UnrecoverableKeyException, KeyStoreException, NoSuchAlgorithmException,
                    NoSuchPaddingException, InvalidKeySpecException, InvalidAlgorithmParameterException,
                    CertificateException, KeyException, IOException {
        String algorithm = Security.getProperty("ssl.KeyManagerFactory.algorithm");
        if (algorithm == null) {
            algorithm = "SunX509";
        }
        return buildKeyManagerFactory(certChainFile, algorithm, keyFile, keyPassword, kmf);
    }

    /**
     * Build a {@link KeyManagerFactory} based upon a key algorithm, key file, key file password,
     * and a certificate chain.
     * @param certChainFile a X.509 certificate chain file in PEM format
     * @param keyAlgorithm the standard name of the requested algorithm. See the Java Secure Socket Extension
     * Reference Guide for information about standard algorithm names.
     * @param keyFile a PKCS#8 private key file in PEM format
     * @param keyPassword the password of the {@code keyFile}.
     *                    {@code null} if it's not password-protected.
     * @param kmf The existing {@link KeyManagerFactory} that will be used if not {@code null}
     * @return A {@link KeyManagerFactory} based upon a key algorithm, key file, key file password,
     * and a certificate chain.
     */
    protected static KeyManagerFactory buildKeyManagerFactory(File certChainFile,
            String keyAlgorithm, File keyFile, String keyPassword, KeyManagerFactory kmf)
                    throws KeyStoreException, NoSuchAlgorithmException, NoSuchPaddingException,
                    InvalidKeySpecException, InvalidAlgorithmParameterException, IOException,
                    CertificateException, KeyException, UnrecoverableKeyException {
        char[] keyPasswordChars = keyPassword == null ? EmptyArrays.EMPTY_CHARS : keyPassword.toCharArray();
        KeyStore ks = buildKeyStore(certChainFile, keyFile, keyPasswordChars);
        // Set up key manager factory to use our key store
        if (kmf == null) {
            kmf = KeyManagerFactory.getInstance(keyAlgorithm);
        }
        kmf.init(ks, keyPasswordChars);

        return kmf;
    }

    /**
     * Build a {@link TrustManagerFactory} from a certificate chain file.
     * @param certChainFile The certificate file to build from.
     * @param trustManagerFactory The existing {@link TrustManagerFactory} that will be used if not {@code null}.
     * @return A {@link TrustManagerFactory} which contains the certificates in {@code certChainFile}
     */
    protected static TrustManagerFactory buildTrustManagerFactory(File certChainFile,
            TrustManagerFactory trustManagerFactory)
                    throws NoSuchAlgorithmException, CertificateException, KeyStoreException, IOException {
        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(null, null);
        CertificateFactory cf = CertificateFactory.getInstance("X.509");

        ByteBuf[] certs = PemReader.readCertificates(certChainFile);
        try {
            for (ByteBuf buf: certs) {
                X509Certificate cert = (X509Certificate) cf.generateCertificate(new ByteBufInputStream(buf));
                X500Principal principal = cert.getSubjectX500Principal();
                ks.setCertificateEntry(principal.getName("RFC2253"), cert);
            }
        } finally {
            for (ByteBuf buf: certs) {
                buf.release();
            }
        }

        // Set up trust manager factory to use our key store.
        if (trustManagerFactory == null) {
            trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        }
        trustManagerFactory.init(ks);

        return trustManagerFactory;
    }
}
