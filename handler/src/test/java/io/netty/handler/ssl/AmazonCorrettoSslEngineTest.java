/*
 * Copyright 2019 The Netty Project
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

import com.amazon.corretto.crypto.provider.AmazonCorrettoCryptoProvider;
import com.amazon.corretto.crypto.provider.SelfTestStatus;
import io.netty.util.internal.PlatformDependent;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import javax.crypto.Cipher;
import java.security.Security;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.Assume.assumeTrue;

@RunWith(Parameterized.class)
public class AmazonCorrettoSslEngineTest extends SSLEngineTest {

    @Parameterized.Parameters(name = "{index}: bufferType = {0}, combo = {1}, delegate = {2}")
    public static Collection<Object[]> data() {
        List<Object[]> params = new ArrayList<Object[]>();
        for (BufferType type: BufferType.values()) {
            params.add(new Object[] { type, ProtocolCipherCombo.tlsv12(), false });
            params.add(new Object[] { type, ProtocolCipherCombo.tlsv12(), true });

            if (PlatformDependent.javaVersion() >= 11) {
                params.add(new Object[] { type, ProtocolCipherCombo.tlsv13(), true });
                params.add(new Object[] { type, ProtocolCipherCombo.tlsv13(), false });
            }
        }
        return params;
    }

    public AmazonCorrettoSslEngineTest(BufferType type, ProtocolCipherCombo combo, boolean delegate) {
        super(type, combo, delegate);
    }

    @BeforeClass
    public static void checkAccp() {
        assumeTrue(AmazonCorrettoCryptoProvider.INSTANCE.getLoadingError() == null &&
                AmazonCorrettoCryptoProvider.INSTANCE.runSelfTests().equals(SelfTestStatus.PASSED));
    }

    @Override
    protected SslProvider sslClientProvider() {
        return SslProvider.JDK;
    }

    @Override
    protected SslProvider sslServerProvider() {
        return SslProvider.JDK;
    }

    @Before
    @Override
    public void setup() {
        // See https://github.com/corretto/amazon-corretto-crypto-provider/blob/develop/README.md#code
        Security.insertProviderAt(AmazonCorrettoCryptoProvider.INSTANCE, 1);

        // See https://github.com/corretto/amazon-corretto-crypto-provider/blob/develop/README.md#verification-optional
        try {
            AmazonCorrettoCryptoProvider.INSTANCE.assertHealthy();
            String providerName = Cipher.getInstance("AES/GCM/NoPadding").getProvider().getName();
            Assert.assertEquals(AmazonCorrettoCryptoProvider.PROVIDER_NAME, providerName);
        } catch (Throwable e) {
            Security.removeProvider(AmazonCorrettoCryptoProvider.PROVIDER_NAME);
            throw new AssertionError(e);
        }
        super.setup();
    }

    @After
    @Override
    public void tearDown() throws InterruptedException {
        super.tearDown();

        // Remove the provider again and verify that it was removed
        Security.removeProvider(AmazonCorrettoCryptoProvider.PROVIDER_NAME);
        Assert.assertNull(Security.getProvider(AmazonCorrettoCryptoProvider.PROVIDER_NAME));
    }

    @Ignore /* Does the JDK support a "max certificate chain length"? */
    @Override
    public void testMutualAuthValidClientCertChainTooLongFailOptionalClientAuth() {
    }

    @Ignore /* Does the JDK support a "max certificate chain length"? */
    @Override
    public void testMutualAuthValidClientCertChainTooLongFailRequireClientAuth() {
    }

    @Override
    protected boolean mySetupMutualAuthServerIsValidException(Throwable cause) {
        // TODO(scott): work around for a JDK issue. The exception should be SSLHandshakeException.
        return super.mySetupMutualAuthServerIsValidException(cause) || causedBySSLException(cause);
    }
}
