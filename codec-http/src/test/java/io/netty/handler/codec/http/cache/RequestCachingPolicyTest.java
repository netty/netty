package io.netty.handler.codec.http.cache;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.ReadOnlyHttpHeaders;
import io.netty.util.CharsetUtil;
import org.junit.Before;
import org.junit.Test;

import static io.netty.buffer.Unpooled.copiedBuffer;
import static io.netty.buffer.Unpooled.unreleasableBuffer;
import static io.netty.handler.codec.http.HttpHeaderNames.CACHE_CONTROL;
import static io.netty.handler.codec.http.HttpHeaderValues.NO_STORE;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class RequestCachingPolicyTest {

    private RequestCachingPolicy requestPolicy;

    @Before
    public void setUp() {
        requestPolicy = new RequestCachingPolicy();
    }

    @Test
    public void getRequestCanBeServedFromCache() {
        final DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "uri", true);
        assertThat(requestPolicy.canBeServedFromCache(request), is(true));
    }

    @Test
    public void headRequestCanBeServedFromCache() {
        final DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.HEAD, "uri", true);
        assertThat(requestPolicy.canBeServedFromCache(request), is(true));
    }

    @Test
    public void postRequestCanNotBeServedFromCache() {
        final DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "uri", true);
        assertThat(requestPolicy.canBeServedFromCache(request), is(false));
    }

    @Test
    public void http1RequestCanNotBeServedFromCache() {
        final DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_0, HttpMethod.GET, "uri", true);
        assertThat(requestPolicy.canBeServedFromCache(request), is(false));
    }

    @Test
    public void cacheControlNoStoreRequestCanNotBeServedFromCache() {
        final ReadOnlyHttpHeaders headers = new ReadOnlyHttpHeaders(true, CACHE_CONTROL, NO_STORE);
        final ByteBuf content = unreleasableBuffer(copiedBuffer("Hello World", CharsetUtil.UTF_8));
        final DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_0, HttpMethod.GET, "uri", content, headers, new ReadOnlyHttpHeaders(true));
        assertThat(requestPolicy.canBeServedFromCache(request), is(false));
    }
}