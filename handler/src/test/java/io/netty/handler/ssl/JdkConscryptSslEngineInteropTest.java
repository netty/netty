/*
 * Copyright 2017 The Netty Project
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

import java.security.Provider;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.net.ssl.SSLSessionContext;

@DisabledIf("checkConscryptDisabled")
public class JdkConscryptSslEngineInteropTest extends SSLEngineTest {

    static boolean checkConscryptDisabled() {
        return !Conscrypt.isAvailable();
    }

    public JdkConscryptSslEngineInteropTest() {
        super(false);
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
    protected Provider serverSslContextProvider() {
        return Java8SslTestUtils.conscryptProvider();
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    @Disabled("TODO: Make this work with Conscrypt")
    @Override
    public void testMutualAuthValidClientCertChainTooLongFailOptionalClientAuth(SSLEngineTestParam param)
            throws Exception {
        super.testMutualAuthValidClientCertChainTooLongFailOptionalClientAuth(param);
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    @Disabled("TODO: Make this work with Conscrypt")
    @Override
    public void testMutualAuthValidClientCertChainTooLongFailRequireClientAuth(SSLEngineTestParam param)
            throws Exception {
        super.testMutualAuthValidClientCertChainTooLongFailRequireClientAuth(param);
    }

    @Override
    protected boolean mySetupMutualAuthServerIsValidClientException(Throwable cause) {
        // TODO(scott): work around for a JDK issue. The exception should be SSLHandshakeException.
        return super.mySetupMutualAuthServerIsValidClientException(cause) || causedBySSLException(cause);
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    @Disabled("Ignore due bug in Conscrypt")
    @Override
    public void testHandshakeSession(SSLEngineTestParam param) throws Exception {
        // Ignore as Conscrypt does not correctly return the local certificates while the TrustManager is invoked.
        // See https://github.com/google/conscrypt/issues/634
    }

    @Override
    protected void invalidateSessionsAndAssert(SSLSessionContext context) {
        // Not supported by conscrypt
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    @Disabled("Possible Conscrypt bug")
    @Override
    public void testSessionCacheTimeout(SSLEngineTestParam param) {
        // Skip
        // https://github.com/google/conscrypt/issues/851
    }

    @Disabled("Not supported")
    @Override
    public void testRSASSAPSS(SSLEngineTestParam param) {
        // skip
    }
}
