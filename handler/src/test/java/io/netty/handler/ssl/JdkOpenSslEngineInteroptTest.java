/*
 * Copyright 2016 The Netty Project
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

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static io.netty.handler.ssl.OpenSslTestUtils.checkShouldUseKeyManagerFactory;
import static io.netty.internal.tcnative.SSL.SSL_CVERIFY_IGNORED;
import static org.junit.Assume.assumeTrue;

@RunWith(Parameterized.class)
public class JdkOpenSslEngineInteroptTest extends SSLEngineTest {

    @Parameterized.Parameters(name = "{index}: bufferType = {0}")
    public static Collection<Object> data() {
        List<Object> params = new ArrayList<Object>();
        for (BufferType type: BufferType.values()) {
            params.add(type);
        }
        return params;
    }

    public JdkOpenSslEngineInteroptTest(BufferType type) {
        super(type);
    }

    @BeforeClass
    public static void checkOpenSsl() {
        assumeTrue(OpenSsl.isAvailable());
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
    protected void mySetupMutualAuthServerInitSslHandler(SslHandler handler) {
        ReferenceCountedOpenSslEngine engine = (ReferenceCountedOpenSslEngine) handler.engine();
        engine.setVerify(SSL_CVERIFY_IGNORED, 1);
    }

    @Override
    protected boolean mySetupMutualAuthServerIsValidClientException(Throwable cause) {
        // TODO(scott): work around for a JDK issue. The exception should be SSLHandshakeException.
        return super.mySetupMutualAuthServerIsValidClientException(cause) || causedBySSLException(cause);
    }
}
