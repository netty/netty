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
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Test;

import javax.net.ssl.KeyManagerFactory;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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

    @Override
    protected void assertRelease(OpenSslKeyMaterial material) {
        assertFalse(material.release());
    }

    @Test
    public void testMaterialCached() throws Exception {
        OpenSslKeyMaterialProvider provider = newMaterialProvider(newKeyManagerFactory(), PASSWORD);

        OpenSslKeyMaterial material = provider.chooseKeyMaterial(UnpooledByteBufAllocator.DEFAULT, EXISTING_ALIAS);
        assertNotNull(material);
        assertNotEquals(0, material.certificateChainAddress());
        assertNotEquals(0, material.privateKeyAddress());
        assertEquals(2, material.refCnt());

        OpenSslKeyMaterial material2 = provider.chooseKeyMaterial(UnpooledByteBufAllocator.DEFAULT, EXISTING_ALIAS);
        assertNotNull(material2);
        assertEquals(material.certificateChainAddress(), material2.certificateChainAddress());
        assertEquals(material.privateKeyAddress(), material2.privateKeyAddress());
        assertEquals(3, material.refCnt());
        assertEquals(3, material2.refCnt());

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
        assertThat(provider,
                CoreMatchers.<OpenSslKeyMaterialProvider>instanceOf(OpenSslCachingKeyMaterialProvider.class));
    }

    @Test
    public void testNotCacheForX509() throws Exception {
        OpenSslCachingX509KeyManagerFactory factory = new OpenSslCachingX509KeyManagerFactory(
                super.newKeyManagerFactory("PKIX"));
        OpenSslKeyMaterialProvider provider = factory.newProvider(PASSWORD);
        assertThat(provider, CoreMatchers.not(
                CoreMatchers.<OpenSslKeyMaterialProvider>instanceOf(OpenSslCachingKeyMaterialProvider.class)));
    }
}
