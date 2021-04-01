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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.buffer.CompositeByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpMessage;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.internal.UnstableApi;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.Date;

import static io.netty.handler.codec.http.cache.HttpCacheEntryChecker.*;
import static io.netty.util.ReferenceCountUtil.*;
import static io.netty.util.internal.ObjectUtil.*;

/**
 * A {@link ChannelDuplexHandler} that provides http client caching capabilities.
 */
@UnstableApi
public class HttpClientCacheHandler extends ChannelDuplexHandler {
    public static final int MAX_COMPOSITEBUFFER_COMPONENTS = 1024;
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(HttpClientCacheHandler.class);
    private final RequestCachingPolicy requestCachingPolicy;
    private final HttpCache httpCache;
    private final ResponseCachingPolicy responseCachingPolicy;
    private final HttpCacheEntryChecker httpCacheEntryChecker;
    private final HttpResponseFromCacheGenerator httpResponseFromCacheGenerator;
    private final CacheConfig cacheConfig;
    private final ConditionalRequestBuilder conditionalRequestBuilder;

    private HttpRequest request;
    private AggregatedCacheFullHttpResponse inFlightResponse;
    private Date requestSent;

    private boolean waitingOnConditionalResponse;
    private HttpCacheEntry conditionResponseCacheEntry;

    public HttpClientCacheHandler(HttpCacheStorage cacheStorage,
                                  CacheConfig cacheConfig,
                                  EventLoop eventLoop,
                                  CacheKeyGenerator cacheKeyGenerator) {
        this.httpCache = new HttpCache(cacheStorage, cacheKeyGenerator, eventLoop);
        this.requestCachingPolicy = new RequestCachingPolicy();
        this.responseCachingPolicy = new ResponseCachingPolicy(cacheConfig.isSharedCache());
        this.httpCacheEntryChecker = new HttpCacheEntryChecker(cacheConfig.isSharedCache());
        this.httpResponseFromCacheGenerator = new HttpResponseFromCacheGenerator();
        this.conditionalRequestBuilder = new ConditionalRequestBuilder();
        this.cacheConfig = cacheConfig;
    }

    HttpClientCacheHandler(final RequestCachingPolicy requestCachingPolicy,
                           final HttpCache httpCache,
                           final ResponseCachingPolicy responseCachingPolicy,
                           final HttpCacheEntryChecker httpCacheEntryChecker,
                           final HttpResponseFromCacheGenerator httpResponseFromCacheGenerator,
                           final CacheConfig cacheConfig) {
        this.httpCache = checkNotNull(httpCache, "httpCache");
        this.requestCachingPolicy = checkNotNull(requestCachingPolicy, "requestCachingPolicy");
        this.responseCachingPolicy = checkNotNull(responseCachingPolicy, "responseCachingPolicy");
        this.httpCacheEntryChecker = checkNotNull(httpCacheEntryChecker, "httpCacheEntryChecker");
        this.httpResponseFromCacheGenerator =
                checkNotNull(httpResponseFromCacheGenerator, "httpResponseFromCacheGenerator");
        this.conditionalRequestBuilder = new ConditionalRequestBuilder();
        this.cacheConfig = checkNotNull(cacheConfig, "cacheConfig");
    }

    private static void appendPartialContent(CompositeByteBuf content, ByteBuf partialContent) {
        if (partialContent.isReadable()) {
            content.addComponent(true, partialContent.retain());
        }
    }

    private static void sendGatewayTimeout(final ChannelHandlerContext ctx, final HttpRequest request) {
        ctx.fireChannelRead(new DefaultFullHttpResponse(request.protocolVersion(), HttpResponseStatus.GATEWAY_TIMEOUT));
        release(request);
    }

    /**
     * @see <a href="https://tools.ietf.org/html/rfc7234#section-5.2.1.7">rfc7234#section-5.2.1.7</a>
     */
    private static boolean mayCallBackend(final CacheControlDirectives requestCacheControlDirectives) {
        if (requestCacheControlDirectives.onlyIfCached()) {
            logger.debug("Backend will not be call because request is using 'only-if-cached' header.");
            return false;
        }

        return true;
    }

    @Override
    public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise)
            throws Exception {
        if (!(msg instanceof HttpRequest) || waitingOnConditionalResponse) {
            super.write(ctx, msg, promise);
            return;
        }

        write(ctx, (HttpRequest) msg, promise);
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
        if (waitingOnConditionalResponse) {
            handleConditionalRequestResponse(ctx, msg);
        } else {
            handleBackendResponse(ctx, msg);
        }
    }

    private void handleConditionalRequestResponse(final ChannelHandlerContext ctx, final Object msg) throws Exception {
        if (msg instanceof HttpResponse) {
            final HttpResponse httpResponse = (HttpResponse) msg;

            if (revalidationResponseIsTooOld(httpResponse, conditionResponseCacheEntry)) {
                release(httpResponse);
                final HttpRequest unconditionalRequest = conditionalRequestBuilder.unconditionalRequest(null);

                ctx.pipeline().write(unconditionalRequest);
                return;
            }

            final HttpResponseStatus status = httpResponse.status();
            if (status == HttpResponseStatus.NOT_MODIFIED) {
                httpCache.updateCacheEntry(request, httpResponse, null, null)
                         .addListener(new GenericFutureListener<Future<HttpCacheEntry>>() {
                             @Override
                             public void operationComplete(Future<HttpCacheEntry> future) throws Exception {
                                 if (future.isSuccess()) {
                                     final HttpCacheEntry cacheEntry = future.getNow();

                                     HttpResponse httpResponse;
                                     if (isConditional(request) &&
                                         allConditionsMatch(request, cacheEntry, new Date())) {
                                         httpResponse =
                                                 httpResponseFromCacheGenerator.generateNotModifiedResponse(cacheEntry);
                                     } else {
                                         httpResponse = httpResponseFromCacheGenerator.generate(request, cacheEntry);
                                     }

                                     ctx.fireChannelRead(httpResponse);
                                 }
                             }
                         });
            } else if (serverError(httpResponse) &&
                       staleResponseAllowed(request, conditionResponseCacheEntry, new Date()) &&
                       mayReturnStaleIfError(request, conditionResponseCacheEntry, new Date())) {
                final FullHttpResponse staleResponse =
                        httpResponseFromCacheGenerator.generate(request, conditionResponseCacheEntry);

                // https://tools.ietf.org/html/rfc7234#section-5.5.1
                staleResponse.headers().add(HttpHeaderNames.WARNING, "110 - \"Response is Stale\"");

                release(httpResponse);
                ctx.fireChannelRead(staleResponse);
            } else {
                handleBackendResponse(ctx, msg);
            }
        }
    }

    private boolean mayReturnStaleIfError(final HttpRequest request, final HttpCacheEntry entry, final Date now) {
        final long stalenessInSeconds = entry.getStalenessInSeconds(cacheConfig.isSharedCache(), now);
        final CacheControlDirectives requestCacheControlDirectives = CacheControlDecoder.decode(request.headers());

        return stalenessInSeconds <= requestCacheControlDirectives.getStaleIfError() ||
               stalenessInSeconds <= entry.getCacheControlDirectives().getStaleIfError();
    }

    private boolean staleResponseAllowed(final HttpRequest request, final HttpCacheEntry entry, final Date now) {
        return !entry.mustRevalidate() &&
               !(cacheConfig.isSharedCache() && entry.proxyRevalidate()) &&
               !explicitFreshnessRequest(request, entry, now);
    }

    private boolean serverError(final HttpResponse response) {
        final HttpResponseStatus status = response.status();
        return status == HttpResponseStatus.INTERNAL_SERVER_ERROR ||
               status == HttpResponseStatus.BAD_GATEWAY ||
               status == HttpResponseStatus.SERVICE_UNAVAILABLE ||
               status == HttpResponseStatus.GATEWAY_TIMEOUT;
    }

    private boolean explicitFreshnessRequest(final HttpRequest request, final HttpCacheEntry entry, final Date now) {
        final CacheControlDirectives requestCacheControlDirectives = CacheControlDecoder.decode(request.headers());
        final int maxStale = requestCacheControlDirectives.getMaxStale();
        if (maxStale != -1) {
            final long currentAgeInSeconds = entry.getCurrentAgeInSeconds(now);
            final long freshnessLifetimeInSeconds = entry.getFreshnessLifetimeInSeconds(cacheConfig.isSharedCache());
            if (currentAgeInSeconds - freshnessLifetimeInSeconds > maxStale) {
                return true;
            }
        }

        return requestCacheControlDirectives.getMinFresh() != -1 ||
               requestCacheControlDirectives.getMaxAge() != -1;
    }

    /**
     * <a href="https://www.w3.org/Protocols/rfc2616/rfc2616-sec13.html#sec13.2.6">Disambiguating Multiple
     * Responses</a>
     */
    private boolean revalidationResponseIsTooOld(HttpResponse httpResponse, HttpCacheEntry httpCacheEntry) {
        final long responseDate = httpResponse.headers().getTimeMillis(HttpHeaderNames.DATE, 0L);
        final long cacheEntryDate = httpCacheEntry.getResponseHeaders().getTimeMillis(HttpHeaderNames.DATE, 0L);
        return responseDate < cacheEntryDate;
    }

    private void handleBackendResponse(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof HttpResponse) {
            final HttpResponse httpResponse = (HttpResponse) msg;

            httpCache.flushCacheEntriesInvalidatedByExchange(request, httpResponse, ctx.newPromise());
            if (responseCachingPolicy.canBeCached(request, httpResponse)) {
                logger.debug("Start caching response...");

                // TODO: storeRequestIfModifiedSinceFor304Response

                if (httpResponse instanceof FullHttpResponse) {
                    // Already have a full response, no need to wait for the other elements
                    tryCacheResponse((FullHttpResponse) httpResponse, ctx);
                } else {
                    // A streamed message - initialize the cumulative buffer, and wait for incoming chunks.
                    CompositeByteBuf content = ctx.alloc().compositeBuffer(MAX_COMPOSITEBUFFER_COMPONENTS);
                    if (msg instanceof ByteBufHolder) {
                        appendPartialContent(content, ((ByteBufHolder) httpResponse).content());
                    }

                    inFlightResponse = new AggregatedCacheFullHttpResponse(httpResponse, content.retain(), null);
                }
            } else {
                logger.debug("Response is not cacheable.");
                httpCache.flushCacheEntriesFor(request, ctx.newPromise());
            }
        } else if (msg instanceof HttpContent) {

            final HttpContent httpContent = (HttpContent) msg;
            if (inFlightResponse != null) {
                CompositeByteBuf content = inFlightResponse.content();
                appendPartialContent(content, httpContent.content());

                aggregate(inFlightResponse, httpContent);

                if (msg instanceof LastHttpContent) {
                    tryCacheResponse(inFlightResponse, ctx);
                }
            }
        }

        ctx.fireChannelRead(msg);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        try {
            // release current message if it is not null as it may be a left-over
            super.channelInactive(ctx);
        } finally {
            releaseInFlightResponse();
        }
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        try {
            super.handlerRemoved(ctx);
        } finally {
            // release current message if it is not null as it may be a left-over as there is not much more we can do in
            // this case
            releaseInFlightResponse();
        }
    }

    private void tryCacheResponse(final FullHttpResponse response, ChannelHandlerContext ctx) {
        ByteBuf content = response.content();
        if (content.readableBytes() > cacheConfig.getMaxObjectSize()) {
            logger.info("HTTP response too big to be cached.");
            return;
        }

        if (cacheConfig.shoulCheckFreshness()) {
            cacheIfFresh(response, ctx);
        } else {
            cache(response);
        }
    }

    private void cacheIfFresh(final FullHttpResponse response, ChannelHandlerContext ctx) {
        httpCache.getCacheEntry(request, ctx.executor().<HttpCacheEntry>newPromise())
                 .addListener(new GenericFutureListener<Future<HttpCacheEntry>>() {
                     @Override
                     public void operationComplete(Future<HttpCacheEntry> future) throws Exception {
                         if (future.isSuccess()) {

                             HttpCacheEntry existingCacheEntry = future.getNow();
                             if (existingCacheEntry != null) {
                                 final Long responseTimestamp = response.headers().getTimeMillis(HttpHeaderNames.DATE);
                                 final long cacheTimestamp = existingCacheEntry.getResponseDate().getTime();
                                 if (cacheTimestamp > responseTimestamp) {
                                     logger.debug("Cache already contains fresher cache entry");
                                     releaseInFlightResponse();
                                     return;
                                 }
                             }
                         }

                         cache(response);
                     }
                 });
    }

    private Future<HttpCacheEntry> cache(FullHttpResponse response) {
        return httpCache.cache(request, response, requestSent, new Date())
                        .addListener(new GenericFutureListener<Future<? super HttpCacheEntry>>() {
                            @Override
                            public void operationComplete(Future<? super HttpCacheEntry> future) throws Exception {
                                if (future.isSuccess()) {
                                    logger.debug("Response has been cached");
                                }

                                releaseInFlightResponse();
                            }
                        });
    }

    private void releaseInFlightResponse() {
        if (inFlightResponse != null) {
            inFlightResponse.release();
            inFlightResponse = null;
        }
    }

    protected void aggregate(FullHttpMessage aggregated, HttpContent content) throws Exception {
        if (content instanceof LastHttpContent) {
            // Merge trailing headers into the message.
            ((AggregatedCacheFullHttpResponse) aggregated)
                    .setTrailingHeaders(((LastHttpContent) content).trailingHeaders());
        }
    }

    private void write(final ChannelHandlerContext ctx, final HttpRequest request, final ChannelPromise promise)
            throws Exception {
        this.request = request;

        if (!requestCachingPolicy.canBeServedFromCache(request)) {
            logger.debug("Request can not be served from cache.");
            httpCache.invalidate(request, ctx.executor().<Void>newPromise());

            callBackend(ctx, request, promise);
            return;
        }

        final CacheControlDirectives requestCacheControlDirectives = CacheControlDecoder.decode(request.headers());
        httpCache.getCacheEntry(request, ctx.executor().<HttpCacheEntry>newPromise())
                 .addListener(new FutureListener<HttpCacheEntry>() {
                     @Override
                     public void operationComplete(Future<HttpCacheEntry> cacheEntryFuture) throws Exception {
                         if (cacheEntryFuture.isSuccess() && cacheEntryFuture.getNow() != null) {
                             handleCacheHit(ctx, request, requestCacheControlDirectives, cacheEntryFuture.getNow(),
                                            promise);
                         } else {
                             handleCacheMiss(ctx, request, requestCacheControlDirectives, promise);
                         }
                     }
                 });
    }

    private void handleCacheHit(final ChannelHandlerContext ctx, final HttpRequest request,
                                final CacheControlDirectives requestCacheControlDirectives,
                                final HttpCacheEntry cacheEntry, final ChannelPromise promise) throws Exception {

        // TODO record cache hit

        final Date now = new Date();
        if (httpCacheEntryChecker.canUseCachedResponse(request, requestCacheControlDirectives, cacheEntry, now)) {
            logger.debug("Cache hit");

            final FullHttpResponse httpResponse = httpResponseFromCacheGenerator.generate(request, cacheEntry);
            release(request);
            ctx.fireChannelRead(httpResponse);
        } else if (!mayCallBackend(requestCacheControlDirectives)) {
            logger.debug("Cache entry not suitable but only-if-cached requested");
            sendGatewayTimeout(ctx, request);
        } else if (cacheEntry.getStatus() != HttpResponseStatus.NOT_MODIFIED || isConditional(request)) {
            logger.debug("Revalidating cache entry");

            revalidateCacheEntry(ctx, request, cacheEntry, promise);
        } else {
            logger.debug("Cache entry non existent or not usable, calling backend.");
            callBackend(ctx, request, promise);
        }
    }

    /**
     * @see <a href="https://tools.ietf.org/html/rfc7234#section-4.3">RFC 7234 - Validation</a>
     */
    private void revalidateCacheEntry(final ChannelHandlerContext ctx,
                                      final HttpRequest request,
                                      final HttpCacheEntry cacheEntry,
                                      final ChannelPromise promise) throws Exception {

        waitingOnConditionalResponse = true;
        conditionResponseCacheEntry = cacheEntry;
        final HttpRequest conditionalRequest = conditionalRequestBuilder.conditionalRequest(request, cacheEntry);

        ctx.pipeline().write(conditionalRequest, promise);
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

    private void callBackend(final ChannelHandlerContext ctx, final HttpRequest request, final ChannelPromise promise)
            throws Exception {
        requestSent = new Date();
        super.write(ctx, request, promise);
    }

}
