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

import org.conscrypt.OpenSSLProvider;

import javax.net.ssl.SNIMatcher;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import java.io.InputStream;
import java.security.Provider;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertNotNull;

public final class Java8SslTestUtils {

    private Java8SslTestUtils() { }

    static void setSNIMatcher(SSLParameters parameters, final byte[] match) {
        SNIMatcher matcher = new SNIMatcher(0) {
            @Override
            public boolean matches(SNIServerName sniServerName) {
                return Arrays.equals(match, sniServerName.getEncoded());
            }
        };
        parameters.setSNIMatchers(Collections.singleton(matcher));
    }

    static Provider conscryptProvider() {
        return new OpenSSLProvider();
    }

    /**
     * Wraps the given {@link SSLEngine} to add extra tests while executing methods if possible / needed.
     */
    static SSLEngine wrapSSLEngineForTesting(SSLEngine engine) {
        if (engine instanceof ReferenceCountedOpenSslEngine) {
            return new OpenSslErrorStackAssertSSLEngine((ReferenceCountedOpenSslEngine) engine);
        }
        return engine;
    }

    public static X509Certificate[] loadCertCollection(String... resourceNames)
            throws Exception {
        CertificateFactory certFactory = CertificateFactory
                .getInstance("X.509");

        X509Certificate[] certCollection = new X509Certificate[resourceNames.length];
        for (int i = 0; i < resourceNames.length; i++) {
            String resourceName = resourceNames[i];
            InputStream is = null;
            try {
                is = SslContextTest.class.getResourceAsStream(resourceName);
                assertNotNull("Cannot find " + resourceName, is);
                certCollection[i] = (X509Certificate) certFactory
                        .generateCertificate(is);
            } finally {
                if (is != null) {
                    is.close();
                }
            }
        }
        return certCollection;
    }
}
