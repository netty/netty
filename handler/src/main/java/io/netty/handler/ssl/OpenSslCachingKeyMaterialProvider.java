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

import javax.net.ssl.X509KeyManager;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * {@link OpenSslKeyMaterialProvider} that will cache the {@link OpenSslKeyMaterial} to reduce the overhead
 * of parsing the chain and the key for generation of the material.
 *
 * When the cache is full on a cache miss, stale entries (whose alias is no longer owned by the
 * {@link X509KeyManager}) are evicted to make room before inserting new material.
 */
final class OpenSslCachingKeyMaterialProvider extends OpenSslKeyMaterialProvider {

    private final int maxCachedEntries;
    private final ConcurrentMap<String, OpenSslKeyMaterial> cache = new ConcurrentHashMap<String, OpenSslKeyMaterial>();

    OpenSslCachingKeyMaterialProvider(X509KeyManager keyManager, String password, int maxCachedEntries) {
        super(keyManager, password);
        this.maxCachedEntries = maxCachedEntries;
    }

    /**
     * Removes cached entries whose alias is no longer recognized by the key manager.
     * Called when the cache is full to reclaim memory from stale entries before giving up on caching.
     */
    private void evictStaleEntries() {
        Iterator<Map.Entry<String, OpenSslKeyMaterial>> iterator = cache.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, OpenSslKeyMaterial> entry = iterator.next();
            if (keyManager().getCertificateChain(entry.getKey()) == null) {
                iterator.remove();
                entry.getValue().release();
            }
        }
    }

    @Override
    OpenSslKeyMaterial chooseKeyMaterial(ByteBufAllocator allocator, String alias) throws Exception {
        OpenSslKeyMaterial material = cache.get(alias);
        if (material == null) {
            material = super.chooseKeyMaterial(allocator, alias);
            if (material == null) {
                // No keymaterial should be used.
                return null;
            }

            if (cache.size() >= maxCachedEntries) {
                // Cache is full; try to evict stale entries to make room.
                evictStaleEntries();
                if (cache.size() >= maxCachedEntries) {
                    // Still full after eviction, do not cache.
                    return material;
                }
            }
            OpenSslKeyMaterial old = cache.putIfAbsent(alias, material);
            if (old != null) {
                material.release();
                material = old;
            }
        }
        // We need to call retain() as we want to always have at least a refCnt() of 1 before destroy() was called.
        return material.retain();
    }

    int cacheSize() {
        return cache.size();
    }

    @Override
    void destroy() {
        // Remove and release all entries.
        do  {
            Iterator<OpenSslKeyMaterial> iterator = cache.values().iterator();
            while (iterator.hasNext()) {
                iterator.next().release();
                iterator.remove();
            }
        } while (!cache.isEmpty());
    }
}
