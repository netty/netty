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

import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.junit.jupiter.api.Test;

import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.TrustManagerFactorySpi;
import java.io.File;
import java.security.KeyStore;
import java.security.Provider;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class JdkSslClientContextTest extends SslContextTest {
    @Override
    protected SslContext newSslContext(File crtFile, File keyFile, String pass) throws SSLException {
        return new JdkSslClientContext(crtFile, InsecureTrustManagerFactory.INSTANCE, crtFile, keyFile, pass,
                null, null, IdentityCipherSuiteFilter.INSTANCE, ApplicationProtocolConfig.DISABLED, 0, 0);
    }

    // Reproduces https://github.com/netty/netty/issues/14488
    // Before the fix, wrapIfNeeded did tms.length without a null check, so a
    // TrustManagerFactory whose getTrustManagers() returns null surfaced as an
    // opaque NullPointerException wrapped in SSLException. After the fix the
    // context is built successfully, matching pre-4.1.114 behavior that relied
    // on SSLContext.init accepting a null TrustManager[] to use platform defaults.
    @Test
    public void testTrustManagerFactoryReturningNullDoesNotThrowNpe() throws Exception {
        TrustManagerFactory tmf = new TrustManagerFactory(
                new NullReturningTrustManagerFactorySpi(), NullReturningTrustManagerFactorySpi.PROVIDER, "null") {
        };
        // TrustManagerFactory must be initialized before SslContextBuilder will accept it;
        // without this call the builder throws before reaching the code path under test.
        tmf.init((KeyStore) null);

        SslContext ctx = SslContextBuilder.forClient()
                .sslProvider(SslProvider.JDK)
                .trustManager(tmf)
                .build();
        // Success is "did not throw". Before the fix this call produced
        // SSLException("failed to initialize the client-side SSL context")
        // caused by NullPointerException from wrapIfNeeded.
        assertNotNull(ctx);
    }

    private static final class NullReturningTrustManagerFactorySpi extends TrustManagerFactorySpi {
        // The Provider(String, double, String) constructor is deprecated since JDK 9 in
        // favor of (String, String, String), but the replacement is unavailable on the
        // JDK 8 source level this module targets, so we suppress the deprecation warning.
        @SuppressWarnings("deprecation")
        static final Provider PROVIDER = new Provider("NullReturningProvider", 1.0, "test-only") {
            private static final long serialVersionUID = 1L;
        };

        @Override
        protected void engineInit(KeyStore ks) {
            // no-op
        }

        @Override
        protected void engineInit(ManagerFactoryParameters spec) {
            // no-op
        }

        @Override
        protected TrustManager[] engineGetTrustManagers() {
            return null;
        }
    }
}
