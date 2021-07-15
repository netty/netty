/*
 * Copyright 2019 The Netty Project
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

import com.amazon.corretto.crypto.provider.AmazonCorrettoCryptoProvider;
import com.amazon.corretto.crypto.provider.SelfTestStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.condition.DisabledIf;

import javax.crypto.Cipher;
import java.security.Security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;


@DisabledIf("checkIfAccpIsDisabled")
public class AmazonCorrettoSslEngineTest extends SSLEngineTest {

    static boolean checkIfAccpIsDisabled() {
        return AmazonCorrettoCryptoProvider.INSTANCE.getLoadingError() != null ||
                !AmazonCorrettoCryptoProvider.INSTANCE.runSelfTests().equals(SelfTestStatus.PASSED);
    }

    public AmazonCorrettoSslEngineTest() {
        super(SslProvider.isTlsv13Supported(SslProvider.JDK));
    }

    @Override
    protected SslProvider sslClientProvider() {
        return SslProvider.JDK;
    }

    @Override
    protected SslProvider sslServerProvider() {
        return SslProvider.JDK;
    }

    @BeforeEach
    @Override
    public void setup() {
        // See https://github.com/corretto/amazon-corretto-crypto-provider/blob/develop/README.md#code
        Security.insertProviderAt(AmazonCorrettoCryptoProvider.INSTANCE, 1);

        // See https://github.com/corretto/amazon-corretto-crypto-provider/blob/develop/README.md#verification-optional
        try {
            AmazonCorrettoCryptoProvider.INSTANCE.assertHealthy();
            String providerName = Cipher.getInstance("AES/GCM/NoPadding").getProvider().getName();
            assertEquals(AmazonCorrettoCryptoProvider.PROVIDER_NAME, providerName);
        } catch (Throwable e) {
            Security.removeProvider(AmazonCorrettoCryptoProvider.PROVIDER_NAME);
            throw new AssertionError(e);
        }
        super.setup();
    }

    @AfterEach
    @Override
    public void tearDown() throws InterruptedException {
        super.tearDown();

        // Remove the provider again and verify that it was removed
        Security.removeProvider(AmazonCorrettoCryptoProvider.PROVIDER_NAME);
        assertNull(Security.getProvider(AmazonCorrettoCryptoProvider.PROVIDER_NAME));
    }

    @Disabled /* Does the JDK support a "max certificate chain length"? */
    @Override
    public void testMutualAuthValidClientCertChainTooLongFailOptionalClientAuth(SSLEngineTestParam param) {
    }

    @Disabled /* Does the JDK support a "max certificate chain length"? */
    @Override
    public void testMutualAuthValidClientCertChainTooLongFailRequireClientAuth(SSLEngineTestParam param) {
    }

    @Override
    protected boolean mySetupMutualAuthServerIsValidException(Throwable cause) {
        // TODO(scott): work around for a JDK issue. The exception should be SSLHandshakeException.
        return super.mySetupMutualAuthServerIsValidException(cause) || causedBySSLException(cause);
    }
}
