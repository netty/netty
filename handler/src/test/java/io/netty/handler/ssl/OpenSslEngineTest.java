/*
 * Copyright 2015 The Netty Project
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

import static org.junit.Assume.assumeTrue;

public class OpenSslEngineTest extends SSLEngineTest {
    @Override
    public void testMutualAuthSameCerts() throws Exception {
        assumeTrue(OpenSsl.isAvailable());
        super.testMutualAuthSameCerts();
    }

    @Override
    public void testMutualAuthDiffCerts() throws Exception {
        assumeTrue(OpenSsl.isAvailable());
        super.testMutualAuthDiffCerts();
    }

    @Override
    public void testMutualAuthDiffCertsServerFailure() throws Exception {
        assumeTrue(OpenSsl.isAvailable());
        super.testMutualAuthDiffCertsServerFailure();
    }

    @Override
    public void testMutualAuthDiffCertsClientFailure() throws Exception {
        assumeTrue(OpenSsl.isAvailable());
        super.testMutualAuthDiffCertsClientFailure();
    }

    @Override
    protected SslProvider sslProvider() {
        return SslProvider.OPENSSL;
    }
}
