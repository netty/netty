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
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link OpenSslKeyMaterialProvider} that will cache the
 * {@link OpenSslKeyMaterial} to reduce the overhead of parsing the chain
 * and the key for generation of the material.
 * <p>
 * All cache operations that access or evict entries perform retain/release
 * atomically via {@link ConcurrentHashMap}'s per-bucket locking.
 * When the cache is full on a cache miss, stale entries (whose alias is no
 * longer owned by the {@link X509KeyManager}) are evicted to make room
 * before inserting new material.
 */
final class OpenSslCachingKeyMaterialProvider extends OpenSslKeyMaterialProvider {

    /**
     * A cache that performs retain/release atomically with map operations,
     * using {@link ConcurrentHashMap}'s per-bucket locking to prevent
     * use-after-free races between concurrent reads and evictions.
     */
    private static final class KeyMaterialCache {

        /**
         * Backing store for cached key materials.
         */
        private final ConcurrentHashMap<String, OpenSslKeyMaterial> map =
                new ConcurrentHashMap<String, OpenSslKeyMaterial>();

        /**
         * Returns the material for the given alias with its reference count
         * incremented, or {@code null} if absent.
         *
         * @param alias the alias to look up
         * @return the retained material, or {@code null} if not cached
         */
        OpenSslKeyMaterial getAndRetain(final String alias) {
            final OpenSslKeyMaterial[] result = {null};
            map.computeIfPresent(alias, (final String k, final OpenSslKeyMaterial v) -> {
                v.retain();
                result[0] = v;
                return v;
            });
            return result[0];
        }

        /**
         * Inserts {@code material} if absent and returns it retained. If a
         * concurrent insert won, returns the existing entry retained instead;
         * the caller must then release {@code material}.
         *
         * @param alias    the alias to insert under
         * @param material the material to insert
         * @return the retained material now in the cache
         */
        OpenSslKeyMaterial putIfAbsentAndRetain(final String alias, final OpenSslKeyMaterial material) {
            final OpenSslKeyMaterial[] result = {null};
            map.compute(alias, (final String k, final OpenSslKeyMaterial existing) -> {
                if (existing != null) {
                    existing.retain();
                    result[0] = existing;
                    return existing;
                }
                // Retain for the caller; the map holds the original reference.
                material.retain();
                result[0] = material;
                return material;
            });
            return result[0];
        }

        /**
         * Removes and releases the entry for the given alias, if present.
         *
         * @param alias the alias whose entry should be removed and released
         */
        void removeAndRelease(final String alias) {
            map.computeIfPresent(alias, (final String k, final OpenSslKeyMaterial v) -> {
                v.release();
                return null;
            });
        }

        /**
         * Returns the number of entries in the cache.
         *
         * @return the cache size
         */
        int size() {
            return map.size();
        }

        /**
         * Returns {@code true} if the cache contains no entries.
         *
         * @return {@code true} if empty
         */
        boolean isEmpty() {
            return map.isEmpty();
        }

        /**
         * Returns a view of the aliases currently in the cache.
         *
         * @return the set of aliases
         */
        Iterable<String> aliases() {
            return map.keySet();
        }
    }

    /**
     * Maximum number of entries to hold in the cache.
     */
    private final int maxCachedEntries;

    /**
     * The key material cache.
     */
    private final KeyMaterialCache cache = new KeyMaterialCache();

    OpenSslCachingKeyMaterialProvider(final X509KeyManager keyManager, final String password, final int maxEntries) {
        super(keyManager, password);
        maxCachedEntries = maxEntries;
    }

    /**
     * Removes cached entries whose alias is no longer recognized by the key
     * manager. Evicting only when full avoids per-insert overhead, which is
     * acceptable for most cases where {@code maxCachedEntries} is reasonably
     * sized.
     */
    private void evictStaleEntries() {
        for (String alias : cache.aliases()) {
            if (keyManager().getCertificateChain(alias) == null) {
                cache.removeAndRelease(alias);
            }
        }
    }

    @Override
    OpenSslKeyMaterial chooseKeyMaterial(final ByteBufAllocator allocator, final String alias) throws Exception {
        OpenSslKeyMaterial material = cache.getAndRetain(alias);
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
            OpenSslKeyMaterial old = cache.putIfAbsentAndRetain(alias, material);
            if (old != material) {
                // Another operation inserted first; release our copy.
                material.release();
                material = old;
            }
        }
        return material;
    }

    int cacheSize() {
        return cache.size();
    }

    @Override
    void destroy() {
        // Remove and release all entries.
        do {
            for (String alias : cache.aliases()) {
                cache.removeAndRelease(alias);
            }
        } while (!cache.isEmpty());
    }
}
