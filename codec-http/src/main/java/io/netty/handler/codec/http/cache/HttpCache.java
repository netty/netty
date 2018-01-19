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

import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.Date;

import static io.netty.util.internal.ObjectUtil.*;

public class HttpCache {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(HttpCache.class);

    private final HttpCacheStorage storage;
    private final CacheKeyGenerator keyGenerator;

    public HttpCache(final HttpCacheStorage storage) {
        this(storage, new CacheKeyGenerator());
    }

    public HttpCache(final HttpCacheStorage storage, final CacheKeyGenerator keyGenerator) {
        this.storage = checkNotNull(storage, "storage");
        this.keyGenerator = checkNotNull(keyGenerator, "keyGenerator");
    }

    public HttpCacheEntry cache(final HttpRequest request,
                                final FullHttpResponse response,
                                final Date requestSent,
                                final Date responseReceived) {
        final String cacheKey = keyGenerator.generateKey(request);

        final HttpCacheEntry entry = new HttpCacheEntry(response.copy(), requestSent, responseReceived,
                                                        response.status(), response.headers());

        storage.put(cacheKey, entry);
        return entry;
    }

    public HttpCacheEntry getCacheEntry(HttpRequest request) {
        final HttpCacheEntry cacheEntry;
        try {
            final String cacheKey = keyGenerator.generateKey(request);
            cacheEntry = storage.get(cacheKey);
        } catch (Exception e) {
            logger.error("Error while retrieving cache entry.", e);
            return null;
        }

        return cacheEntry;
    }

    public void invalidate(final HttpRequest request) {
        if (logger.isDebugEnabled()) {
            logger.debug("Invalid cache entries");
        }

        final String cacheKey = keyGenerator.generateKey(request);
        final HttpCacheEntry cacheEntry = getCacheEntry(request);
        if (cacheEntry != null) {

            // TODO: remove variants

            storage.remove(cacheKey);
        }
    }
}
