package io.netty.handler.codec.http.cache;

import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.ReadOnlyHttpHeaders;
import org.junit.Test;

import static io.netty.handler.codec.http.HttpHeaderNames.*;
import static io.netty.handler.codec.http.cache.CacheControlDecoder.*;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

public class CacheControlDecoderTest {
    @Test
    public void shouldParsePublicDirective() {
        final HttpHeaders headers = new ReadOnlyHttpHeaders(false, CACHE_CONTROL, HttpHeaderValues.PUBLIC);
        assertThat(decode(headers).isPublic(), is(true));
    }

    @Test
    public void shouldParsePrivateDirective() {
        final HttpHeaders headers = new ReadOnlyHttpHeaders(false, CACHE_CONTROL, HttpHeaderValues.PRIVATE);
        assertThat(decode(headers).isPrivate(), is(true));
    }

    @Test
    public void shouldParseNoCacheDirective() {
        final HttpHeaders headers = new ReadOnlyHttpHeaders(false, CACHE_CONTROL, HttpHeaderValues.NO_CACHE);
        assertThat(decode(headers).noCache(), is(true));
    }

    @Test
    public void shouldParseNoStoreDirective() {
        final HttpHeaders headers = new ReadOnlyHttpHeaders(false, CACHE_CONTROL, HttpHeaderValues.NO_STORE);
        assertThat(decode(headers).noStore(), is(true));
    }

    @Test
    public void shouldParseNoTransformDirective() {
        final HttpHeaders headers = new ReadOnlyHttpHeaders(false, CACHE_CONTROL, HttpHeaderValues.NO_TRANSFORM);
        assertThat(decode(headers).noTransform(), is(true));
    }

    @Test
    public void shouldParseMustRevalidateDirective() {
        final HttpHeaders headers = new ReadOnlyHttpHeaders(false, CACHE_CONTROL, HttpHeaderValues.MUST_REVALIDATE);
        assertThat(decode(headers).mustRevalidate(), is(true));
    }

    @Test
    public void shouldParseProxyRevalidateDirective() {
        final HttpHeaders headers = new ReadOnlyHttpHeaders(false, CACHE_CONTROL, HttpHeaderValues.PROXY_REVALIDATE);
        assertThat(decode(headers).proxyRevalidate(), is(true));
    }

    @Test
    public void shouldParseOnlyIfCachedDirective() {
        final HttpHeaders headers = new ReadOnlyHttpHeaders(false, CACHE_CONTROL, HttpHeaderValues.ONLY_IF_CACHED);
        assertThat(decode(headers).onlyIfCached(), is(true));
    }

    @Test
    public void shouldParseImmutableDirective() {
        final HttpHeaders headers = new ReadOnlyHttpHeaders(false, CACHE_CONTROL, HttpHeaderValues.IMMUTABLE);
        assertThat(decode(headers).immutable(), is(true));
    }

    @Test
    public void shouldParseMaxAgeDirective() {
        final HttpHeaders headers = new ReadOnlyHttpHeaders(false, CACHE_CONTROL, "max-age=10");
        assertThat(decode(headers).getMaxAge(), is(10));
    }

    @Test
    public void shouldParseSMaxAgeDirective() {
        final HttpHeaders headers = new ReadOnlyHttpHeaders(false, CACHE_CONTROL, "s-maxage=10");
        assertThat(decode(headers).getSMaxAge(), is(10));
    }

    @Test
    public void shouldParseMaxStaleDirective() {
        final HttpHeaders headers = new ReadOnlyHttpHeaders(false, CACHE_CONTROL, "max-stale=10");
        assertThat(decode(headers).getMaxStale(), is(10));
    }

    @Test
    public void shouldParseMinFreshDirective() {
        final HttpHeaders headers = new ReadOnlyHttpHeaders(false, CACHE_CONTROL, "min-fresh=10");
        assertThat(decode(headers).getMinFresh(), is(10));
    }

    @Test
    public void shouldParseStaleWhileRevalidateDirective() {
        final HttpHeaders headers = new ReadOnlyHttpHeaders(false, CACHE_CONTROL, "stale-while-revalidate=10");
        assertThat(decode(headers).getStaleWhileRevalidate(), is(10));
    }

    @Test
    public void shouldParseStaleIfErrorDirective() {
        final HttpHeaders headers = new ReadOnlyHttpHeaders(false, CACHE_CONTROL, "stale-if-error=10");
        assertThat(decode(headers).getStaleIfError(), is(10));
    }

    @Test
    public void shouldBeCaseInsensitive() {
        final HttpHeaders headers = new ReadOnlyHttpHeaders(false, CACHE_CONTROL, "PUBLIC, Max-Age=5");
        assertThat(decode(headers).isPublic(), is(true));
        assertThat(decode(headers).getMaxAge(), is(5));
    }

    @Test
    public void shouldDecodeMultipleDirectives() {
        final HttpHeaders headers = new ReadOnlyHttpHeaders(false, CACHE_CONTROL, "public, max-age=5");
        assertThat(decode(headers).isPublic(), is(true));
        assertThat(decode(headers).getMaxAge(), is(5));
    }

    @Test
    public void shouldIgnoreFieldNameForNoCache() {
        final HttpHeaders headers = new ReadOnlyHttpHeaders(false, CACHE_CONTROL, "no-cache=test");
        assertThat(decode(headers).noCache(), is(true));
    }

    @Test
    public void shouldIgnoreFieldNameForPrivate() {
        final HttpHeaders headers = new ReadOnlyHttpHeaders(false, CACHE_CONTROL, "private=test");
        assertThat(decode(headers).isPrivate(), is(true));
    }

    @Test
    public void shouldLimitValueToMaximumAge() {
        final HttpHeaders headers = new ReadOnlyHttpHeaders(false, CACHE_CONTROL, "max-age=" + Long.toString(MAXIMUM_AGE + 100L));
        assertThat(decode(headers).getMaxAge(), is(MAXIMUM_AGE));
    }

    @Test
    public void shouldMergeFromMultipleCacheControlHeaders() {
        final HttpHeaders headers = new ReadOnlyHttpHeaders(false, CACHE_CONTROL, "private", CACHE_CONTROL, "min-fresh=10");
        assertThat(decode(headers).isPrivate(), is(true));
        assertThat(decode(headers).getMinFresh(), is(10));
    }

    @Test
    public void shouldHandleStupidValue() {
        final HttpHeaders headers = new ReadOnlyHttpHeaders(false, CACHE_CONTROL, "aaa=dsadad;bbb=a,b,c;");
        assertThat(decode(headers), is(notNullValue()));
    }
}