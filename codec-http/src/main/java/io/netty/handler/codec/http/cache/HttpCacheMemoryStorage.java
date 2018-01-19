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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class HttpCacheMemoryStorage implements HttpCacheStorage {
    private final ConcurrentMap<String, HttpCacheEntry> cache;

    public HttpCacheMemoryStorage() {
        cache = new ConcurrentHashMap<String, HttpCacheEntry>();
    }

    @Override
    public void put(final String key, final HttpCacheEntry entry) {
        cache.put(key, entry);
    }

    @Override
    public HttpCacheEntry get(final String key) {
        return cache.get(key);
    }

    @Override
    public void remove(final String key) {
        cache.remove(key);
    }
}
