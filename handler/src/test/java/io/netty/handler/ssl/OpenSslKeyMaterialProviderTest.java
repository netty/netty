/*
 * Copyright 2018 The Netty Project
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

import io.netty.buffer.UnpooledByteBufAllocator;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.net.ssl.KeyManagerFactory;

import java.security.KeyStore;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

public class OpenSslKeyMaterialProviderTest {

    static final String PASSWORD = "example";
    static final String EXISTING_ALIAS = "1";
    private static final String NON_EXISTING_ALIAS = "nonexisting";

    @BeforeClass
    public static void checkOpenSsl() {
        assumeTrue(OpenSsl.isAvailable());
    }

    protected KeyManagerFactory newKeyManagerFactory() throws Exception {
        char[] password = PASSWORD.toCharArray();
        final KeyStore keystore = KeyStore.getInstance("PKCS12");
        keystore.load(getClass().getResourceAsStream("mutual_auth_server.p12"), password);

        KeyManagerFactory kmf =
                KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keystore, password);
        return kmf;
    }

    protected OpenSslKeyMaterialProvider newMaterialProvider(KeyManagerFactory factory, String password) {
        return new OpenSslKeyMaterialProvider(ReferenceCountedOpenSslContext.chooseX509KeyManager(
                factory.getKeyManagers()), password);
    }

    protected void assertRelease(OpenSslKeyMaterial material) {
        assertTrue(material.release());
    }

    @Test
    public void testChooseKeyMaterial() throws Exception {
        OpenSslKeyMaterialProvider provider = newMaterialProvider(newKeyManagerFactory(), PASSWORD);
        OpenSslKeyMaterial nonExistingMaterial = provider.chooseKeyMaterial(
                UnpooledByteBufAllocator.DEFAULT, NON_EXISTING_ALIAS);
        assertNull(nonExistingMaterial);

        OpenSslKeyMaterial material = provider.chooseKeyMaterial(UnpooledByteBufAllocator.DEFAULT, EXISTING_ALIAS);
        assertNotNull(material);
        assertNotEquals(0, material.certificateChainAddress());
        assertNotEquals(0, material.privateKeyAddress());
        assertRelease(material);

        provider.destroy();
    }
}
