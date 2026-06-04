/*
 * Copyright 2026 The Netty Project
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
package io.netty.handler.ssl.util;

import io.netty.util.internal.EmptyArrays;
import org.junit.jupiter.api.Test;

import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.KeyStore;
import java.security.cert.X509Certificate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class SimpleTrustManagerFactoryTest {

    @Test
    public void testNotWrap() {
        final X509TrustManager tm = new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
                // NOOP
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {
                // NOOP
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return EmptyArrays.EMPTY_X509_CERTIFICATES;
            }
        };
        SimpleTrustManagerFactory factory = new SimpleTrustManagerFactory() {
            @Override
            protected void engineInit(KeyStore keyStore) {
                // NOOP
            }

            @Override
            protected void engineInit(ManagerFactoryParameters managerFactoryParameters) {
                // NOOP
            }

            @Override
            protected TrustManager[] engineGetTrustManagers() {
                return new TrustManager[] { tm };
            }
        };

        TrustManager[] tms = factory.getTrustManagers();
        assertEquals(1, tms.length);
        assertSame(tm, tms[0]);
    }
}
