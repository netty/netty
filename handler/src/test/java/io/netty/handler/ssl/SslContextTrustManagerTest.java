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

import static org.junit.Assert.fail;
import static org.junit.Assert.assertNotNull;

import java.io.InputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.junit.Test;

public class SslContextTrustManagerTest {
    @Test
    public void testUsingAllCAs() throws Exception {
        runTests(new String[] { "tm_test_ca_1a.pem", "tm_test_ca_1b.pem",
                "tm_test_ca_2.pem" }, new String[] { "tm_test_eec_1.pem",
                "tm_test_eec_2.pem", "tm_test_eec_3.pem" }, new boolean[] {
                true, true, true });
    }

    @Test
    public void testUsingAllCAsWithDuplicates() throws Exception {
        runTests(new String[] { "tm_test_ca_1a.pem", "tm_test_ca_1b.pem",
                "tm_test_ca_2.pem", "tm_test_ca_2.pem" },
                new String[] { "tm_test_eec_1.pem", "tm_test_eec_2.pem",
                        "tm_test_eec_3.pem" },
                new boolean[] { true, true, true });
    }

    @Test
    public void testUsingCAsOneAandB() throws Exception {
        runTests(new String[] { "tm_test_ca_1a.pem", "tm_test_ca_1b.pem", },
                new String[] { "tm_test_eec_1.pem", "tm_test_eec_2.pem",
                        "tm_test_eec_3.pem" }, new boolean[] { true, true,
                        false });
    }

    @Test
    public void testUsingCAsOneAandTwo() throws Exception {
        runTests(new String[] { "tm_test_ca_1a.pem", "tm_test_ca_2.pem" },
                new String[] { "tm_test_eec_1.pem", "tm_test_eec_2.pem",
                        "tm_test_eec_3.pem" }, new boolean[] { true, false,
                        true });
    }

    /**
     *
     * @param caResources
     *            an array of paths to CA Certificates in PEM format to load
     *            from the classpath (relative to this class).
     * @param eecResources
     *            an array of paths to Server Certificates in PEM format in to
     *            load from the classpath (relative to this class).
     * @param expectations
     *            an array of expecting results for each EEC Server Certificate
     *            (the array is expected to have the same length the previous
     *            argument, and be arrange in matching order: true means
     *            expected to be valid, false otherwise.
     */
    private static void runTests(String[] caResources, String[] eecResources,
            boolean[] expectations) throws Exception {
        X509TrustManager tm = getTrustManager(caResources);

        X509Certificate[] eecCerts = loadCertCollection(eecResources);

        for (int i = 0; i < eecResources.length; i++) {
            X509Certificate eecCert = eecCerts[i];
            assertNotNull("Cannot use cert " + eecResources[i], eecCert);
            try {
                tm.checkServerTrusted(new X509Certificate[] { eecCert }, "RSA");
                if (!expectations[i]) {
                    fail(String.format(
                            "Certificate %s was expected not to be valid when using CAs %s, but its "
                                    + "verification passed.", eecResources[i],
                            Arrays.asList(caResources)));
                }
            } catch (CertificateException e) {
                if (expectations[i]) {
                    fail(String.format(
                            "Certificate %s was expected to be valid when using CAs %s, but its "
                                    + "verification failed.", eecResources[i],
                            Arrays.asList(caResources)));
                }
            }
        }
    }

    private static X509TrustManager getTrustManager(String[] resourceNames)
            throws Exception {
        X509Certificate[] certCollection = loadCertCollection(resourceNames);
        TrustManagerFactory tmf = SslContext.buildTrustManagerFactory(
                certCollection, null);

        for (TrustManager tm : tmf.getTrustManagers()) {
            if (tm instanceof X509TrustManager) {
                return (X509TrustManager) tm;
            }
        }

        throw new Exception(
                "Unable to find any X509TrustManager from this factory.");
    }

    private static X509Certificate[] loadCertCollection(String[] resourceNames)
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
