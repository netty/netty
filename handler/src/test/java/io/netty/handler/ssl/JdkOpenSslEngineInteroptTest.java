/*
 * Copyright 2016 The Netty Project
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

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import javax.net.ssl.SSLEngine;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static io.netty.handler.ssl.OpenSslTestUtils.checkShouldUseKeyManagerFactory;
import static io.netty.internal.tcnative.SSL.SSL_CVERIFY_IGNORED;
import static org.junit.Assume.assumeTrue;

@RunWith(Parameterized.class)
public class JdkOpenSslEngineInteroptTest extends SSLEngineTest {

    @Parameterized.Parameters(name = "{index}: bufferType = {0}, combo = {1}, delegate = {2}, useTasks = {3}")
    public static Collection<Object[]> data() {
        List<Object[]> params = new ArrayList<Object[]>();
        for (BufferType type: BufferType.values()) {
            params.add(new Object[] { type, ProtocolCipherCombo.tlsv12(), false, false });
            params.add(new Object[] { type, ProtocolCipherCombo.tlsv12(), false, true });

            params.add(new Object[] { type, ProtocolCipherCombo.tlsv12(), true, false });
            params.add(new Object[] { type, ProtocolCipherCombo.tlsv12(), true, true });

            if (SslProvider.isTlsv13Supported(SslProvider.JDK) && SslProvider.isTlsv13Supported(SslProvider.OPENSSL)) {
                params.add(new Object[] { type, ProtocolCipherCombo.tlsv13(), false, false });
                params.add(new Object[] { type, ProtocolCipherCombo.tlsv13(), false, true });

                params.add(new Object[] { type, ProtocolCipherCombo.tlsv13(), true, false });
                params.add(new Object[] { type, ProtocolCipherCombo.tlsv13(), true, true });
            }
        }
        return params;
    }

    private final boolean useTasks;

    public JdkOpenSslEngineInteroptTest(BufferType type, ProtocolCipherCombo protocolCipherCombo,
                                        boolean delegate, boolean useTasks) {
        super(type, protocolCipherCombo, delegate);
        this.useTasks = useTasks;
    }

    @BeforeClass
    public static void checkOpenSsl() {
        OpenSsl.ensureAvailability();
    }

    @Override
    protected SslProvider sslClientProvider() {
        return SslProvider.JDK;
    }

    @Override
    protected SslProvider sslServerProvider() {
        return SslProvider.OPENSSL;
    }

    @Override
    @Test
    public void testMutualAuthInvalidIntermediateCASucceedWithOptionalClientAuth() throws Exception {
        checkShouldUseKeyManagerFactory();
        super.testMutualAuthInvalidIntermediateCASucceedWithOptionalClientAuth();
    }

    @Override
    @Test
    public void testMutualAuthInvalidIntermediateCAFailWithOptionalClientAuth() throws Exception {
        checkShouldUseKeyManagerFactory();
        super.testMutualAuthInvalidIntermediateCAFailWithOptionalClientAuth();
    }

    @Override
    @Test
    public void testMutualAuthInvalidIntermediateCAFailWithRequiredClientAuth() throws Exception {
        checkShouldUseKeyManagerFactory();
        super.testMutualAuthInvalidIntermediateCAFailWithRequiredClientAuth();
    }

    @Override
    @Test
    public void testMutualAuthValidClientCertChainTooLongFailOptionalClientAuth() throws Exception {
        checkShouldUseKeyManagerFactory();
        super.testMutualAuthValidClientCertChainTooLongFailOptionalClientAuth();
    }

    @Override
    @Test
    public void testMutualAuthValidClientCertChainTooLongFailRequireClientAuth() throws Exception {
        checkShouldUseKeyManagerFactory();
        super.testMutualAuthValidClientCertChainTooLongFailRequireClientAuth();
    }

    @Override
    @Test
    public void testSessionAfterHandshakeKeyManagerFactoryMutualAuth() throws Exception {
        checkShouldUseKeyManagerFactory();
        super.testSessionAfterHandshakeKeyManagerFactoryMutualAuth();
    }

    @Override
    @Test
    public void testSessionAfterHandshakeKeyManagerFactory() throws Exception {
        checkShouldUseKeyManagerFactory();
        super.testSessionAfterHandshakeKeyManagerFactory();
    }

    @Override
    protected void mySetupMutualAuthServerInitSslHandler(SslHandler handler) {
        ReferenceCountedOpenSslEngine engine = (ReferenceCountedOpenSslEngine) handler.engine();
        engine.setVerify(SSL_CVERIFY_IGNORED, 1);
    }

    @Override
    protected boolean mySetupMutualAuthServerIsValidClientException(Throwable cause) {
        // TODO(scott): work around for a JDK issue. The exception should be SSLHandshakeException.
        return super.mySetupMutualAuthServerIsValidClientException(cause) || causedBySSLException(cause);
    }

    @Override
    public void testHandshakeSession() throws Exception {
        checkShouldUseKeyManagerFactory();
        super.testHandshakeSession();
    }

    @Override
    @Test
    public void testSupportedSignatureAlgorithms() throws Exception {
        checkShouldUseKeyManagerFactory();
        super.testSupportedSignatureAlgorithms();
    }

    @Override
    @Test
    public void testSessionLocalWhenNonMutualWithKeyManager() throws Exception {
        checkShouldUseKeyManagerFactory();
        super.testSessionLocalWhenNonMutualWithKeyManager();
    }

    @Override
    public void testSessionLocalWhenNonMutualWithoutKeyManager() throws Exception {
        // This only really works when the KeyManagerFactory is supported as otherwise we not really know when
        // we need to provide a cert.
        assumeTrue(OpenSsl.supportsKeyManagerFactory());
        super.testSessionLocalWhenNonMutualWithoutKeyManager();
    }

    @Override
    @Test
    public void testSessionCache() throws Exception {
        assumeTrue(OpenSsl.isSessionCacheSupported());
        super.testSessionCache();
    }

    @Override
    @Test
    public void testSessionCacheTimeout() throws Exception {
        assumeTrue(OpenSsl.isSessionCacheSupported());
        super.testSessionCacheTimeout();
    }

    @Override
    @Test
    public void testSessionCacheSize() throws Exception {
        assumeTrue(OpenSsl.isSessionCacheSupported());
        super.testSessionCacheSize();
    }

    @Override
    protected SSLEngine wrapEngine(SSLEngine engine) {
        return Java8SslTestUtils.wrapSSLEngineForTesting(engine);
    }

    @SuppressWarnings("deprecation")
    @Override
    protected SslContext wrapContext(SslContext context) {
        if (context instanceof OpenSslContext) {
            ((OpenSslContext) context).setUseTasks(useTasks);
            // Explicit enable the session cache as its disabled by default on the client side.
            ((OpenSslContext) context).sessionContext().setSessionCacheEnabled(true);
        }
        return context;
    }
}
