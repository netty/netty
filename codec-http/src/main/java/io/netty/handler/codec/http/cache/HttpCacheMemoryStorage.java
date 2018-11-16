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

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * TODO: Enforce config like maxCacheEntries
 */
public class HttpCacheMemoryStorage implements HttpCacheStorage {
    private final ConcurrentMap<String, HttpCacheEntry> cache;

    public HttpCacheMemoryStorage() {
        cache = new ConcurrentHashMap<String, HttpCacheEntry>();
    }

    @Override
    public Future<Void> put(final String key, final HttpCacheEntry entry, final Promise<Void> promise) {
        cache.put(key, entry);
        return promise.setSuccess(null);
    }

    @Override
    public Future<HttpCacheEntry> get(final String key, final Promise<HttpCacheEntry> promise) {
        return promise.setSuccess(cache.get(key));
    }

    @Override
    public Future<Void> remove(final String key, Promise<Void> promise) {
        final HttpCacheEntry cacheEntry = cache.remove(key);
        cacheEntry.getContent().release();
        promise.setSuccess(null);
        return promise;
    }
}
