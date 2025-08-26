/*
 * Copyright 2021 The Netty Project
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
package io.netty.handler.codec.quic;

import io.netty.handler.ssl.SslContext;
import io.netty.util.internal.EmptyArrays;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.X509ExtendedKeyManager;

import java.net.Socket;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class QuicSslContextTest {

    @Test
    public void testSessionContextSettingsForClient() {
        testSessionContextSettings(QuicSslContextBuilder.forClient(), 20, 50);
    }

    @Test
    public void testSessionContextSettingsForServer() {
        testSessionContextSettings(QuicSslContextBuilder.forServer(new X509ExtendedKeyManager() {
            @Override
            public String[] getClientAliases(String keyType, Principal[] issuers) {
                return EmptyArrays.EMPTY_STRINGS;
            }

            @Override
            @Nullable
            public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket) {
                return null;
            }

            @Override
            public String[] getServerAliases(String keyType, Principal[] issuers) {
                return EmptyArrays.EMPTY_STRINGS;
            }

            @Override
            @Nullable
            public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
                return null;
            }

            @Override
            public X509Certificate[] getCertificateChain(String alias) {
                return new X509Certificate[0];
            }

            @Override
            @Nullable
            public PrivateKey getPrivateKey(String alias) {
                return null;
            }
        }, null), 20, 50);
    }

    private void testSessionContextSettings(QuicSslContextBuilder builder, int size, int timeout) {
        SslContext context = builder.sessionCacheSize(size).sessionTimeout(timeout).build();
        assertEquals(size, context.sessionCacheSize());
        assertEquals(timeout, context.sessionTimeout());
        SSLSessionContext sessionContext = context.sessionContext();
        assertEquals(size, sessionContext.getSessionCacheSize());
        assertEquals(timeout, sessionContext.getSessionTimeout());

        int newSize = size / 2;
        sessionContext.setSessionCacheSize(newSize);
        assertEquals(newSize, context.sessionCacheSize());

        int newTimeout = timeout / 2;
        sessionContext.setSessionTimeout(newTimeout);
        assertEquals(newTimeout, sessionContext.getSessionTimeout());
    }
}
