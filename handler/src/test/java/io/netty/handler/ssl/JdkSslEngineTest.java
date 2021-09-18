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

import io.netty.handler.ssl.ApplicationProtocolConfig.Protocol;
import io.netty.handler.ssl.ApplicationProtocolConfig.SelectedListenerFailureBehavior;
import io.netty.handler.ssl.ApplicationProtocolConfig.SelectorFailureBehavior;
import io.netty.handler.ssl.JdkApplicationProtocolNegotiator.ProtocolSelector;
import io.netty.handler.ssl.JdkApplicationProtocolNegotiator.ProtocolSelectorFactory;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import java.security.Provider;

import io.netty.util.internal.EmptyArrays;
import org.junit.AssumptionViolatedException;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLHandshakeException;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JdkSslEngineTest extends SSLEngineTest {
    public enum ProviderType {
        NPN_JETTY {
            @Override
            boolean isAvailable() {
                return JettyNpnSslEngine.isAvailable();
            }

            @Override
            Protocol protocol() {
                return Protocol.NPN;
            }

            @Override
            Provider provider() {
                return null;
            }
        },
        ALPN_JETTY {
            @Override
            boolean isAvailable() {
                return JettyAlpnSslEngine.isAvailable();
            }

            @Override
            Protocol protocol() {
                return Protocol.ALPN;
            }

            @Override
            Provider provider() {
                // Use the default provider.
                return null;
            }
        },
        ALPN_JAVA {
            @Override
            boolean isAvailable() {
                return JdkAlpnSslUtils.supportsAlpn();
            }

            @Override
            Protocol protocol() {
                return Protocol.ALPN;
            }

            @Override
            Provider provider() {
                // Use the default provider.
                return null;
            }
        },
        ALPN_CONSCRYPT {
            private Provider provider;

            @Override
            boolean isAvailable() {
                return Conscrypt.isAvailable();
            }

            @Override
            Protocol protocol() {
                return Protocol.ALPN;
            }

            @Override
            Provider provider() {
                try {
                    if (provider == null) {
                        provider = (Provider) Class.forName("org.conscrypt.OpenSSLProvider")
                            .getConstructor().newInstance();
                    }
                    return provider;
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }
        };

        abstract boolean isAvailable();
        abstract Protocol protocol();
        abstract Provider provider();

        final void activate(JdkSslEngineTest instance) {
            // Typical code will not have to check this, but will get a initialization error on class load.
            // Check in this test just in case we have multiple tests that just the class and we already ignored the
            // initialization error.
            if (!isAvailable()) {
                throw tlsExtensionNotFound(protocol());
            }
            instance.provider = provider();
        }
    }

    private static final String PREFERRED_APPLICATION_LEVEL_PROTOCOL = "my-protocol-http2";
    private static final String FALLBACK_APPLICATION_LEVEL_PROTOCOL = "my-protocol-http1_1";
    private static final String APPLICATION_LEVEL_PROTOCOL_NOT_COMPATIBLE = "my-protocol-FOO";

    private Provider provider;

    public JdkSslEngineTest() {
        super(SslProvider.isTlsv13Supported(SslProvider.JDK));
    }

    List<JdkSSLEngineTestParam> newJdkParams() {
        List<SSLEngineTestParam> params = newTestParams();

        List<JdkSSLEngineTestParam> jdkParams = new ArrayList<JdkSSLEngineTestParam>();
        for (ProviderType providerType: ProviderType.values()) {
            for (SSLEngineTestParam param: params) {
                jdkParams.add(new JdkSSLEngineTestParam(providerType, param));
            }
        }
        return jdkParams;
    }

    private static final class JdkSSLEngineTestParam extends SSLEngineTestParam {
        final ProviderType providerType;
        JdkSSLEngineTestParam(ProviderType providerType, SSLEngineTestParam param) {
            super(param.type(), param.combo(), param.delegate());
            this.providerType = providerType;
        }

        @Override
        public String toString() {
            return "JdkSSLEngineTestParam{" +
                    "type=" + type() +
                    ", protocolCipherCombo=" + combo() +
                    ", delegate=" + delegate() +
                    ", providerType=" + providerType +
                    '}';
        }
    }

    @MethodSource("newJdkParams")
    @ParameterizedTest
    public void testTlsExtension(JdkSSLEngineTestParam param) throws Exception {
        try {
            param.providerType.activate(this);
            ApplicationProtocolConfig apn = failingNegotiator(param.providerType.protocol(),
                PREFERRED_APPLICATION_LEVEL_PROTOCOL);
            setupHandlers(param, apn);
            runTest();
        } catch (SkipTestException e) {
            // ALPN availability is dependent on the java version. If ALPN is not available because of
            // java version incompatibility don't fail the test, but instead just skip the test
            throw new AssumptionViolatedException("Not expected", e);
        }
    }

    @MethodSource("newJdkParams")
    @ParameterizedTest
    public void testTlsExtensionNoCompatibleProtocolsNoHandshakeFailure(JdkSSLEngineTestParam param) throws Exception {
        try {
            param.providerType.activate(this);
            ApplicationProtocolConfig clientApn = acceptingNegotiator(param.providerType.protocol(),
                PREFERRED_APPLICATION_LEVEL_PROTOCOL);
            ApplicationProtocolConfig serverApn = acceptingNegotiator(param.providerType.protocol(),
                APPLICATION_LEVEL_PROTOCOL_NOT_COMPATIBLE);
            setupHandlers(param, serverApn, clientApn);
            runTest(null);
        } catch (SkipTestException e) {
            // ALPN availability is dependent on the java version. If ALPN is not available because of
            // java version incompatibility don't fail the test, but instead just skip the test
            throw new AssumptionViolatedException("Not expected", e);
        }
    }

    @MethodSource("newJdkParams")
    @ParameterizedTest
    public void testTlsExtensionNoCompatibleProtocolsClientHandshakeFailure(JdkSSLEngineTestParam param)
            throws Exception {
        try {
            param.providerType.activate(this);
            if (param.providerType == ProviderType.NPN_JETTY) {
                ApplicationProtocolConfig clientApn = failingNegotiator(param.providerType.protocol(),
                    PREFERRED_APPLICATION_LEVEL_PROTOCOL);
                ApplicationProtocolConfig serverApn = acceptingNegotiator(param.providerType.protocol(),
                    APPLICATION_LEVEL_PROTOCOL_NOT_COMPATIBLE);
                setupHandlers(param, serverApn, clientApn);
                assertTrue(clientLatch.await(2, TimeUnit.SECONDS));
                assertTrue(clientException instanceof SSLHandshakeException);
            } else {
                // ALPN
                SelfSignedCertificate ssc = new SelfSignedCertificate();
                JdkApplicationProtocolNegotiator clientApn = new JdkAlpnApplicationProtocolNegotiator(true, true,
                    PREFERRED_APPLICATION_LEVEL_PROTOCOL);
                JdkApplicationProtocolNegotiator serverApn = new JdkAlpnApplicationProtocolNegotiator(
                    new ProtocolSelectorFactory() {
                        @Override
                        public ProtocolSelector newSelector(SSLEngine engine, Set<String> supportedProtocols) {
                            return new ProtocolSelector() {
                                @Override
                                public void unsupported() {
                                }

                                @Override
                                public String select(List<String> protocols) {
                                    return APPLICATION_LEVEL_PROTOCOL_NOT_COMPATIBLE;
                                }
                            };
                        }
                    }, JdkBaseApplicationProtocolNegotiator.FAIL_SELECTION_LISTENER_FACTORY,
                    APPLICATION_LEVEL_PROTOCOL_NOT_COMPATIBLE);

                SslContext serverSslCtx = new JdkSslServerContext(param.providerType.provider(),
                    ssc.certificate(), ssc.privateKey(), null, null,
                    IdentityCipherSuiteFilter.INSTANCE, serverApn, 0, 0, null);
                SslContext clientSslCtx = new JdkSslClientContext(param.providerType.provider(), null,
                    InsecureTrustManagerFactory.INSTANCE, null,
                    IdentityCipherSuiteFilter.INSTANCE, clientApn, 0, 0);

                setupHandlers(param.type(), param.delegate(), new TestDelegatingSslContext(param, serverSslCtx),
                        new TestDelegatingSslContext(param, clientSslCtx));
                assertTrue(clientLatch.await(2, TimeUnit.SECONDS));
                // When using TLSv1.3 the handshake is NOT sent in an extra round trip which means there will be
                // no exception reported in this case but just the channel will be closed.
                assertTrue(clientException instanceof SSLHandshakeException || clientException == null);
            }
        } catch (SkipTestException e) {
            // ALPN availability is dependent on the java version. If ALPN is not available because of
            // java version incompatibility don't fail the test, but instead just skip the test
            throw new AssumptionViolatedException("Not expected", e);
        }
    }

    @MethodSource("newJdkParams")
    @ParameterizedTest
    public void testTlsExtensionNoCompatibleProtocolsServerHandshakeFailure(JdkSSLEngineTestParam param)
            throws Exception {
        try {
            param.providerType.activate(this);
            ApplicationProtocolConfig clientApn = acceptingNegotiator(param.providerType.protocol(),
                PREFERRED_APPLICATION_LEVEL_PROTOCOL);
            ApplicationProtocolConfig serverApn = failingNegotiator(param.providerType.protocol(),
                APPLICATION_LEVEL_PROTOCOL_NOT_COMPATIBLE);
            setupHandlers(param, serverApn, clientApn);
            assertTrue(serverLatch.await(2, TimeUnit.SECONDS));
            assertTrue(serverException instanceof SSLHandshakeException);
        } catch (SkipTestException e) {
            // ALPN availability is dependent on the java version. If ALPN is not available because of
            // java version incompatibility don't fail the test, but instead just skip the test
            throw new AssumptionViolatedException("Not expected", e);
        }
    }

    @MethodSource("newJdkParams")
    @ParameterizedTest
    public void testAlpnCompatibleProtocolsDifferentClientOrder(JdkSSLEngineTestParam param) throws Exception {
        try {
            param.providerType.activate(this);
            if (param.providerType == ProviderType.NPN_JETTY) {
                // This test only applies to ALPN.
                throw tlsExtensionNotFound(param.providerType.protocol());
            }
            // Even the preferred application protocol appears second in the client's list, it will be picked
            // because it's the first one on server's list.
            ApplicationProtocolConfig clientApn = acceptingNegotiator(Protocol.ALPN,
                FALLBACK_APPLICATION_LEVEL_PROTOCOL, PREFERRED_APPLICATION_LEVEL_PROTOCOL);
            ApplicationProtocolConfig serverApn = failingNegotiator(Protocol.ALPN,
                PREFERRED_APPLICATION_LEVEL_PROTOCOL, FALLBACK_APPLICATION_LEVEL_PROTOCOL);
            setupHandlers(param, serverApn, clientApn);
            assertNull(serverException);
            runTest(PREFERRED_APPLICATION_LEVEL_PROTOCOL);
        } catch (SkipTestException e) {
            // ALPN availability is dependent on the java version. If ALPN is not available because of
            // java version incompatibility don't fail the test, but instead just skip the test
            throw new AssumptionViolatedException("Not expected", e);
        }
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testEnablingAnAlreadyDisabledSslProtocol(SSLEngineTestParam param) throws Exception {
        testEnablingAnAlreadyDisabledSslProtocol(param, new String[]{}, new String[]{ SslProtocols.TLS_v1_2 });
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    @Disabled /* Does the JDK support a "max certificate chain length"? */
    @Override
    public void testMutualAuthValidClientCertChainTooLongFailOptionalClientAuth(SSLEngineTestParam param)
            throws Exception {
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    @Disabled /* Does the JDK support a "max certificate chain length"? */
    @Override
    public void testMutualAuthValidClientCertChainTooLongFailRequireClientAuth(SSLEngineTestParam param)
            throws Exception {
    }

    @Override
    protected boolean mySetupMutualAuthServerIsValidException(Throwable cause) {
        // TODO(scott): work around for a JDK issue. The exception should be SSLHandshakeException.
        return super.mySetupMutualAuthServerIsValidException(cause) || causedBySSLException(cause);
    }

    private void runTest() throws Exception {
        runTest(PREFERRED_APPLICATION_LEVEL_PROTOCOL);
    }

    @Override
    protected SslProvider sslClientProvider() {
        return SslProvider.JDK;
    }

    @Override
    protected SslProvider sslServerProvider() {
        return SslProvider.JDK;
    }

    @Override
    protected Provider clientSslContextProvider() {
        return provider;
    }

    @Override
    protected Provider serverSslContextProvider() {
        return provider;
    }

    private static ApplicationProtocolConfig failingNegotiator(Protocol protocol, String... supportedProtocols) {
        return new ApplicationProtocolConfig(protocol,
                SelectorFailureBehavior.FATAL_ALERT,
                SelectedListenerFailureBehavior.FATAL_ALERT,
                supportedProtocols);
    }

    private static ApplicationProtocolConfig acceptingNegotiator(Protocol protocol, String... supportedProtocols) {
        return new ApplicationProtocolConfig(protocol,
                SelectorFailureBehavior.NO_ADVERTISE,
                SelectedListenerFailureBehavior.ACCEPT,
                supportedProtocols);
    }

    private static SkipTestException tlsExtensionNotFound(Protocol protocol) {
        throw new SkipTestException(protocol + " not on classpath");
    }

    private static final class SkipTestException extends RuntimeException {
        private static final long serialVersionUID = 9214869217774035223L;

        SkipTestException(String message) {
            super(message);
        }
    }

    private static final class TestDelegatingSslContext extends DelegatingSslContext {
        private final SSLEngineTestParam param;

        TestDelegatingSslContext(SSLEngineTestParam param, SslContext ctx) {
            super(ctx);
            this.param = param;
        }

        @Override
        protected void initEngine(SSLEngine engine) {
            engine.setEnabledProtocols(param.protocols().toArray(EmptyArrays.EMPTY_STRINGS));
            engine.setEnabledCipherSuites(param.ciphers().toArray(EmptyArrays.EMPTY_STRINGS));
        }
    }
}
