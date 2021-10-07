/*
 * Copyright 2018 The Netty Project
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


import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.net.ssl.SSLSessionContext;
import java.security.Provider;

@DisabledIf("checkConscryptDisabled")
public class ConscryptSslEngineTest extends SSLEngineTest {

    static boolean checkConscryptDisabled() {
        return !Conscrypt.isAvailable();
    }

    public ConscryptSslEngineTest() {
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
    protected Provider clientSslContextProvider() {
        return Java8SslTestUtils.conscryptProvider();
    }

    @Override
    protected Provider serverSslContextProvider() {
        return Java8SslTestUtils.conscryptProvider();
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    @Disabled /* Does the JDK support a "max certificate chain length"? */
    @Override
    public void testMutualAuthValidClientCertChainTooLongFailOptionalClientAuth(SSLEngineTestParam param) {
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    @Disabled /* Does the JDK support a "max certificate chain length"? */
    @Override
    public void testMutualAuthValidClientCertChainTooLongFailRequireClientAuth(SSLEngineTestParam param) {
    }

    @Override
    protected void invalidateSessionsAndAssert(SSLSessionContext context) {
        // Not supported by conscrypt
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    @Disabled("Possible Conscrypt bug")
    @Override
    public void testSessionCacheTimeout(SSLEngineTestParam param) throws Exception {
        // Skip
        // https://github.com/google/conscrypt/issues/851
    }

    @Disabled("Not supported")
    @Override
    public void testRSASSAPSS(SSLEngineTestParam param) {
        // skip
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    @Disabled("Disabled due a conscrypt bug")
    @Override
    public void testInvalidSNIIsIgnoredAndNotThrow(SSLEngineTestParam param) throws Exception {
        super.testInvalidSNIIsIgnoredAndNotThrow(param);
    }
}
