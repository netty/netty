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

import java.security.Provider;
import org.junit.BeforeClass;
import org.junit.Ignore;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.Assume.assumeTrue;

@RunWith(Parameterized.class)
public class ConscryptJdkSslEngineInteropTest extends SSLEngineTest {

    @Parameterized.Parameters(name = "{index}: bufferType = {0}, combo = {1}, delegate = {2}")
    public static Collection<Object[]> data() {
        List<Object[]> params = new ArrayList<Object[]>();
        for (BufferType type: BufferType.values()) {
            params.add(new Object[] { type, ProtocolCipherCombo.tlsv12(), false });
            params.add(new Object[] { type, ProtocolCipherCombo.tlsv12(), true });
        }
        return params;
    }

    public ConscryptJdkSslEngineInteropTest(BufferType type, ProtocolCipherCombo combo, boolean delegate) {
        super(type, combo, delegate);
    }

    @BeforeClass
    public static void checkConscrypt() {
        assumeTrue(Conscrypt.isAvailable());
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
        return Java8SslTestUtils.conscryptProvider();
    }

    @Ignore /* Does the JDK support a "max certificate chain length"? */
    @Override
    public void testMutualAuthValidClientCertChainTooLongFailOptionalClientAuth() throws Exception {
    }

    @Ignore /* Does the JDK support a "max certificate chain length"? */
    @Override
    public void testMutualAuthValidClientCertChainTooLongFailRequireClientAuth() throws Exception {
    }

    @Override
    protected boolean mySetupMutualAuthServerIsValidServerException(Throwable cause) {
        // TODO(scott): work around for a JDK issue. The exception should be SSLHandshakeException.
        return super.mySetupMutualAuthServerIsValidServerException(cause) || causedBySSLException(cause);
    }
}
