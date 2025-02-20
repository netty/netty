/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.netty.handler.ssl;

import io.netty.buffer.ByteBufAllocator;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.EmptyArrays;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.File;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Security;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.crypto.NoSuchPaddingException;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSessionContext;

import static io.netty.handler.ssl.SslUtils.DEFAULT_CIPHER_SUITES;
import static io.netty.handler.ssl.SslUtils.addIfSupported;
import static io.netty.handler.ssl.SslUtils.useFallbackCiphersIfDefaultIsEmpty;
import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * An {@link SslContext} which uses JDK's SSL/TLS implementation.
 */
public class JdkSslContext extends SslContext {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(JdkSslContext.class);

    static final String PROTOCOL = "TLS";
    private static final String[] DEFAULT_PROTOCOLS;
    private static final List<String> DEFAULT_CIPHERS;
    private static final List<String> DEFAULT_CIPHERS_NON_TLSV13;
    private static final Set<String> SUPPORTED_CIPHERS;
    private static final Set<String> SUPPORTED_CIPHERS_NON_TLSV13;
    private static final Provider DEFAULT_PROVIDER;

    static {
        Defaults defaults = new Defaults();
        defaults.init();

        DEFAULT_PROVIDER = defaults.defaultProvider;
        DEFAULT_PROTOCOLS = defaults.defaultProtocols;
        SUPPORTED_CIPHERS = defaults.supportedCiphers;
        DEFAULT_CIPHERS = defaults.defaultCiphers;
        DEFAULT_CIPHERS_NON_TLSV13 = defaults.defaultCiphersNonTLSv13;
        SUPPORTED_CIPHERS_NON_TLSV13 = defaults.supportedCiphersNonTLSv13;

        if (logger.isDebugEnabled()) {
            logger.debug("Default protocols (JDK): {} ", Arrays.asList(DEFAULT_PROTOCOLS));
            logger.debug("Default cipher suites (JDK): {}", DEFAULT_CIPHERS);
        }
    }

    private static final class Defaults {
        String[] defaultProtocols;
        List<String> defaultCiphers;
        List<String> defaultCiphersNonTLSv13;
        Set<String> supportedCiphers;
        Set<String> supportedCiphersNonTLSv13;
        Provider defaultProvider;

        void init() {
            SSLContext context;
            try {
                context = SSLContext.getInstance(PROTOCOL);
                context.init(null, null, null);
            } catch (Exception e) {
                throw new Error("failed to initialize the default SSL context", e);
            }

            defaultProvider = context.getProvider();

            SSLEngine engine = context.createSSLEngine();
            defaultProtocols = defaultProtocols(context, engine);

            supportedCiphers = Collections.unmodifiableSet(supportedCiphers(engine));
            defaultCiphers = Collections.unmodifiableList(defaultCiphers(engine, supportedCiphers));

            List<String> ciphersNonTLSv13 = new ArrayList<String>(defaultCiphers);
            ciphersNonTLSv13.removeAll(Arrays.asList(SslUtils.DEFAULT_TLSV13_CIPHER_SUITES));
            defaultCiphersNonTLSv13 = Collections.unmodifiableList(ciphersNonTLSv13);

            Set<String> suppertedCiphersNonTLSv13 = new LinkedHashSet<String>(supportedCiphers);
            suppertedCiphersNonTLSv13.removeAll(Arrays.asList(SslUtils.DEFAULT_TLSV13_CIPHER_SUITES));
            supportedCiphersNonTLSv13 = Collections.unmodifiableSet(suppertedCiphersNonTLSv13);
        }
    }

    private static String[] defaultProtocols(SSLContext context, SSLEngine engine) {
        // Choose the sensible default list of protocols that respects JDK flags, eg. jdk.tls.client.protocols
        final String[] supportedProtocols = context.getDefaultSSLParameters().getProtocols();
        Set<String> supportedProtocolsSet = new HashSet<String>(supportedProtocols.length);
        Collections.addAll(supportedProtocolsSet, supportedProtocols);
        List<String> protocols = new ArrayList<String>();
        addIfSupported(
                supportedProtocolsSet, protocols,
                SslProtocols.TLS_v1_3, SslProtocols.TLS_v1_2,
                SslProtocols.TLS_v1_1, SslProtocols.TLS_v1);

        if (!protocols.isEmpty()) {
            return protocols.toArray(EmptyArrays.EMPTY_STRINGS);
        }
        return engine.getEnabledProtocols();
    }

    private static Set<String> supportedCiphers(SSLEngine engine) {
        // Choose the sensible default list of cipher suites.
        final String[] supportedCiphers = engine.getSupportedCipherSuites();
        Set<String> supportedCiphersSet = new LinkedHashSet<String>(supportedCiphers.length);
        for (int i = 0; i < supportedCiphers.length; ++i) {
            String supportedCipher = supportedCiphers[i];
            supportedCiphersSet.add(supportedCipher);
            // IBM's J9 JVM utilizes a custom naming scheme for ciphers and only returns ciphers with the "SSL_"
            // prefix instead of the "TLS_" prefix (as defined in the JSSE cipher suite names [1]). According to IBM's
            // documentation [2] the "SSL_" prefix is "interchangeable" with the "TLS_" prefix.
            // See the IBM forum discussion [3] and issue on IBM's JVM [4] for more details.
            //[1] https://docs.oracle.com/javase/8/docs/technotes/guides/security/StandardNames.html#ciphersuites
            //[2] https://www.ibm.com/support/knowledgecenter/en/SSYKE2_8.0.0/com.ibm.java.security.component.80.doc/
            // security-component/jsse2Docs/ciphersuites.html
            //[3] https://www.ibm.com/developerworks/community/forums/html/topic?id=9b5a56a9-fa46-4031-b33b-df91e28d77c2
            //[4] https://www.ibm.com/developerworks/rfe/execute?use_case=viewRfe&CR_ID=71770
            if (supportedCipher.startsWith("SSL_")) {
                final String tlsPrefixedCipherName = "TLS_" + supportedCipher.substring("SSL_".length());
                try {
                    engine.setEnabledCipherSuites(new String[]{tlsPrefixedCipherName});
                    supportedCiphersSet.add(tlsPrefixedCipherName);
                } catch (IllegalArgumentException ignored) {
                    // The cipher is not supported ... move on to the next cipher.
                }
            }
        }
        return supportedCiphersSet;
    }

    private static List<String> defaultCiphers(SSLEngine engine, Set<String> supportedCiphers) {
        List<String> ciphers = new ArrayList<String>();
        addIfSupported(supportedCiphers, ciphers, DEFAULT_CIPHER_SUITES);
        useFallbackCiphersIfDefaultIsEmpty(ciphers, engine.getEnabledCipherSuites());
        return ciphers;
    }

    private static boolean isTlsV13Supported(String[] protocols) {
        for (String protocol: protocols) {
            if (SslProtocols.TLS_v1_3.equals(protocol)) {
                return true;
            }
        }
        return false;
    }

    private final String[] protocols;
    private final String[] cipherSuites;
    private final List<String> unmodifiableCipherSuites;
    @SuppressWarnings("deprecation")
    private final JdkApplicationProtocolNegotiator apn;
    private final ClientAuth clientAuth;
    private final SSLContext sslContext;
    private final boolean isClient;
    private final String endpointIdentificationAlgorithm;
    private final List<SNIServerName> serverNames;

    /**
     * Creates a new {@link JdkSslContext} from a pre-configured {@link SSLContext}.
     *
     * @param sslContext the {@link SSLContext} to use.
     * @param isClient {@code true} if this context should create {@link SSLEngine}s for client-side usage.
     * @param clientAuth the {@link ClientAuth} to use. This will only be used when {@param isClient} is {@code false}.
     * @deprecated Use {@link #JdkSslContext(SSLContext, boolean, Iterable, CipherSuiteFilter,
     * ApplicationProtocolConfig, ClientAuth, String[], boolean)}
     */
    @Deprecated
    public JdkSslContext(SSLContext sslContext, boolean isClient,
                         ClientAuth clientAuth) {
        this(sslContext, isClient, null, IdentityCipherSuiteFilter.INSTANCE,
                JdkDefaultApplicationProtocolNegotiator.INSTANCE, clientAuth, null, false);
    }

    /**
     * Creates a new {@link JdkSslContext} from a pre-configured {@link SSLContext}.
     *
     * @param sslContext the {@link SSLContext} to use.
     * @param isClient {@code true} if this context should create {@link SSLEngine}s for client-side usage.
     * @param ciphers the ciphers to use or {@code null} if the standard should be used.
     * @param cipherFilter the filter to use.
     * @param apn the {@link ApplicationProtocolConfig} to use.
     * @param clientAuth the {@link ClientAuth} to use. This will only be used when {@param isClient} is {@code false}.
     * @deprecated Use {@link #JdkSslContext(SSLContext, boolean, Iterable, CipherSuiteFilter,
     * ApplicationProtocolConfig, ClientAuth, String[], boolean)}
     */
    @Deprecated
    public JdkSslContext(SSLContext sslContext, boolean isClient, Iterable<String> ciphers,
                         CipherSuiteFilter cipherFilter, ApplicationProtocolConfig apn,
                         ClientAuth clientAuth) {
        this(sslContext, isClient, ciphers, cipherFilter, apn, clientAuth, null, false);
    }

    /**
     * Creates a new {@link JdkSslContext} from a pre-configured {@link SSLContext}.
     *
     * @param sslContext the {@link SSLContext} to use.
     * @param isClient {@code true} if this context should create {@link SSLEngine}s for client-side usage.
     * @param ciphers the ciphers to use or {@code null} if the standard should be used.
     * @param cipherFilter the filter to use.
     * @param apn the {@link ApplicationProtocolConfig} to use.
     * @param clientAuth the {@link ClientAuth} to use. This will only be used when {@param isClient} is {@code false}.
     * @param protocols the protocols to enable, or {@code null} to enable the default protocols.
     * @param startTls {@code true} if the first write request shouldn't be encrypted
     */
    public JdkSslContext(SSLContext sslContext,
                         boolean isClient,
                         Iterable<String> ciphers,
                         CipherSuiteFilter cipherFilter,
                         ApplicationProtocolConfig apn,
                         ClientAuth clientAuth,
                         String[] protocols,
                         boolean startTls) {
        this(sslContext,
                isClient,
                ciphers,
                cipherFilter,
                toNegotiator(apn, !isClient),
                clientAuth,
                protocols == null ? null : protocols.clone(),
                startTls);
    }

    @SuppressWarnings("deprecation")
    JdkSslContext(SSLContext sslContext, boolean isClient, Iterable<String> ciphers, CipherSuiteFilter cipherFilter,
                  JdkApplicationProtocolNegotiator apn, ClientAuth clientAuth, String[] protocols, boolean startTls) {
        this(sslContext, isClient, ciphers, cipherFilter, apn, clientAuth, protocols, startTls, null, null, null);
    }

    @SuppressWarnings("deprecation")
    JdkSslContext(SSLContext sslContext, boolean isClient, Iterable<String> ciphers, CipherSuiteFilter cipherFilter,
                  JdkApplicationProtocolNegotiator apn, ClientAuth clientAuth, String[] protocols, boolean startTls,
                  String endpointIdentificationAlgorithm, List<SNIServerName> serverNames,
                  ResumptionController resumptionController) {
        super(startTls, resumptionController);
        this.apn = checkNotNull(apn, "apn");
        this.clientAuth = checkNotNull(clientAuth, "clientAuth");
        this.sslContext = checkNotNull(sslContext, "sslContext");
        this.endpointIdentificationAlgorithm = endpointIdentificationAlgorithm;
        this.serverNames = serverNames;

        final List<String> defaultCiphers;
        final Set<String> supportedCiphers;
        if (DEFAULT_PROVIDER.equals(sslContext.getProvider())) {
            this.protocols = protocols == null? DEFAULT_PROTOCOLS : protocols;
            if (isTlsV13Supported(this.protocols)) {
                supportedCiphers = SUPPORTED_CIPHERS;
                defaultCiphers = DEFAULT_CIPHERS;
            } else {
                // TLSv1.3 is not supported, ensure we do not include any TLSv1.3 ciphersuite.
                supportedCiphers = SUPPORTED_CIPHERS_NON_TLSV13;
                defaultCiphers = DEFAULT_CIPHERS_NON_TLSV13;
            }
        } else {
            // This is a different Provider then the one used by the JDK by default so we can not just assume
            // the same protocols and ciphers are supported. For example even if Java11+ is used Conscrypt will
            // not support TLSv1.3 and the TLSv1.3 ciphersuites.
            SSLEngine engine = sslContext.createSSLEngine();
            try {
                if (protocols == null) {
                    this.protocols = defaultProtocols(sslContext, engine);
                } else {
                    this.protocols = protocols;
                }
                supportedCiphers = supportedCiphers(engine);
                defaultCiphers = defaultCiphers(engine, supportedCiphers);
                if (!isTlsV13Supported(this.protocols)) {
                    // TLSv1.3 is not supported, ensure we do not include any TLSv1.3 ciphersuite.
                    for (String cipher: SslUtils.DEFAULT_TLSV13_CIPHER_SUITES) {
                        supportedCiphers.remove(cipher);
                        defaultCiphers.remove(cipher);
                    }
                }
            } finally {
                ReferenceCountUtil.release(engine);
            }
        }

        cipherSuites = checkNotNull(cipherFilter, "cipherFilter").filterCipherSuites(
                ciphers, defaultCiphers, supportedCiphers);

        unmodifiableCipherSuites = Collections.unmodifiableList(Arrays.asList(cipherSuites));
        this.isClient = isClient;
    }

    /**
     * Returns the JDK {@link SSLContext} object held by this context.
     */
    public final SSLContext context() {
        return sslContext;
    }

    @Override
    public final boolean isClient() {
        return isClient;
    }

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
    public final SSLEngine newEngine(ByteBufAllocator alloc) {
        return configureAndWrapEngine(context().createSSLEngine(), alloc);
    }

    @Override
    public final SSLEngine newEngine(ByteBufAllocator alloc, String peerHost, int peerPort) {
        return configureAndWrapEngine(context().createSSLEngine(peerHost, peerPort), alloc);
    }

    @SuppressWarnings("deprecation")
    private SSLEngine configureAndWrapEngine(SSLEngine engine, ByteBufAllocator alloc) {
        engine.setEnabledCipherSuites(cipherSuites);
        engine.setEnabledProtocols(protocols);
        engine.setUseClientMode(isClient());
        if (isServer()) {
            switch (clientAuth) {
                case OPTIONAL:
                    engine.setWantClientAuth(true);
                    break;
                case REQUIRE:
                    engine.setNeedClientAuth(true);
                    break;
                case NONE:
                    break; // exhaustive cases
                default:
                    throw new Error("Unknown auth " + clientAuth);
            }
        }
        configureSSLParameters(engine);
        JdkApplicationProtocolNegotiator.SslEngineWrapperFactory factory = apn.wrapperFactory();
        if (factory instanceof JdkApplicationProtocolNegotiator.AllocatorAwareSslEngineWrapperFactory) {
            return ((JdkApplicationProtocolNegotiator.AllocatorAwareSslEngineWrapperFactory) factory)
                    .wrapSslEngine(engine, alloc, apn, isServer());
        }
        return factory.wrapSslEngine(engine, apn, isServer());
    }

    private void configureSSLParameters(SSLEngine engine) {
        SSLParameters params = engine.getSSLParameters();
        params.setEndpointIdentificationAlgorithm(endpointIdentificationAlgorithm);
        if (serverNames != null && !serverNames.isEmpty()) {
            params.setServerNames(serverNames);
        }
        engine.setSSLParameters(params);
    }

    @Override
    public final JdkApplicationProtocolNegotiator applicationProtocolNegotiator() {
        return apn;
    }

    /**
     * Translate a {@link ApplicationProtocolConfig} object to a {@link JdkApplicationProtocolNegotiator} object.
     * @param config The configuration which defines the translation
     * @param isServer {@code true} if a server {@code false} otherwise.
     * @return The results of the translation
     */
    @SuppressWarnings("deprecation")
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
        default:
            throw new UnsupportedOperationException(new StringBuilder("JDK provider does not support ")
            .append(config.protocol()).append(" protocol").toString());
        }
    }

    /**
     * Build a {@link KeyManagerFactory} based upon a key file, key file password, and a certificate chain.
     * @param certChainFile an X.509 certificate chain file in PEM format
     * @param keyFile a PKCS#8 private key file in PEM format
     * @param keyPassword the password of the {@code keyFile}.
     *                    {@code null} if it's not password-protected.
     * @param kmf The existing {@link KeyManagerFactory} that will be used if not {@code null}
     * @param keyStore the {@link KeyStore} that should be used in the {@link KeyManagerFactory}
     * @return A {@link KeyManagerFactory} based upon a key file, key file password, and a certificate chain.
     */
    static KeyManagerFactory buildKeyManagerFactory(File certChainFile, File keyFile, String keyPassword,
            KeyManagerFactory kmf, String keyStore)
                    throws UnrecoverableKeyException, KeyStoreException, NoSuchAlgorithmException,
                    NoSuchPaddingException, InvalidKeySpecException, InvalidAlgorithmParameterException,
                    CertificateException, KeyException, IOException {
        String algorithm = Security.getProperty("ssl.KeyManagerFactory.algorithm");
        if (algorithm == null) {
            algorithm = "SunX509";
        }
        return buildKeyManagerFactory(certChainFile, algorithm, keyFile, keyPassword, kmf, keyStore);
    }

    /**
     * Build a {@link KeyManagerFactory} based upon a key file, key file password, and a certificate chain.
     * @param certChainFile an X.509 certificate chain file in PEM format
     * @param keyFile a PKCS#8 private key file in PEM format
     * @param keyPassword the password of the {@code keyFile}.
     *                    {@code null} if it's not password-protected.
     * @param kmf The existing {@link KeyManagerFactory} that will be used if not {@code null}
     * @return A {@link KeyManagerFactory} based upon a key file, key file password, and a certificate chain.
     * @deprecated will be removed.
     */
    @Deprecated
    protected static KeyManagerFactory buildKeyManagerFactory(File certChainFile, File keyFile, String keyPassword,
                                                              KeyManagerFactory kmf)
            throws UnrecoverableKeyException, KeyStoreException, NoSuchAlgorithmException,
            NoSuchPaddingException, InvalidKeySpecException, InvalidAlgorithmParameterException,
            CertificateException, KeyException, IOException {
        return buildKeyManagerFactory(certChainFile, keyFile, keyPassword, kmf, KeyStore.getDefaultType());
    }

    /**
     * Build a {@link KeyManagerFactory} based upon a key algorithm, key file, key file password,
     * and a certificate chain.
     * @param certChainFile an X.509 certificate chain file in PEM format
     * @param keyAlgorithm the standard name of the requested algorithm. See the Java Secure Socket Extension
     *                    Reference Guide for information about standard algorithm names.
     * @param keyFile a PKCS#8 private key file in PEM format
     * @param keyPassword the password of the {@code keyFile}.
     *                    {@code null} if it's not password-protected.
     * @param kmf The existing {@link KeyManagerFactory} that will be used if not {@code null}
     * @param keyStore the {@link KeyStore} that should be used in the {@link KeyManagerFactory}
     * @return A {@link KeyManagerFactory} based upon a key algorithm, key file, key file password,
     * and a certificate chain.
     */
    static KeyManagerFactory buildKeyManagerFactory(File certChainFile,
            String keyAlgorithm, File keyFile, String keyPassword, KeyManagerFactory kmf,
            String keyStore)
                    throws KeyStoreException, NoSuchAlgorithmException, NoSuchPaddingException,
                    InvalidKeySpecException, InvalidAlgorithmParameterException, IOException,
                    CertificateException, KeyException, UnrecoverableKeyException {
        return buildKeyManagerFactory(toX509Certificates(certChainFile), keyAlgorithm,
                                      toPrivateKey(keyFile, keyPassword), keyPassword, kmf, keyStore);
    }

    /**
     * Build a {@link KeyManagerFactory} based upon a key algorithm, key file, key file password,
     * and a certificate chain.
     * @param certChainFile an buildKeyManagerFactory X.509 certificate chain file in PEM format
     * @param keyAlgorithm the standard name of the requested algorithm. See the Java Secure Socket Extension
     *                    Reference Guide for information about standard algorithm names.
     * @param keyFile a PKCS#8 private key file in PEM format
     * @param keyPassword the password of the {@code keyFile}.
     *                    {@code null} if it's not password-protected.
     * @param kmf The existing {@link KeyManagerFactory} that will be used if not {@code null}
     * @return A {@link KeyManagerFactory} based upon a key algorithm, key file, key file password,
     * and a certificate chain.
     * @deprecated will be removed.
     */
    @Deprecated
    protected static KeyManagerFactory buildKeyManagerFactory(File certChainFile,
                                                              String keyAlgorithm, File keyFile,
                                                              String keyPassword, KeyManagerFactory kmf)
            throws KeyStoreException, NoSuchAlgorithmException, NoSuchPaddingException,
            InvalidKeySpecException, InvalidAlgorithmParameterException, IOException,
            CertificateException, KeyException, UnrecoverableKeyException {
        return buildKeyManagerFactory(toX509Certificates(certChainFile), keyAlgorithm,
                toPrivateKey(keyFile, keyPassword), keyPassword, kmf, KeyStore.getDefaultType());
    }
}
