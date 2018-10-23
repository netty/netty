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
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.Date;

import static io.netty.util.internal.ObjectUtil.*;

class HttpCache {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(HttpCache.class);

    private final HttpCacheStorage storage;
    private final CacheKeyGenerator keyGenerator;
    private final EventExecutor executor;

    HttpCache(final HttpCacheStorage storage, final EventExecutor executor) {
        this(storage, new CacheKeyGenerator(), executor);
    }

    HttpCache(final HttpCacheStorage storage,
              final CacheKeyGenerator keyGenerator,
              final EventExecutor executor) {
        this.storage = checkNotNull(storage, "storage");
        this.keyGenerator = checkNotNull(keyGenerator, "keyGenerator");
        this.executor = checkNotNull(executor, "executor");
    }

    public Future<HttpCacheEntry> cache(final HttpRequest request,
                                final FullHttpResponse response,
                                final Date requestSent,
                                final Date responseReceived) {
        final String cacheKey = keyGenerator.generateKey(request);

        final HttpCacheEntry entry = new HttpCacheEntry(response.copy(), requestSent, responseReceived,
                                                        response.status(), response.headers());

        final Promise<HttpCacheEntry> promise = executor.newPromise();
        storage.put(cacheKey, entry, executor.<Void>newPromise())
               .addListener(new GenericFutureListener<Future<? super Void>>() {
                   @Override
                   public void operationComplete(Future<? super Void> future) throws Exception {
                       if (future.isSuccess()) {
                           promise.setSuccess(entry);
                       } else {
                           logger.error("Error putting entry in the cache", future.cause());
                           promise.setFailure(future.cause());
                       }
                   }
               });

        return promise;
    }

    public Future<HttpCacheEntry> getCacheEntry(final HttpRequest request, final Promise<HttpCacheEntry> promise) {
        final String cacheKey = keyGenerator.generateKey(request);
        return storage.get(cacheKey, promise);
    }

    public Future<Void> invalidate(final HttpRequest request, final Promise<Void> promise) {
        if (logger.isDebugEnabled()) {
            logger.debug("Invalid cache entries");
        }

        final String cacheKey = keyGenerator.generateKey(request);
        getCacheEntry(request, executor.<HttpCacheEntry>newPromise()).addListener(new FutureListener<HttpCacheEntry>() {
            @Override
            public void operationComplete(Future<HttpCacheEntry> future) throws Exception {
                if (future.isSuccess() && future.getNow() != null) {
                    // TODO: remove variants
                    storage.remove(cacheKey, executor.<Void>newPromise())
                           .addListener(new GenericFutureListener<Future<? super Void>>() {
                               @Override
                               public void operationComplete(Future<? super Void> future) throws Exception {
                                   promise.setSuccess(null);
                               }
                           });
                }
            }
        });

        return promise;
    }
}
