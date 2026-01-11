/*
 * Copyright 2022 The Netty Project
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
package io.netty.handler.ssl.ocsp;

import io.netty.util.concurrent.Promise;
import org.bouncycastle.cert.ocsp.BasicOCSPResp;
import org.junit.jupiter.api.Test;

import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.util.concurrent.ExecutionException;

import static io.netty.handler.ssl.ocsp.OcspServerCertificateValidator.createDefaultResolver;
import static org.junit.jupiter.api.Assertions.assertNull;

class OcspClientTest {

    @Test
    void simpleOcspQueryTest() throws IOException, ExecutionException, InterruptedException {
        HttpsURLConnection httpsConnection = null;
        try {
            URL url = new URL("https://netty.io");
            httpsConnection = (HttpsURLConnection) url.openConnection();
            httpsConnection.connect();

            // Pull server certificates for validation
            X509Certificate[] certs = (X509Certificate[]) httpsConnection.getServerCertificates();
            X509Certificate serverCert = certs[0];
            X509Certificate certIssuer = certs[1];

            Promise<BasicOCSPResp> promise = OcspClient.query(serverCert, certIssuer, false,
                    IoTransport.DEFAULT, createDefaultResolver(IoTransport.DEFAULT));
            BasicOCSPResp basicOCSPResp = promise.get();

            // 'null' means certificate is valid
            assertNull(basicOCSPResp.getResponses()[0].getCertStatus());
        } finally {
            if (httpsConnection != null) {
                httpsConnection.disconnect();
            }
        }
    }
}
