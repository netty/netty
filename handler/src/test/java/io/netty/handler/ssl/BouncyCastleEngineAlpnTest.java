/*
 * Copyright 2025 The Netty Project
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

import io.netty.handler.ssl.util.BouncyCastleUtil;
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.security.Provider;
import java.security.SecureRandom;
import java.util.List;
import java.util.function.BiFunction;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BouncyCastleEngineAlpnTest {

    @Test
    public void testBouncyCastleSSLEngineSupportsAlpn() throws Exception {
        Provider bouncyCastleProvider = new BouncyCastleJsseProvider();
        SSLContext context = SslUtils.getSSLContext(bouncyCastleProvider, new SecureRandom());
        SSLEngine engine = context.createSSLEngine();
        assertTrue(BouncyCastleUtil.isBcJsseInUse(engine));
        assertTrue(BouncyCastleAlpnSslUtils.isAlpnSupported());

        BouncyCastleAlpnSslEngine alpnSslEngine = new BouncyCastleAlpnSslEngine(
                engine, new JdkAlpnApplicationProtocolNegotiator("fake"), true);

        // Call methods to ensure these not throw.
        alpnSslEngine.setHandshakeApplicationProtocolSelector(new BiFunction<SSLEngine, List<String>, String>() {
            @Override
            public String apply(SSLEngine sslEngine, List<String> strings) {
                return "fake";
            }
        });
        // Check that none of the methods will throw.
        alpnSslEngine.getHandshakeApplicationProtocolSelector();
        alpnSslEngine.setNegotiatedApplicationProtocol("fake");
        alpnSslEngine.getNegotiatedApplicationProtocol();
    }
}
