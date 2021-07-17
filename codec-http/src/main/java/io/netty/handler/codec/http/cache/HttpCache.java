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
package io.netty.handler.codec.http.cache;

import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpStatusClass;
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

    HttpCache(HttpCacheStorage storage, EventExecutor executor, CacheKeyGenerator cacheKeyGenerator) {
        this(storage, cacheKeyGenerator, executor);
    }

    HttpCache(HttpCacheStorage storage, CacheKeyGenerator keyGenerator, EventExecutor executor) {
        this.storage = checkNotNull(storage, "storage");
        this.keyGenerator = checkNotNull(keyGenerator, "keyGenerator");
        this.executor = checkNotNull(executor, "executor");
    }

    private static boolean isMethodCacheable(HttpMethod method) {
        return method == HttpMethod.GET || method == HttpMethod.HEAD || method == HttpMethod.TRACE;
    }

    public Future<HttpCacheEntry> cache(HttpRequest request, FullHttpResponse response, Date requestSent,
                                        Date responseReceived) {

        String cacheKey = keyGenerator.generateKey(request);

        final HttpCacheEntry entry = new HttpCacheEntry(response.copy(), requestSent, responseReceived,
                                                        response.status(), response.headers());

        final Promise<HttpCacheEntry> promise = executor.newPromise();
        storage.put(cacheKey, entry, executor.<Void>newPromise())
               .addListener(new GenericFutureListener<Future<? super Void>>() {
                   @Override
                   public void operationComplete(Future<? super Void> future) {
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

    public Future<HttpCacheEntry> getCacheEntry(HttpRequest request, Promise<HttpCacheEntry> promise) {
        String cacheKey = keyGenerator.generateKey(request);
        return storage.get(cacheKey, promise);
    }

    public Future<Void> invalidate(HttpRequest request, final Promise<Void> promise) {
        logger.debug("Invalid cache entries");

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

    /**
     * @see <a href="https://tools.ietf.org/html/rfc7234#section-4.4">RFC 7234 - Invalidation</a>
     */
    public Future<Void> flushCacheEntriesInvalidatedByExchange(HttpRequest request, HttpResponse response,
                                                               Promise<Void> promise) {
        if (logger.isDebugEnabled()) {
            logger.debug("Flush cache entries invalidated by exchange: " +
                    request.headers().get(HttpHeaderNames.HOST) + "; " + request.method() + ' ' + request.uri() +
                    request.protocolVersion() + " -> " + response.protocolVersion() + ' ' + response.status());
        }

        if (!isMethodCacheable(request.method())) {

            final HttpResponseStatus status = response.status();
            if (status.codeClass() != HttpStatusClass.SUCCESS) {
                return promise.setSuccess(null);
            }

            // get request uri

            // TODO: flush content location uri if present

            // TODO: flush location uri if present
        }

        return promise;
    }

    public Future<Void> flushCacheEntriesFor(final HttpRequest request, Promise<Void> promise) {
        if (logger.isDebugEnabled()) {
            logger.debug("Flush cache entries: " + request.headers().get(HttpHeaderNames.HOST));
        }

        final HttpMethod method = request.method();
        if (!isMethodCacheable(method)) {
            return invalidate(request, promise);
        }

        return promise.setSuccess(null);
    }

    public Future<HttpCacheEntry> updateCacheEntry(final HttpRequest request,
                                                   final HttpResponse response,
                                                   final Date requestSent,
                                                   final Date responseReceived) {
        if (logger.isDebugEnabled()) {
            logger.debug("Update cache entry: " + request.headers().get(HttpHeaderNames.HOST));
        }

        final String cacheKey = keyGenerator.generateKey(request);

        // TODO cacheUpdateHandler.updateCacheEntry
        return getCacheEntry(request, executor.<HttpCacheEntry>newPromise());
    }
}
