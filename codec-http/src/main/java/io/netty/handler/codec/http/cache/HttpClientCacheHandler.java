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

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.Date;

import static io.netty.util.ReferenceCountUtil.*;
import static io.netty.util.internal.ObjectUtil.*;

/**
 *
 */
public class HttpClientCacheHandler extends ChannelDuplexHandler {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(HttpClientCacheHandler.class);

    private final RequestCachingPolicy requestCachingPolicy;
    private final HttpCache httpCache;
    private final ResponseCachingPolicy responseCachingPolicy;
    private final HttpCacheEntryChecker httpCacheEntryChecker;
    private final HttpResponseFromCacheGenerator httpResponseFromCacheGenerator;

    private HttpRequest request;
    private Date requestSent;

    public HttpClientCacheHandler(final HttpCacheStorage cacheStorage, final boolean sharedCache) {
        this.httpCache = new HttpCache(cacheStorage, new CacheKeyGenerator());
        this.requestCachingPolicy = new RequestCachingPolicy();
        this.responseCachingPolicy = new ResponseCachingPolicy(sharedCache);
        this.httpCacheEntryChecker = new HttpCacheEntryChecker(sharedCache);
        this.httpResponseFromCacheGenerator = new HttpResponseFromCacheGenerator();
    }

    public HttpClientCacheHandler(final RequestCachingPolicy requestCachingPolicy,
                                  final HttpCache httpCache,
                                  final ResponseCachingPolicy responseCachingPolicy,
                                  final HttpCacheEntryChecker httpCacheEntryChecker,
                                  final HttpResponseFromCacheGenerator httpResponseFromCacheGenerator) {
        this.httpCache = checkNotNull(httpCache, "httpCache");
        this.requestCachingPolicy = checkNotNull(requestCachingPolicy, "requestCachingPolicy");
        this.responseCachingPolicy = checkNotNull(responseCachingPolicy, "responseCachingPolicy");
        this.httpCacheEntryChecker = checkNotNull(httpCacheEntryChecker, "httpCacheEntryChecker");
        this.httpResponseFromCacheGenerator =
                checkNotNull(httpResponseFromCacheGenerator, "httpResponseFromCacheGenerator");
    }

    @Override
    public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise)
            throws Exception {
        if (!(msg instanceof HttpRequest)) {
            super.write(ctx, msg, promise);
            return;
        }

        write(ctx, (HttpRequest) msg, promise);
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
        if (!(msg instanceof FullHttpResponse)) {
            super.channelRead(ctx, msg);
            return;
        }

        channelRead(ctx, (FullHttpResponse) msg);
    }

    private void write(final ChannelHandlerContext ctx, final HttpRequest request, final ChannelPromise promise)
            throws Exception {
        this.request = request;

        if (!requestCachingPolicy.canBeServedFromCache(request)) {
            logger.debug("Request can not be served from cache.");
            httpCache.invalidate(request);

            callBackend(ctx, request, promise);
            return;
        }

        final CacheControlDirectives requestCacheControlDirectives = CacheControlDecoder.decode(request.headers());
        final HttpCacheEntry cacheEntry = httpCache.getCacheEntry(request);
        if (cacheEntry == null) {
            handleCacheMiss(ctx, request, requestCacheControlDirectives, promise);
        } else {
            handleCacheHit(ctx, request, requestCacheControlDirectives, cacheEntry, promise);
        }
    }

    private void handleCacheHit(final ChannelHandlerContext ctx, final HttpRequest request,
                                final CacheControlDirectives requestCacheControlDirectives,
                                final HttpCacheEntry cacheEntry, final ChannelPromise promise) throws Exception {

        final Date now = new Date();
        if (httpCacheEntryChecker.canUseCachedResponse(requestCacheControlDirectives, cacheEntry, now)) {
            logger.debug("Cache hit");

            final FullHttpResponse httpResponse = httpResponseFromCacheGenerator.generate(request, cacheEntry);
            release(request);
            ctx.fireChannelRead(httpResponse);
        } else if (!mayCallBackend(requestCacheControlDirectives)) {
            logger.debug("Cache entry not suitable but only-if-cached requested");
            sendGatewayTimeout(ctx, request);
        } else {
            callBackend(ctx, request, promise);
        }
    }

    private void sendGatewayTimeout(final ChannelHandlerContext ctx, final HttpRequest request) {
        ctx.fireChannelRead(new DefaultFullHttpResponse(request.protocolVersion(), HttpResponseStatus.GATEWAY_TIMEOUT));
        release(request);
    }

    private void handleCacheMiss(final ChannelHandlerContext ctx, final HttpRequest request,
                                 final CacheControlDirectives requestCacheControlDirectives,
                                 final ChannelPromise promise) throws Exception {
        logger.debug("Cache miss");
        if (!mayCallBackend(requestCacheControlDirectives)) {
            sendGatewayTimeout(ctx, request);
            return;
        }

        callBackend(ctx, request, promise);
    }

    /**
     * @see <a href="https://tools.ietf.org/html/rfc7234#section-5.2.1.7">rfc7234#section-5.2.1.7</a>
     */
    private boolean mayCallBackend(final CacheControlDirectives requestCacheControlDirectives) {
        if (requestCacheControlDirectives.onlyIfCached()) {
            logger.debug("Backend will not be call because request is using 'only-if-cached' header.");
            return false;
        }

        return true;
    }

    private void callBackend(final ChannelHandlerContext ctx, final HttpRequest request, final ChannelPromise promise)
            throws Exception {
        requestSent = new Date();
        super.write(ctx, request, promise);
    }

    private void channelRead(final ChannelHandlerContext ctx, final FullHttpResponse response) throws Exception {
        if (responseCachingPolicy.canBeCached(request, response)) {
            logger.debug("Caching response");
            HttpCacheEntry cacheEntry = httpCache.cache(request, response, requestSent, new Date());
        }

        super.channelRead(ctx, response);
    }

}
