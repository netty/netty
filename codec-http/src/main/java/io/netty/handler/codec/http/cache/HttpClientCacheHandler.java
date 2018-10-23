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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.buffer.CompositeByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.EmptyHttpHeaders;
import io.netty.handler.codec.http.FullHttpMessage;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.internal.UnstableApi;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.Date;

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

    private HttpRequest request;
    private AggregatedFullHttpResponse inFlightResponse;
    private Date requestSent;

    public HttpClientCacheHandler(final HttpCacheStorage cacheStorage,
                                  final CacheConfig cacheConfig,
                                  final EventLoop eventLoop) {
        this.httpCache = new HttpCache(cacheStorage, new CacheKeyGenerator(), eventLoop);
        this.requestCachingPolicy = new RequestCachingPolicy();
        this.responseCachingPolicy = new ResponseCachingPolicy(cacheConfig.isSharedCache());
        this.httpCacheEntryChecker = new HttpCacheEntryChecker(cacheConfig.isSharedCache());
        this.httpResponseFromCacheGenerator = new HttpResponseFromCacheGenerator();
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
        if (!(msg instanceof HttpRequest)) {
            super.write(ctx, msg, promise);
            return;
        }

        write(ctx, (HttpRequest) msg, promise);
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {

        if (msg instanceof HttpResponse) {
            final HttpResponse httpResponse = (HttpResponse) msg;
            if (responseCachingPolicy.canBeCached(request, httpResponse)) {
                logger.debug("Start caching response...");
                if (httpResponse instanceof FullHttpResponse) {
                    // Already have a full response, no need to wait for the other elements
                    cacheResponse((FullHttpResponse) httpResponse);
                } else {
                    // A streamed message - initialize the cumulative buffer, and wait for incoming chunks.
                    CompositeByteBuf content = ctx.alloc().compositeBuffer(MAX_COMPOSITEBUFFER_COMPONENTS);
                    if (msg instanceof ByteBufHolder) {
                        appendPartialContent(content, ((ByteBufHolder) httpResponse).content());
                    }

                    inFlightResponse = new AggregatedFullHttpResponse(httpResponse, content.retain(), null);
                }
            }
        } else if (msg instanceof HttpContent) {

            final HttpContent httpContent = (HttpContent) msg;
            if (inFlightResponse != null) {
                CompositeByteBuf content = inFlightResponse.content();
                appendPartialContent(content, httpContent.content());

                aggregate(inFlightResponse, httpContent);

                if (msg instanceof LastHttpContent) {
                    cacheResponse(inFlightResponse);
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
            releaseCurrentResponse();
        }
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        try {
            super.handlerRemoved(ctx);
        } finally {
            // release current message if it is not null as it may be a left-over as there is not much more we can do in
            // this case
            releaseCurrentResponse();
        }
    }

    private void cacheResponse(FullHttpResponse response) {
        ByteBuf content = response.content();
        if (content.readableBytes() > cacheConfig.getMaxObjectSize()) {
            logger.info("HTTP response too big to be cached.");
        } else {
            httpCache.cache(request, response, requestSent, new Date())
                     .addListener(new GenericFutureListener<Future<? super HttpCacheEntry>>() {
                         @Override
                         public void operationComplete(Future<? super HttpCacheEntry> future) throws Exception {
                             if (future.isSuccess()) {
                                 logger.debug("Response has been cached");
                             }

                             releaseCurrentResponse();
                         }
                     });
        }
    }

    private void releaseCurrentResponse() {
        if (inFlightResponse != null) {
            inFlightResponse.release();
            inFlightResponse = null;
        }
    }

    protected void aggregate(FullHttpMessage aggregated, HttpContent content) throws Exception {
        if (content instanceof LastHttpContent) {
            // Merge trailing headers into the message.
            ((AggregatedFullHttpResponse) aggregated).setTrailingHeaders(((LastHttpContent) content).trailingHeaders());
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

    private static final class AggregatedFullHttpResponse implements FullHttpResponse {

        private final HttpMessage message;
        private final CompositeByteBuf content;
        private HttpHeaders trailingHeaders;

        AggregatedFullHttpResponse(HttpMessage message, CompositeByteBuf content, HttpHeaders trailingHeaders) {
            this.message = message;
            this.content = content;
            this.trailingHeaders = trailingHeaders;
        }

        @Override
        public HttpHeaders trailingHeaders() {
            HttpHeaders trailingHeaders = this.trailingHeaders;
            if (trailingHeaders == null) {
                return EmptyHttpHeaders.INSTANCE;
            } else {
                return trailingHeaders;
            }
        }

        void setTrailingHeaders(HttpHeaders trailingHeaders) {
            this.trailingHeaders = trailingHeaders;
        }

        @Override
        public HttpVersion getProtocolVersion() {
            return message.protocolVersion();
        }

        @Override
        public HttpVersion protocolVersion() {
            return message.protocolVersion();
        }

        @Override
        public FullHttpResponse setProtocolVersion(HttpVersion version) {
            message.setProtocolVersion(version);
            return this;
        }

        @Override
        public HttpHeaders headers() {
            return message.headers();
        }

        @Override
        public DecoderResult decoderResult() {
            return message.decoderResult();
        }

        @Override
        public DecoderResult getDecoderResult() {
            return message.decoderResult();
        }

        @Override
        public void setDecoderResult(DecoderResult result) {
            message.setDecoderResult(result);
        }

        @Override
        public CompositeByteBuf content() {
            return content;
        }

        @Override
        public int refCnt() {
            return content.refCnt();
        }

        @Override
        public FullHttpResponse retain() {
            content.retain();
            return this;
        }

        @Override
        public FullHttpResponse retain(int increment) {
            content.retain(increment);
            return this;
        }

        @Override
        public FullHttpResponse touch(Object hint) {
            content.touch(hint);
            return this;
        }

        @Override
        public FullHttpResponse touch() {
            content.touch();
            return this;
        }

        @Override
        public boolean release() {
            return content.release();
        }

        @Override
        public boolean release(int decrement) {
            return content.release(decrement);
        }

        @Override
        public FullHttpResponse copy() {
            return replace(content().copy());
        }

        @Override
        public FullHttpResponse duplicate() {
            return replace(content().duplicate());
        }

        @Override
        public FullHttpResponse retainedDuplicate() {
            return replace(content().retainedDuplicate());
        }

        @Override
        public FullHttpResponse replace(ByteBuf content) {
            DefaultFullHttpResponse dup = new DefaultFullHttpResponse(getProtocolVersion(), getStatus(), content,
                                                                      headers().copy(), trailingHeaders().copy());
            dup.setDecoderResult(decoderResult());
            return dup;
        }

        @Override
        public FullHttpResponse setStatus(HttpResponseStatus status) {
            ((HttpResponse) message).setStatus(status);
            return this;
        }

        @Override
        public HttpResponseStatus getStatus() {
            return ((HttpResponse) message).status();
        }

        @Override
        public HttpResponseStatus status() {
            return getStatus();
        }
    }
}
