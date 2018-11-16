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
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.DateFormatter;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.EmptyHttpHeaders;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.ReadOnlyHttpHeaders;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.SucceededFuture;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.MockitoRule;

import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

import static io.netty.buffer.Unpooled.*;
import static io.netty.handler.codec.http.HttpHeaderNames.*;
import static io.netty.handler.codec.http.HttpVersion.*;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
@RunWith(MockitoJUnitRunner.class)
public class HttpClientCacheHandlerTest {

    @Rule
    public MockitoRule mockitoRule;

    @Mock
    private RequestCachingPolicy requestCachingPolicy;

    @Mock
    private HttpCache cache;

    @Mock
    private ResponseCachingPolicy responseCachingPolicy;

    @Mock
    private HttpCacheEntryChecker httpCacheEntryChecker;

    @Mock
    private HttpResponseFromCacheGenerator httpResponseFromCacheGenerator;

    private static DefaultFullHttpResponse fullHttpResponse() {
        return new DefaultFullHttpResponse(HTTP_1_1, HttpResponseStatus.OK,
                                           copiedBuffer("Hello World", CharsetUtil.UTF_8));
    }

    private static HttpResponse httpResponse() {
        return new DefaultHttpResponse(HTTP_1_1, HttpResponseStatus.OK);
    }

    private static HttpResponse notModifiedHttpResponse() {
        return new DefaultFullHttpResponse(HTTP_1_1, HttpResponseStatus.NOT_MODIFIED,
                                           copiedBuffer("Hello World", CharsetUtil.UTF_8));
    }

    /**
     * Outbound: HttpRequest
     */

    @Test
    public void shouldReturnCachedEntry() {
        final EmbeddedChannel channel = new EmbeddedChannel(
                new HttpClientCacheHandler(requestCachingPolicy, cache, responseCachingPolicy, httpCacheEntryChecker,
                                           httpResponseFromCacheGenerator, CacheConfig.DEFAULT));

        final DefaultFullHttpRequest request = new DefaultFullHttpRequest(HTTP_1_1, HttpMethod.GET, "/info");

        final DefaultFullHttpResponse cachedResponse = fullHttpResponse();
        final HttpCacheEntry httpCacheEntry = new HttpCacheEntry(cachedResponse, new Date(), new Date(),
                                                                 HttpResponseStatus.OK, EmptyHttpHeaders.INSTANCE);
        when(requestCachingPolicy.canBeServedFromCache(request)).thenReturn(true);
        when(cache.getCacheEntry(eq(request), any(Promise.class)))
                .thenReturn(new SucceededFuture<HttpCacheEntry>(ImmediateEventExecutor.INSTANCE, httpCacheEntry));
        when(httpCacheEntryChecker
                     .canUseCachedResponse(any(HttpRequest.class), any(CacheControlDirectives.class),
                                           eq(httpCacheEntry), any(Date.class)))
                .thenReturn(true);
        when(httpResponseFromCacheGenerator.generate(eq(request), eq(httpCacheEntry))).thenReturn(cachedResponse);

        channel.writeOutbound(request);

        final FullHttpResponse response = channel.readInbound();

        verify(cache).getCacheEntry(eq(request), any(Promise.class));
        assertThat(response.content().toString(CharsetUtil.UTF_8), is("Hello World"));
    }

    @Test
    public void shouldPassThroughIfCachedEntryNonUsable() {
        final EmbeddedChannel channel = new EmbeddedChannel(
                new HttpClientCacheHandler(requestCachingPolicy, cache, responseCachingPolicy, httpCacheEntryChecker,
                                           httpResponseFromCacheGenerator, CacheConfig.DEFAULT));

        final HttpRequest request = new DefaultFullHttpRequest(HTTP_1_1, HttpMethod.GET, "/info");

        final DefaultFullHttpResponse cachedResponse = fullHttpResponse();
        final HttpCacheEntry httpCacheEntry = new HttpCacheEntry(cachedResponse, new Date(), new Date(),
                                                                 HttpResponseStatus.OK, EmptyHttpHeaders.INSTANCE);
        when(requestCachingPolicy.canBeServedFromCache(request)).thenReturn(true);
        when(cache.getCacheEntry(eq(request), any(Promise.class)))
                .thenReturn(new SucceededFuture<HttpCacheEntry>(ImmediateEventExecutor.INSTANCE, httpCacheEntry));
        when(httpCacheEntryChecker
                     .canUseCachedResponse(any(HttpRequest.class), any(CacheControlDirectives.class),
                                           eq(httpCacheEntry), any(Date.class)))
                .thenReturn(false);

        channel.writeOutbound(request);

        verify(cache).getCacheEntry(eq(request), any(Promise.class));
        assertThat((HttpRequest) channel.readOutbound(), is(request));
    }

    @Test
    public void shouldRespondWith504IfCachedEntryNonUsableAndCacheControlOnlyIfCachedUsed() {
        final EmbeddedChannel channel = new EmbeddedChannel(
                new HttpClientCacheHandler(requestCachingPolicy, cache, responseCachingPolicy, httpCacheEntryChecker,
                                           httpResponseFromCacheGenerator, CacheConfig.DEFAULT));

        final HttpRequest request = new DefaultFullHttpRequest(HTTP_1_1, HttpMethod.GET, "/info", EMPTY_BUFFER,
                                                               new ReadOnlyHttpHeaders(false,
                                                                                       HttpHeaderNames.CACHE_CONTROL,
                                                                                       HttpHeaderValues.ONLY_IF_CACHED),
                                                               new ReadOnlyHttpHeaders(false));

        final DefaultFullHttpResponse cachedResponse = fullHttpResponse();
        final HttpCacheEntry httpCacheEntry = new HttpCacheEntry(cachedResponse, new Date(), new Date(),
                                                                 HttpResponseStatus.OK, EmptyHttpHeaders.INSTANCE);
        when(requestCachingPolicy.canBeServedFromCache(request)).thenReturn(true);
        when(cache.getCacheEntry(eq(request), any(Promise.class)))
                .thenReturn(new SucceededFuture<HttpCacheEntry>(ImmediateEventExecutor.INSTANCE, httpCacheEntry));
        when(httpCacheEntryChecker
                     .canUseCachedResponse(any(HttpRequest.class), any(CacheControlDirectives.class),
                                           eq(httpCacheEntry), any(Date.class)))
                .thenReturn(false);

        channel.writeOutbound(request);

        final FullHttpResponse response = channel.readInbound();
        assertThat(response.status(), is(HttpResponseStatus.GATEWAY_TIMEOUT));
    }

    @Test
    public void shouldIgnoreNonHttpRequest() {
        final EmbeddedChannel channel = new EmbeddedChannel(
                new HttpClientCacheHandler(requestCachingPolicy, cache, responseCachingPolicy, httpCacheEntryChecker,
                                           httpResponseFromCacheGenerator, CacheConfig.DEFAULT));

        final Object msg = new Object();
        channel.writeOutbound(msg);

        assertThat(channel.readOutbound(), is(msg));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldPassNonCacheableRequestThrough() {
        final EmbeddedChannel channel = new EmbeddedChannel(
                new HttpClientCacheHandler(requestCachingPolicy, cache, responseCachingPolicy, httpCacheEntryChecker,
                                           httpResponseFromCacheGenerator, CacheConfig.DEFAULT));

        final HttpRequest request = new DefaultFullHttpRequest(HTTP_1_1, HttpMethod.GET, "/info");

        when(requestCachingPolicy.canBeServedFromCache(request)).thenReturn(false);

        channel.writeOutbound(request);

        verify(cache, never()).getCacheEntry(eq(request), any(Promise.class));
        assertThat((HttpRequest) channel.readOutbound(), is(request));
    }

    @Test
    public void shouldPassNonCachedRequestThrough() {
        final EmbeddedChannel channel = new EmbeddedChannel(
                new HttpClientCacheHandler(requestCachingPolicy, cache, responseCachingPolicy, httpCacheEntryChecker,
                                           httpResponseFromCacheGenerator, CacheConfig.DEFAULT));

        final HttpRequest request = new DefaultFullHttpRequest(HTTP_1_1, HttpMethod.GET, "/info");

        when(requestCachingPolicy.canBeServedFromCache(request)).thenReturn(true);
        when(cache.getCacheEntry(eq(request), any(Promise.class)))
                .thenReturn(new SucceededFuture<HttpCacheEntry>(ImmediateEventExecutor.INSTANCE, null));

        channel.writeOutbound(request);

        assertThat((HttpRequest) channel.readOutbound(), is(request));
    }

    @Test
    public void shouldRespondWith504IfNonCachedRequestAndCacheControlOnlyIfCachedUsed() {
        final EmbeddedChannel channel = new EmbeddedChannel(
                new HttpClientCacheHandler(requestCachingPolicy, cache, responseCachingPolicy, httpCacheEntryChecker,
                                           httpResponseFromCacheGenerator, CacheConfig.DEFAULT));

        final HttpRequest request = new DefaultFullHttpRequest(HTTP_1_1, HttpMethod.GET, "/info", EMPTY_BUFFER,
                                                               new ReadOnlyHttpHeaders(false,
                                                                                       HttpHeaderNames.CACHE_CONTROL,
                                                                                       HttpHeaderValues.ONLY_IF_CACHED),
                                                               new ReadOnlyHttpHeaders(false));

        when(requestCachingPolicy.canBeServedFromCache(request)).thenReturn(true);
        when(cache.getCacheEntry(eq(request), any(Promise.class)))
                .thenReturn(new SucceededFuture<HttpCacheEntry>(ImmediateEventExecutor.INSTANCE, null));

        channel.writeOutbound(request);

        final FullHttpResponse response = channel.readInbound();

        assertThat(response.status(), is(HttpResponseStatus.GATEWAY_TIMEOUT));
    }

    /**
     * Inbound: HttpResponse
     */

    @Test
    public void shouldCacheFullHttpResponseIfCacheable() {
        final EmbeddedChannel channel = new EmbeddedChannel(
                new HttpClientCacheHandler(requestCachingPolicy, cache, responseCachingPolicy, httpCacheEntryChecker,
                                           httpResponseFromCacheGenerator, CacheConfig.DEFAULT));

        final DefaultFullHttpResponse response = fullHttpResponse();

        when(responseCachingPolicy.canBeCached((HttpRequest) any(), eq(response))).thenReturn(true);
        when(cache.cache((HttpRequest) any(), (FullHttpResponse) any(), (Date) any(), (Date) any()))
                .thenReturn(new SucceededFuture<HttpCacheEntry>(ImmediateEventExecutor.INSTANCE, null));
        when(cache.getCacheEntry((HttpRequest) any(), any(Promise.class)))
                .thenReturn(new SucceededFuture(ImmediateEventExecutor.INSTANCE, null));

        channel.writeInbound(response);

        final ArgumentCaptor<FullHttpResponse> argumentCaptor = ArgumentCaptor.forClass(FullHttpResponse.class);
        verify(cache).cache((HttpRequest) any(), argumentCaptor.capture(), (Date) any(), (Date) any());

        final FullHttpResponse cachedResponse = argumentCaptor.getValue();
        assertThat(cachedResponse.content().compareTo(response.content()), is(0));
    }

    @Test
    public void shouldNotCacheFullHttpResponseIfNotCacheable() {
        final EmbeddedChannel channel = new EmbeddedChannel(
                new HttpClientCacheHandler(requestCachingPolicy, cache, responseCachingPolicy, httpCacheEntryChecker,
                                           httpResponseFromCacheGenerator, CacheConfig.DEFAULT));

        final DefaultFullHttpResponse response = fullHttpResponse();

        when(responseCachingPolicy.canBeCached((HttpRequest) any(), eq(response))).thenReturn(false);

        channel.writeInbound(response);

        verify(cache, never()).cache((HttpRequest) any(), eq(response), (Date) any(), (Date) any());
    }

    @Test
    public void shouldNotCacheFullHttpResponseIfContentBiggerThanMax() {
        final EmbeddedChannel channel = new EmbeddedChannel(
                new HttpClientCacheHandler(requestCachingPolicy, cache, responseCachingPolicy, httpCacheEntryChecker,
                                           httpResponseFromCacheGenerator, CacheConfig.custom()
                                                                                      .setMaxObjectSize(0)
                                                                                      .build()));

        final DefaultFullHttpResponse response = fullHttpResponse();

        when(responseCachingPolicy.canBeCached((HttpRequest) any(), eq(response))).thenReturn(true);

        channel.writeInbound(response);

        verify(cache, never()).cache((HttpRequest) any(), any(FullHttpResponse.class), (Date) any(), (Date) any());
    }

    @Test
    public void shouldNotCacheHttpResponseAndContentIfTotalBiggerThanMax() {
        final EmbeddedChannel channel = new EmbeddedChannel(
                new HttpClientCacheHandler(requestCachingPolicy, cache, responseCachingPolicy, httpCacheEntryChecker,
                                           httpResponseFromCacheGenerator, CacheConfig.custom()
                                                                                      .setMaxObjectSize(0)
                                                                                      .build()));

        final HttpResponse response = httpResponse();
        final ByteBuf content = copiedBuffer("Hello World", CharsetUtil.UTF_8);

        when(responseCachingPolicy.canBeCached((HttpRequest) any(), eq(response))).thenReturn(true);

        channel.writeInbound(response);
        channel.writeInbound(new DefaultHttpContent(content));
        channel.writeInbound(new DefaultLastHttpContent());

        verify(cache, never()).cache((HttpRequest) any(), any(FullHttpResponse.class), (Date) any(), (Date) any());
    }

    @Test
    public void shouldInvalidateEntryIfResponseIsNotCacheable() {
        final EmbeddedChannel channel = new EmbeddedChannel(
                new HttpClientCacheHandler(requestCachingPolicy, cache, responseCachingPolicy, httpCacheEntryChecker,
                                           httpResponseFromCacheGenerator, CacheConfig.DEFAULT));

        final DefaultFullHttpResponse response = fullHttpResponse();

        when(responseCachingPolicy.canBeCached((HttpRequest) any(), eq(response))).thenReturn(false);

        channel.writeInbound(response);

        verify(cache, never()).flushCacheEntriesFor(any(HttpRequest.class), any(Promise.class));
    }

    @Test
    public void shouldFlushCacheEntriesInvalidatedByExchange() {
        final EmbeddedChannel channel = new EmbeddedChannel(
                new HttpClientCacheHandler(requestCachingPolicy, cache, responseCachingPolicy, httpCacheEntryChecker,
                                           httpResponseFromCacheGenerator, CacheConfig.DEFAULT));

        final HttpResponse response = httpResponse();
        final ByteBuf content = copiedBuffer("Hello World", CharsetUtil.UTF_8);

        when(responseCachingPolicy.canBeCached((HttpRequest) any(), eq(response))).thenReturn(true);

        channel.writeInbound(response);
        channel.writeInbound(new DefaultHttpContent(content));

        verify(cache).flushCacheEntriesInvalidatedByExchange((HttpRequest) any(), any(HttpResponse.class),
                                                             any(Promise.class));
    }

    @Test
    public void shouldCacheHttpResponseWithContentIfCacheable() {
        final EmbeddedChannel channel = new EmbeddedChannel(
                new HttpClientCacheHandler(requestCachingPolicy, cache, responseCachingPolicy, httpCacheEntryChecker,
                                           httpResponseFromCacheGenerator, CacheConfig.DEFAULT));

        final HttpResponse response = httpResponse();
        final ByteBuf content = copiedBuffer("Hello World", CharsetUtil.UTF_8);

        when(responseCachingPolicy.canBeCached((HttpRequest) any(), eq(response))).thenReturn(true);
        when(cache.cache((HttpRequest) any(), (FullHttpResponse) any(), (Date) any(), (Date) any()))
                .thenReturn(new SucceededFuture<HttpCacheEntry>(ImmediateEventExecutor.INSTANCE, null));
        when(cache.getCacheEntry((HttpRequest) any(), any(Promise.class)))
                .thenReturn(new SucceededFuture(ImmediateEventExecutor.INSTANCE, null));

        channel.writeInbound(response);
        channel.writeInbound(new DefaultHttpContent(content));
        channel.writeInbound(new DefaultLastHttpContent());

        final ArgumentCaptor<FullHttpResponse> argumentCaptor = ArgumentCaptor.forClass(FullHttpResponse.class);
        verify(cache).cache((HttpRequest) any(), argumentCaptor.capture(), (Date) any(), (Date) any());

        final FullHttpResponse cachedResponse = argumentCaptor.getValue();
        assertThat(cachedResponse.content().compareTo(content), is(0));
    }

    @Test
    public void shouldNotCacheHttpResponseIfCacheEntryIsFresher() {
        final EmbeddedChannel channel = new EmbeddedChannel(
                new HttpClientCacheHandler(requestCachingPolicy, cache, responseCachingPolicy, httpCacheEntryChecker,
                                           httpResponseFromCacheGenerator, CacheConfig.DEFAULT));

        final HttpResponse response = httpResponse();
        final Date before = new Date(1000L);
        final Date after = new Date(2000L);
        response.headers().add(HttpHeaderNames.DATE, DateFormatter.format(before));
        final ByteBuf content = copiedBuffer("Hello World", CharsetUtil.UTF_8);

        when(responseCachingPolicy.canBeCached((HttpRequest) any(), eq(response))).thenReturn(true);

        final HttpCacheEntry httpCacheEntry = new HttpCacheEntry(fullHttpResponse(), after, after,
                                                                 HttpResponseStatus.OK, EmptyHttpHeaders.INSTANCE);
        when(cache.getCacheEntry((HttpRequest) any(), any(Promise.class)))
                .thenReturn(new SucceededFuture(ImmediateEventExecutor.INSTANCE, httpCacheEntry));

        channel.writeInbound(response);
        channel.writeInbound(new DefaultHttpContent(content));
        channel.writeInbound(new DefaultLastHttpContent());

        final ArgumentCaptor<FullHttpResponse> argumentCaptor = ArgumentCaptor.forClass(FullHttpResponse.class);
        verify(cache, never()).cache((HttpRequest) any(), argumentCaptor.capture(), (Date) any(), (Date) any());
    }

    @Test
    public void shouldNotCacheHttpResponseWithContentIfNotCacheable() {
        final EmbeddedChannel channel = new EmbeddedChannel(
                new HttpClientCacheHandler(requestCachingPolicy, cache, responseCachingPolicy, httpCacheEntryChecker,
                                           httpResponseFromCacheGenerator, CacheConfig.DEFAULT));

        final HttpResponse response = httpResponse();
        final ByteBuf content = copiedBuffer("Hello World", CharsetUtil.UTF_8);

        when(responseCachingPolicy.canBeCached((HttpRequest) any(), eq(response))).thenReturn(false);

        channel.writeInbound(response);
        channel.writeInbound(new DefaultHttpContent(content));
        channel.writeInbound(new DefaultLastHttpContent());

        verify(cache, never()).cache((HttpRequest) any(), any(FullHttpResponse.class), (Date) any(), (Date) any());
    }

    @Test
    public void shouldIgnoreNonHttpResponse() {
        final EmbeddedChannel channel = new EmbeddedChannel(
                new HttpClientCacheHandler(requestCachingPolicy, cache, responseCachingPolicy, httpCacheEntryChecker,
                                           httpResponseFromCacheGenerator, CacheConfig.DEFAULT));

        final Object msg = new Object();
        channel.writeInbound(msg);

        assertThat(channel.readInbound(), is(msg));
    }

    /**
     * Outbound + Inbound
     */

    @Test
    public void shouldUseCachedResponseForNextRequest() {
        final EchoHandler echoHandler = new EchoHandler();
        final ImmediateEventExecutor eventExecutor = ImmediateEventExecutor.INSTANCE;
        final HttpCache httpCache = new HttpCache(
                new HttpCacheMemoryStorage(),
                eventExecutor);
        final EmbeddedChannel channel = new EmbeddedChannel(echoHandler,
                                                            new HttpClientCacheHandler(requestCachingPolicy,
                                                                                       httpCache,
                                                                                       responseCachingPolicy,
                                                                                       httpCacheEntryChecker,
                                                                                       httpResponseFromCacheGenerator,
                                                                                       CacheConfig.DEFAULT));

        final DefaultFullHttpRequest request = new DefaultFullHttpRequest(HTTP_1_1, HttpMethod.GET, "/info");
        request.headers().add(HOST, "example.com");

        when(requestCachingPolicy.canBeServedFromCache(request)).thenReturn(true);
        when(responseCachingPolicy.canBeCached(any(HttpRequest.class), any(HttpResponse.class))).thenReturn(true);
        when(httpCacheEntryChecker.canUseCachedResponse(any(HttpRequest.class), any(CacheControlDirectives.class),
                                                        any(HttpCacheEntry.class),
                                                        any(Date.class))).thenReturn(true);
        when(httpResponseFromCacheGenerator.generate(any(HttpRequest.class), any(HttpCacheEntry.class))).thenReturn(
                fullHttpResponse());

        channel.writeOutbound(request);
        channel.writeOutbound(request);

        assertThat(echoHandler.getCallCount(), is(1));
    }

    @Test
    public void shouldRevalidateCachedEntry() {
        final EchoHandler echoHandler = new EchoHandler();
        final ImmediateEventExecutor eventExecutor = ImmediateEventExecutor.INSTANCE;
        final EmbeddedChannel channel = new EmbeddedChannel(echoHandler,
                                                            new HttpClientCacheHandler(requestCachingPolicy,
                                                                                       cache,
                                                                                       responseCachingPolicy,
                                                                                       httpCacheEntryChecker,
                                                                                       httpResponseFromCacheGenerator,
                                                                                       CacheConfig.DEFAULT));

        final DefaultFullHttpRequest request = new DefaultFullHttpRequest(HTTP_1_1, HttpMethod.GET, "/info");
        request.headers().add(HOST, "example.com");

        final DefaultFullHttpResponse cachedResponse = fullHttpResponse();
        final DefaultHttpHeaders cacheEntryHeaders = new DefaultHttpHeaders();
        cacheEntryHeaders.add(ETAG, "etagValue");
        final HttpCacheEntry httpCacheEntry = new HttpCacheEntry(cachedResponse, new Date(), new Date(),
                                                                 HttpResponseStatus.OK, cacheEntryHeaders);

        when(cache.getCacheEntry(any(HttpRequest.class), any(Promise.class)))
                .thenReturn(new SucceededFuture(eventExecutor, null),
                            new SucceededFuture(eventExecutor, httpCacheEntry));
        when(cache.updateCacheEntry(any(HttpRequest.class), any(HttpResponse.class), (Date) any(), (Date) any()))
                .thenReturn(new SucceededFuture(eventExecutor, httpCacheEntry));
        when(requestCachingPolicy.canBeServedFromCache(request)).thenReturn(true);
        when(responseCachingPolicy.canBeCached(any(HttpRequest.class), any(HttpResponse.class))).thenReturn(true);
        when(httpCacheEntryChecker.canUseCachedResponse(any(HttpRequest.class), any(CacheControlDirectives.class),
                                                        any(HttpCacheEntry.class),
                                                        any(Date.class))).thenReturn(false);
        final HttpResponse notModifiedHttpResponse = notModifiedHttpResponse();
        when(httpResponseFromCacheGenerator.generateNotModifiedResponse(any(HttpCacheEntry.class))).thenReturn(
                notModifiedHttpResponse);

        channel.writeOutbound(request);
        channel.readInbound();

        request.headers().add(IF_NONE_MATCH, "etagValue");
        channel.writeOutbound(request);
        final HttpResponse response = channel.readInbound();

        assertThat("", response, is(notModifiedHttpResponse));
        assertThat(echoHandler.getCallCount(), is(1));
    }

    private static class EchoHandler extends ChannelOutboundHandlerAdapter {

        private final AtomicInteger callCount = new AtomicInteger();
        private final AtomicInteger callCount304 = new AtomicInteger();

        @Override
        public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise)
                throws Exception {
            if (!(msg instanceof HttpRequest)) {
                super.write(ctx, msg, promise);
                return;
            }

            final HttpRequest httpRequest = (HttpRequest) msg;
            if (httpRequest.headers().contains(IF_NONE_MATCH)) {
                callCount304.incrementAndGet();
                ctx.fireChannelRead(
                        new DefaultFullHttpResponse(HTTP_1_1, HttpResponseStatus.NOT_MODIFIED, EMPTY_BUFFER));
                return;
            }

            final DefaultFullHttpResponse response = fullHttpResponse();
            response.headers().add(CACHE_CONTROL, "max-age=3600");

            ctx.fireChannelRead(response);
            callCount.incrementAndGet();
        }

        public int getCallCount() {
            return callCount.get();
        }

        public int getCallCount304() {
            return callCount304.get();
        }
    }
}
