package io.netty.handler.codec.http.cache;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
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
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.MockitoRule;

import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

import static io.netty.buffer.Unpooled.EMPTY_BUFFER;
import static io.netty.buffer.Unpooled.copiedBuffer;
import static io.netty.handler.codec.http.HttpHeaderNames.CACHE_CONTROL;
import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * - return cached entry can be served by cache / cache non expired
 * - cacheable but not cached
 * -
 */
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

    private static DefaultFullHttpResponse response() {
        return new DefaultFullHttpResponse(HTTP_1_1, HttpResponseStatus.OK, copiedBuffer("Hello World", CharsetUtil.UTF_8));
    }

    /**
     * Outbound: HttpRequest
     */

    @Test
    public void shouldReturnCachedEntry() {
        final EmbeddedChannel channel = new EmbeddedChannel(new HttpClientCacheHandler(requestCachingPolicy, cache, responseCachingPolicy, httpCacheEntryChecker, httpResponseFromCacheGenerator));

        final DefaultFullHttpRequest request = new DefaultFullHttpRequest(HTTP_1_1, HttpMethod.GET, "/info");

        final DefaultFullHttpResponse cachedResponse = response();
        final HttpCacheEntry httpCacheEntry = new HttpCacheEntry(cachedResponse, new Date(), new Date(),
                HttpResponseStatus.OK, EmptyHttpHeaders.INSTANCE);
        when(requestCachingPolicy.canBeServedFromCache(request)).thenReturn(true);
        when(cache.getCacheEntry(request)).thenReturn(httpCacheEntry);
        when(httpCacheEntryChecker.canUseCachedResponse(any(CacheControlDirectives.class), eq(httpCacheEntry), any(Date.class)))
                .thenReturn(true);
        when(httpResponseFromCacheGenerator.generate(eq(request), eq(httpCacheEntry))).thenReturn(cachedResponse);

        channel.writeOutbound(request);

        final FullHttpResponse response = channel.readInbound();

        verify(cache).getCacheEntry(request);
        assertThat(response.content().toString(CharsetUtil.UTF_8), is("Hello World"));
    }

    @Test
    public void shouldIgnoreNonHttpRequest() {
        final EmbeddedChannel channel = new EmbeddedChannel(new HttpClientCacheHandler(requestCachingPolicy, cache, responseCachingPolicy, httpCacheEntryChecker, httpResponseFromCacheGenerator));

        final Object msg = new Object();
        channel.writeOutbound(msg);

        assertThat(channel.readOutbound(), is(msg));
    }

    @Test
    public void shouldPassNonCacheableRequestThrough() {
        final EmbeddedChannel channel = new EmbeddedChannel(new HttpClientCacheHandler(requestCachingPolicy, cache, responseCachingPolicy, httpCacheEntryChecker, httpResponseFromCacheGenerator));

        final HttpRequest request = new DefaultFullHttpRequest(HTTP_1_1, HttpMethod.GET, "/info");

        when(requestCachingPolicy.canBeServedFromCache(request)).thenReturn(false);

        channel.writeOutbound(request);

        verify(cache, never()).getCacheEntry(request);
        assertThat((HttpRequest) channel.readOutbound(), is(request));
    }

    @Test
    public void shouldPassNonCachedRequestThrough() {
        final EmbeddedChannel channel = new EmbeddedChannel(new HttpClientCacheHandler(requestCachingPolicy, cache, responseCachingPolicy, httpCacheEntryChecker, httpResponseFromCacheGenerator));

        final HttpRequest request = new DefaultFullHttpRequest(HTTP_1_1, HttpMethod.GET, "/info");

        when(requestCachingPolicy.canBeServedFromCache(request)).thenReturn(true);
        when(cache.getCacheEntry(request)).thenReturn(null);

        channel.writeOutbound(request);

        assertThat((HttpRequest) channel.readOutbound(), is(request));
    }

    @Test
    public void shouldRespondWith504IfNonCachedRequestAndCacheControlOnlyIfCachedUsed() {
        final EmbeddedChannel channel = new EmbeddedChannel(new HttpClientCacheHandler(requestCachingPolicy, cache, responseCachingPolicy, httpCacheEntryChecker, httpResponseFromCacheGenerator));

        final HttpRequest request = new DefaultFullHttpRequest(HTTP_1_1, HttpMethod.GET, "/info", EMPTY_BUFFER, new ReadOnlyHttpHeaders(false, HttpHeaderNames.CACHE_CONTROL, HttpHeaderValues.ONLY_IF_CACHED), new ReadOnlyHttpHeaders(false));

        when(requestCachingPolicy.canBeServedFromCache(request)).thenReturn(true);
        when(cache.getCacheEntry(request)).thenReturn(null);

        channel.writeOutbound(request);

        final FullHttpResponse response = channel.readInbound();

        assertThat(response.status(), is(HttpResponseStatus.GATEWAY_TIMEOUT));
    }

    /**
     * Inbound: HttpResponse
     */

    @Test
    public void shouldCacheResponseIfCacheable() {
        final EmbeddedChannel channel = new EmbeddedChannel(new HttpClientCacheHandler(requestCachingPolicy, cache, responseCachingPolicy, httpCacheEntryChecker, httpResponseFromCacheGenerator));

        final DefaultFullHttpResponse response = response();

        when(responseCachingPolicy.canBeCached((HttpRequest) any(), eq(response))).thenReturn(true);

        channel.writeInbound(response);

        verify(cache).cache((HttpRequest) any(), eq(response), (Date) any(), (Date) any());
    }

    @Test
    public void shouldIgnoreNonHttpResponse() {
        final EmbeddedChannel channel = new EmbeddedChannel(new HttpClientCacheHandler(requestCachingPolicy, cache, responseCachingPolicy, httpCacheEntryChecker, httpResponseFromCacheGenerator));

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
        final EmbeddedChannel channel = new EmbeddedChannel(echoHandler, new HttpClientCacheHandler(requestCachingPolicy, new HttpCache(new HttpCacheMemoryStorage()), responseCachingPolicy, httpCacheEntryChecker, httpResponseFromCacheGenerator));

        final DefaultFullHttpRequest request = new DefaultFullHttpRequest(HTTP_1_1, HttpMethod.GET, "/info");
        request.headers().add(HOST, "example.com");

        when(requestCachingPolicy.canBeServedFromCache(request)).thenReturn(true);
        when(responseCachingPolicy.canBeCached(any(HttpRequest.class), any(HttpResponse.class))).thenReturn(true);
        when(httpCacheEntryChecker.canUseCachedResponse(any(CacheControlDirectives.class), any(HttpCacheEntry.class), any(Date.class))).thenReturn(true);
        when(httpResponseFromCacheGenerator.generate(any(HttpRequest.class), any(HttpCacheEntry.class))).thenReturn(response());

        channel.writeOutbound(request);
        channel.writeOutbound(request);

        assertThat(echoHandler.getCallCount(), is(1));
    }

    private static class EchoHandler extends ChannelOutboundHandlerAdapter {

        private final AtomicInteger callCount = new AtomicInteger();

        @Override
        public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise) {
            final DefaultFullHttpResponse response = response();
            response.headers().add(CACHE_CONTROL, "max-age=3600");

            ctx.fireChannelRead(response);
            callCount.incrementAndGet();
        }

        public int getCallCount() {
            return callCount.get();
        }
    }
}