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

import io.netty.handler.ssl.ApplicationProtocolConfig.Protocol;
import io.netty.handler.ssl.ApplicationProtocolConfig.SelectedListenerFailureBehavior;
import io.netty.handler.ssl.ApplicationProtocolConfig.SelectorFailureBehavior;
import io.netty.handler.ssl.JdkApplicationProtocolNegotiator.ProtocolSelector;
import io.netty.handler.ssl.JdkApplicationProtocolNegotiator.ProtocolSelectorFactory;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import org.junit.Test;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLHandshakeException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeNoException;

public class JdkSslEngineTest extends SSLEngineTest {
    private static final String PREFERRED_APPLICATION_LEVEL_PROTOCOL = "my-protocol-http2";
    private static final String FALLBACK_APPLICATION_LEVEL_PROTOCOL = "my-protocol-http1_1";
    private static final String APPLICATION_LEVEL_PROTOCOL_NOT_COMPATIBLE = "my-protocol-FOO";

    @Test
    public void testNpn() throws Exception {
        try {
            // Typical code will not have to check this, but will get a initialization error on class load.
            // Check in this test just in case we have multiple tests that just the class and we already ignored the
            // initialization error.
            if (!JdkNpnSslEngine.isAvailable()) {
                throw tlsExtensionNotFound(Protocol.NPN);
            }
            ApplicationProtocolConfig apn = failingNegotiator(Protocol.NPN,
                    PREFERRED_APPLICATION_LEVEL_PROTOCOL);
            setupHandlers(apn);
            runTest();
        } catch (SkipTestException e) {
            // NPN availability is dependent on the java version. If NPN is not available because of
            // java version incompatibility don't fail the test, but instead just skip the test
            assumeNoException(e);
        }
    }

    @Test
    public void testNpnNoCompatibleProtocolsNoHandshakeFailure() throws Exception {
        try {
            // Typical code will not have to check this, but will get a initialization error on class load.
            // Check in this test just in case we have multiple tests that just the class and we already ignored the
            // initialization error.
            if (!JdkNpnSslEngine.isAvailable()) {
                throw tlsExtensionNotFound(Protocol.NPN);
            }
            ApplicationProtocolConfig clientApn = acceptingNegotiator(Protocol.NPN,
                    PREFERRED_APPLICATION_LEVEL_PROTOCOL);
            ApplicationProtocolConfig serverApn = acceptingNegotiator(Protocol.NPN,
                    APPLICATION_LEVEL_PROTOCOL_NOT_COMPATIBLE);
            setupHandlers(serverApn, clientApn);
            runTest(null);
        } catch (SkipTestException e) {
            // ALPN availability is dependent on the java version. If ALPN is not available because of
            // java version incompatibility don't fail the test, but instead just skip the test
            assumeNoException(e);
        }
    }

    @Test
    public void testNpnNoCompatibleProtocolsClientHandshakeFailure() throws Exception {
        try {
            // Typical code will not have to check this, but will get a initialization error on class load.
            // Check in this test just in case we have multiple tests that just the class and we already ignored the
            // initialization error.
            if (!JdkNpnSslEngine.isAvailable()) {
                throw tlsExtensionNotFound(Protocol.NPN);
            }
            ApplicationProtocolConfig clientApn = failingNegotiator(Protocol.NPN,
                    PREFERRED_APPLICATION_LEVEL_PROTOCOL);
            ApplicationProtocolConfig serverApn = acceptingNegotiator(Protocol.NPN,
                    APPLICATION_LEVEL_PROTOCOL_NOT_COMPATIBLE);
            setupHandlers(serverApn, clientApn);
            assertTrue(clientLatch.await(2, TimeUnit.SECONDS));
            assertTrue(clientException instanceof SSLHandshakeException);
        } catch (SkipTestException e) {
            // NPN availability is dependent on the java version. If NPN is not available because of
            // java version incompatibility don't fail the test, but instead just skip the test
            assumeNoException(e);
        }
    }

    @Test
    public void testNpnNoCompatibleProtocolsServerHandshakeFailure() throws Exception {
        try {
            // Typical code will not have to check this, but will get a initialization error on class load.
            // Check in this test just in case we have multiple tests that just the class and we already ignored the
            // initialization error.
            if (!JdkNpnSslEngine.isAvailable()) {
                throw tlsExtensionNotFound(Protocol.NPN);
            }
            ApplicationProtocolConfig clientApn = acceptingNegotiator(Protocol.NPN,
                    PREFERRED_APPLICATION_LEVEL_PROTOCOL);
            ApplicationProtocolConfig serverApn = failingNegotiator(Protocol.NPN,
                    APPLICATION_LEVEL_PROTOCOL_NOT_COMPATIBLE);
            setupHandlers(serverApn, clientApn);
            assertTrue(serverLatch.await(2, TimeUnit.SECONDS));
            assertTrue(serverException instanceof SSLHandshakeException);
        } catch (SkipTestException e) {
            // NPN availability is dependent on the java version. If NPN is not available because of
            // java version incompatibility don't fail the test, but instead just skip the test
            assumeNoException(e);
        }
    }

    @Test
    public void testAlpn() throws Exception {
        try {
            // Typical code will not have to check this, but will get a initialization error on class load.
            // Check in this test just in case we have multiple tests that just the class and we already ignored the
            // initialization error.
            if (!JdkAlpnSslEngine.isAvailable()) {
                throw tlsExtensionNotFound(Protocol.ALPN);
            }
            ApplicationProtocolConfig apn = failingNegotiator(Protocol.ALPN,
                    PREFERRED_APPLICATION_LEVEL_PROTOCOL);
            setupHandlers(apn);
            runTest();
        } catch (SkipTestException e) {
            // ALPN availability is dependent on the java version. If ALPN is not available because of
            // java version incompatibility don't fail the test, but instead just skip the test
            assumeNoException(e);
        }
    }

    @Test
    public void testAlpnNoCompatibleProtocolsNoHandshakeFailure() throws Exception {
        try {
            // Typical code will not have to check this, but will get a initialization error on class load.
            // Check in this test just in case we have multiple tests that just the class and we already ignored the
            // initialization error.
            if (!JdkAlpnSslEngine.isAvailable()) {
                throw tlsExtensionNotFound(Protocol.ALPN);
            }
            ApplicationProtocolConfig clientApn = acceptingNegotiator(Protocol.ALPN,
                    PREFERRED_APPLICATION_LEVEL_PROTOCOL);
            ApplicationProtocolConfig serverApn = acceptingNegotiator(Protocol.ALPN,
                    APPLICATION_LEVEL_PROTOCOL_NOT_COMPATIBLE);
            setupHandlers(serverApn, clientApn);
            runTest(null);
        } catch (SkipTestException e) {
            // ALPN availability is dependent on the java version. If ALPN is not available because of
            // java version incompatibility don't fail the test, but instead just skip the test
            assumeNoException(e);
        }
    }

    @Test
    public void testAlpnNoCompatibleProtocolsServerHandshakeFailure() throws Exception {
        try {
            // Typical code will not have to check this, but will get a initialization error on class load.
            // Check in this test just in case we have multiple tests that just the class and we already ignored the
            // initialization error.
            if (!JdkAlpnSslEngine.isAvailable()) {
                throw tlsExtensionNotFound(Protocol.ALPN);
            }
            ApplicationProtocolConfig clientApn = acceptingNegotiator(Protocol.ALPN,
                    PREFERRED_APPLICATION_LEVEL_PROTOCOL);
            ApplicationProtocolConfig serverApn = failingNegotiator(Protocol.ALPN,
                    APPLICATION_LEVEL_PROTOCOL_NOT_COMPATIBLE);
            setupHandlers(serverApn, clientApn);
            assertTrue(serverLatch.await(2, TimeUnit.SECONDS));
            assertTrue(serverException instanceof SSLHandshakeException);
        } catch (SkipTestException e) {
            // ALPN availability is dependent on the java version. If ALPN is not available because of
            // java version incompatibility don't fail the test, but instead just skip the test
            assumeNoException(e);
        }
    }

    @Test
    public void testAlpnCompatibleProtocolsDifferentClientOrder() throws Exception {
        try {
            // Typical code will not have to check this, but will get a initialization error on class load.
            // Check in this test just in case we have multiple tests that just the class and we already ignored the
            // initialization error.
            if (!JdkAlpnSslEngine.isAvailable()) {
                throw tlsExtensionNotFound(Protocol.ALPN);
            }
            // Even the preferred application protocol appears second in the client's list, it will be picked
            // because it's the first one on server's list.
            ApplicationProtocolConfig clientApn = acceptingNegotiator(Protocol.ALPN,
                FALLBACK_APPLICATION_LEVEL_PROTOCOL, PREFERRED_APPLICATION_LEVEL_PROTOCOL);
            ApplicationProtocolConfig serverApn = failingNegotiator(Protocol.ALPN,
                PREFERRED_APPLICATION_LEVEL_PROTOCOL, FALLBACK_APPLICATION_LEVEL_PROTOCOL);
            setupHandlers(serverApn, clientApn);
            assertNull(serverException);
            runTest(PREFERRED_APPLICATION_LEVEL_PROTOCOL);
        } catch (SkipTestException e) {
            // ALPN availability is dependent on the java version. If ALPN is not available because of
            // java version incompatibility don't fail the test, but instead just skip the test
            assumeNoException(e);
        }
    }

    @Test
    public void testAlpnNoCompatibleProtocolsClientHandshakeFailure() throws Exception {
        try {
            // Typical code will not have to check this, but will get a initialization error on class load.
            // Check in this test just in case we have multiple tests that just the class and we already ignored the
            // initialization error.
            if (!JdkAlpnSslEngine.isAvailable()) {
                throw tlsExtensionNotFound(Protocol.ALPN);
            }
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

            SslContext serverSslCtx = new JdkSslServerContext(ssc.certificate(), ssc.privateKey(), null, null,
                    IdentityCipherSuiteFilter.INSTANCE, serverApn, 0, 0);
            SslContext clientSslCtx = new JdkSslClientContext(null, InsecureTrustManagerFactory.INSTANCE, null,
                    IdentityCipherSuiteFilter.INSTANCE, clientApn, 0, 0);

            setupHandlers(serverSslCtx, clientSslCtx);
            assertTrue(clientLatch.await(2, TimeUnit.SECONDS));
            assertTrue(clientException instanceof SSLHandshakeException);
        } catch (SkipTestException e) {
            // ALPN availability is dependent on the java version. If ALPN is not available because of
            // java version incompatibility don't fail the test, but instead just skip the test
            assumeNoException(e);
        }
    }

    @Test
    public void testEnablingAnAlreadyDisabledSslProtocol() throws Exception {
        testEnablingAnAlreadyDisabledSslProtocol(new String[]{}, new String[]{PROTOCOL_TLS_V1_2});
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

    private ApplicationProtocolConfig failingNegotiator(Protocol protocol,
                                                        String... supportedProtocols) {
        return new ApplicationProtocolConfig(protocol,
                SelectorFailureBehavior.FATAL_ALERT,
                SelectedListenerFailureBehavior.FATAL_ALERT,
                supportedProtocols);
    }

    private ApplicationProtocolConfig acceptingNegotiator(Protocol protocol,
                                                          String... supportedProtocols) {
        return new ApplicationProtocolConfig(protocol,
                SelectorFailureBehavior.NO_ADVERTISE,
                SelectedListenerFailureBehavior.ACCEPT,
                supportedProtocols);
    }

    private SkipTestException tlsExtensionNotFound(Protocol protocol) {
        throw new SkipTestException(protocol + " not on classpath");
    }

    private static final class SkipTestException extends RuntimeException {
        public SkipTestException(String message) {
            super(message);
        }
    }
}
