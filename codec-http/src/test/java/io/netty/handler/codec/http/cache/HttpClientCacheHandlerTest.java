package io.netty.handler.codec.http.cache;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpContent;
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
import static org.mockito.ArgumentMatchers.eq;
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
                     .canUseCachedResponse(any(CacheControlDirectives.class), eq(httpCacheEntry), any(Date.class)))
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
                     .canUseCachedResponse(any(CacheControlDirectives.class), eq(httpCacheEntry), any(Date.class)))
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
                     .canUseCachedResponse(any(CacheControlDirectives.class), eq(httpCacheEntry), any(Date.class)))
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
    public void shouldCacheHttpResponseWithContentIfCacheable() {
        final EmbeddedChannel channel = new EmbeddedChannel(
                new HttpClientCacheHandler(requestCachingPolicy, cache, responseCachingPolicy, httpCacheEntryChecker,
                                           httpResponseFromCacheGenerator, CacheConfig.DEFAULT));

        final HttpResponse response = httpResponse();
        final ByteBuf content = copiedBuffer("Hello World", CharsetUtil.UTF_8);

        when(responseCachingPolicy.canBeCached((HttpRequest) any(), eq(response))).thenReturn(true);
        when(cache.cache((HttpRequest) any(), (FullHttpResponse) any(), (Date) any(), (Date) any()))
                .thenReturn(new SucceededFuture<HttpCacheEntry>(ImmediateEventExecutor.INSTANCE, null));

        channel.writeInbound(response);
        channel.writeInbound(new DefaultHttpContent(content));
        channel.writeInbound(new DefaultLastHttpContent());

        final ArgumentCaptor<FullHttpResponse> argumentCaptor = ArgumentCaptor.forClass(FullHttpResponse.class);
        verify(cache).cache((HttpRequest) any(), argumentCaptor.capture(), (Date) any(), (Date) any());

        final FullHttpResponse cachedResponse = argumentCaptor.getValue();
        assertThat(cachedResponse.content().compareTo(content), is(0));
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
        final EmbeddedChannel channel = new EmbeddedChannel(echoHandler,
                                                            new HttpClientCacheHandler(requestCachingPolicy,
                                                                                       new HttpCache(
                                                                                               new HttpCacheMemoryStorage(),
                                                                                               eventExecutor),
                                                                                       responseCachingPolicy,
                                                                                       httpCacheEntryChecker,
                                                                                       httpResponseFromCacheGenerator,
                                                                                       CacheConfig.DEFAULT));

        final DefaultFullHttpRequest request = new DefaultFullHttpRequest(HTTP_1_1, HttpMethod.GET, "/info");
        request.headers().add(HOST, "example.com");

        when(requestCachingPolicy.canBeServedFromCache(request)).thenReturn(true);
        when(responseCachingPolicy.canBeCached(any(HttpRequest.class), any(HttpResponse.class))).thenReturn(true);
        when(httpCacheEntryChecker.canUseCachedResponse(any(CacheControlDirectives.class), any(HttpCacheEntry.class),
                                                        any(Date.class))).thenReturn(true);
        when(httpResponseFromCacheGenerator.generate(any(HttpRequest.class), any(HttpCacheEntry.class))).thenReturn(
                fullHttpResponse());

        channel.writeOutbound(request);
        channel.writeOutbound(request);

        assertThat(echoHandler.getCallCount(), is(1));
    }

    private static class EchoHandler extends ChannelOutboundHandlerAdapter {

        private final AtomicInteger callCount = new AtomicInteger();

        @Override
        public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise) {
            final DefaultFullHttpResponse response = fullHttpResponse();
            response.headers().add(CACHE_CONTROL, "max-age=3600");

            ctx.fireChannelRead(response);
            callCount.incrementAndGet();
        }

        public int getCallCount() {
            return callCount.get();
        }
    }
}