package io.netty.handler.codec.http.cache;

import io.netty.handler.codec.DateFormatter;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.ReadOnlyHttpHeaders;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Date;

import static io.netty.buffer.Unpooled.EMPTY_BUFFER;
import static io.netty.handler.codec.http.HttpHeaderNames.AUTHORIZATION;
import static io.netty.handler.codec.http.HttpHeaderNames.CACHE_CONTROL;
import static io.netty.handler.codec.http.HttpHeaderValues.MUST_REVALIDATE;
import static io.netty.handler.codec.http.HttpHeaderValues.NO_CACHE;
import static io.netty.handler.codec.http.HttpHeaderValues.NO_STORE;
import static io.netty.handler.codec.http.HttpHeaderValues.PUBLIC;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class ResponseCachingPolicyTest {
    @Rule
    public MockitoRule rule = MockitoJUnit.rule();

    private ResponseCachingPolicy responseCachingPolicy;

    @Before
    public void setUp() {
        responseCachingPolicy = new ResponseCachingPolicy(false);
    }

    @Test
    public void http1RequestCanNotBeCached() {
        final DefaultFullHttpResponse response = response(HttpResponseStatus.OK);
        assertThat(responseCachingPolicy.canBeCached(request(HttpVersion.HTTP_1_0, HttpMethod.GET), response), is(false));
    }

    @Test
    public void getRequestCanBeCached() {
        final DefaultFullHttpResponse response = response(HttpResponseStatus.OK);
        assertThat(responseCachingPolicy.canBeCached(request(HttpMethod.GET), response), is(true));
    }

    @Test
    public void headRequestCanBeCached() {
        final DefaultFullHttpResponse response = response(HttpResponseStatus.OK);
        assertThat(responseCachingPolicy.canBeCached(request(HttpMethod.HEAD), response), is(true));
    }

    @Test
    public void postRequestCanNotBeCached() {
        final DefaultFullHttpResponse response = response(HttpResponseStatus.OK);
        assertThat(responseCachingPolicy.canBeCached(request(HttpMethod.POST), response), is(false));
    }

    @Test
    public void partialContentCanNotBeCached() {
        final DefaultFullHttpResponse response = response(HttpResponseStatus.PARTIAL_CONTENT);
        assertThat(responseCachingPolicy.canBeCached(request(HttpMethod.GET), response), is(false));
    }

    @Test
    public void unknownStatusCodeCanNotBeCached() {
        final DefaultFullHttpResponse response = response(new HttpResponseStatus(123, "Unknown"));
        assertThat(responseCachingPolicy.canBeCached(request(HttpMethod.GET), response), is(false));
    }

    @Test
    public void responseWithDateHeaderCanNotBeCached() {
        final DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                EMPTY_BUFFER, new ReadOnlyHttpHeaders(false), new ReadOnlyHttpHeaders(false));
        assertThat(responseCachingPolicy.canBeCached(request(HttpMethod.GET), response), is(false));
    }

    @Test
    public void responseToGetWithNoStoreCacheControlHeaderCanNotBeCached() {
        final DefaultFullHttpResponse response = response(HttpResponseStatus.OK, CACHE_CONTROL, NO_STORE);
        assertThat(responseCachingPolicy.canBeCached(request(HttpMethod.GET), response), is(false));
    }

    @Test
    public void responseToHeadWithNoStoreCacheControlHeaderCanNotBeCached() {
        final DefaultFullHttpResponse response = response(HttpResponseStatus.OK, CACHE_CONTROL, NO_STORE);
        assertThat(responseCachingPolicy.canBeCached(request(HttpMethod.GET), response), is(false));
    }

    @Test
    public void responseToGetWithNoCacheCacheControlHeaderCanNotBeCached() {
        final DefaultFullHttpResponse response = response(HttpResponseStatus.OK, CACHE_CONTROL, NO_CACHE);
        assertThat(responseCachingPolicy.canBeCached(request(HttpMethod.GET), response), is(false));
    }

    @Test
    public void responseToHeadWithNoCacheCacheControlHeaderCanNotBeCached() {
        final DefaultFullHttpResponse response = response(HttpResponseStatus.OK, CACHE_CONTROL, NO_CACHE);
        assertThat(responseCachingPolicy.canBeCached(request(HttpMethod.HEAD), response), is(false));
    }

    @Test
    public void authorizedResponseCanNotBeCachedBySharedCache() {
        final DefaultFullHttpResponse response = response(HttpResponseStatus.OK, AUTHORIZATION, "Basic abcdefgh");
        responseCachingPolicy = new ResponseCachingPolicy(true);
        assertThat(responseCachingPolicy.canBeCached(request(HttpMethod.HEAD), response), is(false));
    }

    @Test
    public void authorizedResponseCanBeCachedByPrivateCache() {
        final DefaultFullHttpResponse response = response(HttpResponseStatus.OK, AUTHORIZATION, "Basic abcdefgh");
        responseCachingPolicy = new ResponseCachingPolicy(false);
        assertThat(responseCachingPolicy.canBeCached(request(HttpMethod.HEAD), response), is(true));
    }

    @Test
    public void authorizedResponseWithSMaxAgeCanBeCachedBySharedCache() {
        final DefaultFullHttpResponse response = response(HttpResponseStatus.OK,
                AUTHORIZATION, "Basic abcdefgh", CACHE_CONTROL, "s-maxage=3600");
        responseCachingPolicy = new ResponseCachingPolicy(true);
        assertThat(responseCachingPolicy.canBeCached(request(HttpMethod.HEAD), response), is(true));
    }

    @Test
    public void authorizedResponseWithMustRevalidateCanBeCachedBySharedCache() {
        final DefaultFullHttpResponse response = response(HttpResponseStatus.OK,
                AUTHORIZATION, "Basic abcdefgh", CACHE_CONTROL, MUST_REVALIDATE);
        responseCachingPolicy = new ResponseCachingPolicy(true);
        assertThat(responseCachingPolicy.canBeCached(request(HttpMethod.HEAD), response), is(true));
    }

    @Test
    public void authorizedResponseWithPublicCanBeCachedBySharedCache() {
        final DefaultFullHttpResponse response = response(HttpResponseStatus.OK,
                AUTHORIZATION, "Basic abcdefgh", CACHE_CONTROL, PUBLIC);
        responseCachingPolicy = new ResponseCachingPolicy(true);
        assertThat(responseCachingPolicy.canBeCached(request(HttpMethod.HEAD), response), is(true));
    }

    @Test
    public void authorizedResponseWithMaxAgeCanNotBeCachedBySharedCache() {
        final DefaultFullHttpResponse response = response(HttpResponseStatus.OK,
                AUTHORIZATION, "Basic abcdefgh", CACHE_CONTROL, "max-age=3600");
        responseCachingPolicy = new ResponseCachingPolicy(true);
        assertThat(responseCachingPolicy.canBeCached(request(HttpMethod.HEAD), response), is(false));
    }

    private DefaultFullHttpRequest request(final HttpMethod httpMethod) {
        return request(HttpVersion.HTTP_1_1, httpMethod);
    }

    private DefaultFullHttpRequest request(final HttpVersion httpVersion, final HttpMethod httpMethod) {
        return new DefaultFullHttpRequest(httpVersion, httpMethod, "/test", EMPTY_BUFFER);
    }

    private DefaultFullHttpResponse response(final HttpResponseStatus partialContent, CharSequence... headerNameValuePairs) {
        final DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, partialContent, EMPTY_BUFFER, new DefaultHttpHeaders(false), new ReadOnlyHttpHeaders(false));
        response.headers().add(HttpHeaderNames.DATE, DateFormatter.format(new Date()));

        for (int i = 0; i < headerNameValuePairs.length; i += 2) {
            response.headers().add(headerNameValuePairs[i], headerNameValuePairs[i + 1]);
        }

        return response;
    }

}