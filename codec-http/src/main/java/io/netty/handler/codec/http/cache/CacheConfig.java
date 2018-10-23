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
package io.netty.handler.codec.http.cache;

import io.netty.util.internal.UnstableApi;

@UnstableApi
public class CacheConfig {
    public static final CacheConfig DEFAULT = new Builder().build();

    private static final int DEFAULT_MAX_OBJECT_SIZE_IN_BYTES = 8192;
    private static final int DEFAULT_MAX_CACHE_ENTRIES = 1000;
    private static final boolean DEFAULT_SHARED_CACHED = true;
    private final long maxObjectSize;
    private final int maxCacheEntries;
    private final boolean sharedCache;

    CacheConfig(long maxObjectSize, int maxCacheEntries, boolean sharedCache) {
        this.maxObjectSize = maxObjectSize;
        this.maxCacheEntries = maxCacheEntries;
        this.sharedCache = sharedCache;
    }

    public static Builder custom() {
        return new Builder();
    }

    /**
     * Maximum response body size in bytes that will be cached.
     */
    public long getMaxObjectSize() {
        return maxObjectSize;
    }

    /**
     * Maximum number of entries in the cache
     */
    public int getMaxCacheEntries() {
        return maxCacheEntries;
    }

    /**
     * Should the cache behave as a shared cache?
     */
    public boolean isSharedCache() {
        return sharedCache;
    }

    public static class Builder {
        private long maxObjectSize;
        private int maxCacheEntries;
        private boolean sharedCache;

        Builder() {
            maxObjectSize = DEFAULT_MAX_OBJECT_SIZE_IN_BYTES;
            maxCacheEntries = DEFAULT_MAX_CACHE_ENTRIES;
            sharedCache = DEFAULT_SHARED_CACHED;
        }

        /**
         * Set maximum response body size in bytes that will be cached.
         */
        public Builder setMaxObjectSize(final long maxObjectSize) {
            this.maxObjectSize = maxObjectSize;
            return this;
        }

        /**
         * Set maximum number of entries in the cache
         */
        public Builder setMaxCacheEntries(final int maxCacheEntries) {
            this.maxCacheEntries = maxCacheEntries;
            return this;
        }

        /**
         * Set whether the cache should behave as a shared cache or not, true by default.
         */
        public Builder isSharedCache(final boolean sharedCache) {
            this.sharedCache = sharedCache;
            return this;
        }

        public CacheConfig build() {
            return new CacheConfig(maxObjectSize, maxCacheEntries, sharedCache);
        }
    }
}
