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

import io.netty.handler.ssl.util.SelfSignedCertificate;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.TrustManagerFactorySpi;
import java.io.File;
import java.security.KeyStore;
import java.security.Provider;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class JdkSslServerContextTest extends SslContextTest {

    @Override
    protected SslContext newSslContext(File crtFile, File keyFile, String pass) throws SSLException {
        return new JdkSslServerContext(crtFile, keyFile, pass);
    }

    @Test
    void testWrappingOfTrustManager() {
        Assertions.assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                JdkSslServerContext.checkIfWrappingTrustManagerIsSupported();
            }
        });
    }

    // A TrustManagerFactory whose getTrustManagers() legitimately returns null
    // (e.g., asking SSLContext.init to fall back to the JDK default trust store)
    // previously NPE'd inside wrapTrustManagerIfNeeded. Verify the server context
    // now builds without throwing.
    @Test
    void testTrustManagerFactoryReturningNullDoesNotThrowNpe() throws Exception {
        SelfSignedCertificate ssc = new SelfSignedCertificate();
        try {
            TrustManagerFactory tmf = new TrustManagerFactory(
                    new NullReturningTrustManagerFactorySpi(),
                    NullReturningTrustManagerFactorySpi.PROVIDER, "null") {
            };
            // TrustManagerFactory must be initialized before SslContextBuilder will accept it;
            // without this call the builder throws before reaching the code path under test.
            tmf.init((KeyStore) null);

            SslContext ctx = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey())
                    .sslProvider(SslProvider.JDK)
                    .trustManager(tmf)
                    .build();
            // Success is "did not throw". Before the fix this call produced
            // SSLException("failed to initialize the server-side SSL context")
            // caused by NullPointerException from wrapTrustManagerIfNeeded.
            assertNotNull(ctx);
        } finally {
            ssc.delete();
        }
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
