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

import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.util.internal.EmptyArrays;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLException;
import javax.net.ssl.X509ExtendedKeyManager;
import java.net.Socket;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

import static org.junit.jupiter.api.Assertions.fail;

public class OpenSslKeyMaterialManagerTest {

    @Test
    public void testChooseClientAliasReturnsNull() throws SSLException {
        OpenSsl.ensureAvailability();

        X509ExtendedKeyManager keyManager = new X509ExtendedKeyManager() {
            @Override
            public String[] getClientAliases(String s, Principal[] principals) {
                return EmptyArrays.EMPTY_STRINGS;
            }

            @Override
            public String chooseClientAlias(String[] strings, Principal[] principals, Socket socket) {
                return null;
            }

            @Override
            public String[] getServerAliases(String s, Principal[] principals) {
                return EmptyArrays.EMPTY_STRINGS;
            }

            @Override
            public String chooseServerAlias(String s, Principal[] principals, Socket socket) {
                return null;
            }

            @Override
            public X509Certificate[] getCertificateChain(String s) {
                return EmptyArrays.EMPTY_X509_CERTIFICATES;
            }

            @Override
            public PrivateKey getPrivateKey(String s) {
                return null;
            }
        };

        OpenSslKeyMaterialManager manager = new OpenSslKeyMaterialManager(
                new OpenSslKeyMaterialProvider(keyManager, null) {
            @Override
            OpenSslKeyMaterial chooseKeyMaterial(ByteBufAllocator allocator, String alias) throws Exception {
                fail("Should not be called when alias is null");
                return null;
            }
        }, false);
        SslContext context = SslContextBuilder.forClient().sslProvider(SslProvider.OPENSSL).build();
        OpenSslEngine engine =
                (OpenSslEngine) context.newEngine(UnpooledByteBufAllocator.DEFAULT);
        manager.setKeyMaterialClientSide(engine, EmptyArrays.EMPTY_STRINGS, null);
    }
}
