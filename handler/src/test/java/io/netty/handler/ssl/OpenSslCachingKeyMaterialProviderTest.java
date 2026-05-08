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

import io.netty.buffer.UnpooledByteBufAllocator;
import org.junit.jupiter.api.Test;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.X509KeyManager;
import java.net.Socket;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class OpenSslCachingKeyMaterialProviderTest extends OpenSslKeyMaterialProviderTest {

    @Override
    protected KeyManagerFactory newKeyManagerFactory() throws Exception {
        return new OpenSslCachingX509KeyManagerFactory(super.newKeyManagerFactory());
    }

    @Override
    protected OpenSslKeyMaterialProvider newMaterialProvider(KeyManagerFactory factory, String password) {
        return new OpenSslCachingKeyMaterialProvider(ReferenceCountedOpenSslContext.chooseX509KeyManager(
                factory.getKeyManagers()), password, Integer.MAX_VALUE);
    }

    @Test
    public void testMaterialCached() throws Exception {
        OpenSslKeyMaterialProvider provider = newMaterialProvider(newKeyManagerFactory(), PASSWORD);

        OpenSslKeyMaterial material = provider.chooseKeyMaterial(UnpooledByteBufAllocator.DEFAULT, EXISTING_ALIAS);
        assertNotNull(material);
        assertNotEquals(0, material.certificateChainAddress());
        assertNotEquals(0, material.privateKeyAddress());
        assertEquals(3, material.refCnt());

        OpenSslKeyMaterial material2 = provider.chooseKeyMaterial(UnpooledByteBufAllocator.DEFAULT, EXISTING_ALIAS);
        assertNotNull(material2);
        assertEquals(material.certificateChainAddress(), material2.certificateChainAddress());
        assertEquals(material.privateKeyAddress(), material2.privateKeyAddress());
        assertEquals(4, material.refCnt());
        assertEquals(4, material2.refCnt());

        assertFalse(material.release());
        assertFalse(material2.release());

        // After this the material should have been released.
        provider.destroy();

        assertEquals(0, material.refCnt());
        assertEquals(0, material2.refCnt());
    }

    @Test
    public void testCacheForSunX509() throws Exception {
        OpenSslCachingX509KeyManagerFactory factory = new OpenSslCachingX509KeyManagerFactory(
                super.newKeyManagerFactory("SunX509"));
        OpenSslKeyMaterialProvider provider = factory.newProvider(PASSWORD);
        assertInstanceOf(OpenSslCachingKeyMaterialProvider.class, provider);
    }

    @Test
    public void testNotCacheForX509() throws Exception {
        OpenSslCachingX509KeyManagerFactory factory = new OpenSslCachingX509KeyManagerFactory(
                super.newKeyManagerFactory("PKIX"));
        OpenSslKeyMaterialProvider provider = factory.newProvider(PASSWORD);
        assertThat(provider).isNotInstanceOf(OpenSslCachingKeyMaterialProvider.class);
    }

    @Test
    public void testStaleEntriesEvictedWhenCacheFull() throws Exception {
        final X509KeyManager delegate = ReferenceCountedOpenSslContext.chooseX509KeyManager(
                newKeyManagerFactory().getKeyManagers());

        class RotatableKeyManager implements X509KeyManager {
            private String currentAlias = "old-key";

            void rotate() {
                currentAlias = "new-key";
            }

            @Override
            public X509Certificate[] getCertificateChain(String alias) {
                return currentAlias.equals(alias) ? delegate.getCertificateChain(EXISTING_ALIAS) : null;
            }
            @Override
            public PrivateKey getPrivateKey(String alias) {
                return currentAlias.equals(alias) ? delegate.getPrivateKey(EXISTING_ALIAS) : null;
            }
            @Override
            public String[] getClientAliases(String keyType, Principal[] issuers) {
                return delegate.getClientAliases(keyType, issuers);
            }
            @Override
            public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket) {
                return delegate.chooseClientAlias(keyType, issuers, socket);
            }
            @Override
            public String[] getServerAliases(String keyType, Principal[] issuers) {
                return delegate.getServerAliases(keyType, issuers);
            }
            @Override
            public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
                return delegate.chooseServerAlias(keyType, issuers, socket);
            }
        }

        RotatableKeyManager rotatableKeyManager = new RotatableKeyManager();
        OpenSslCachingKeyMaterialProvider provider =
                new OpenSslCachingKeyMaterialProvider(rotatableKeyManager, PASSWORD, 1);

        // Populate the cache with the old alias.
        OpenSslKeyMaterial material = provider.chooseKeyMaterial(UnpooledByteBufAllocator.DEFAULT, "old-key");
        assertNotNull(material);
        assertEquals(1, provider.cacheSize());
        material.release();

        // Simulate cert rotation: old alias is gone, new alias takes over.
        rotatableKeyManager.rotate();

        // Cache is full; loading "new-key" triggers evictStaleEntries(), which removes "old-key".
        OpenSslKeyMaterial newMaterial = provider.chooseKeyMaterial(UnpooledByteBufAllocator.DEFAULT, "new-key");
        assertNotNull(newMaterial);
        assertEquals(1, provider.cacheSize()); // old evicted, new-key inserted
        newMaterial.release();

        provider.destroy();
    }
}
