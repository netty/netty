package io.netty.handler.codec.http.cache;

import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.ReadOnlyHttpHeaders;
import org.junit.Test;

import static io.netty.handler.codec.http.HttpHeaderNames.CACHE_CONTROL;
import static io.netty.handler.codec.http.cache.CacheControlDecoder.MAXIMUM_AGE;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class CacheControlDecoderTest {
    @Test
    public void shouldParsePublicDirective() {
        final HttpHeaders headers = new ReadOnlyHttpHeaders(false, CACHE_CONTROL, HttpHeaderValues.PUBLIC);
        assertThat(CacheControlDecoder.decode(headers).isPublic(), is(true));
    }

    @Test
    public void shouldParsePrivateDirective() {
        final HttpHeaders headers = new ReadOnlyHttpHeaders(false, CACHE_CONTROL, HttpHeaderValues.PRIVATE);
        assertThat(CacheControlDecoder.decode(headers).isPrivate(), is(true));
    }

    @Test
    public void shouldParseNoCacheDirective() {
        final HttpHeaders headers = new ReadOnlyHttpHeaders(false, CACHE_CONTROL, HttpHeaderValues.NO_CACHE);
        assertThat(CacheControlDecoder.decode(headers).noCache(), is(true));
    }

    @Test
    public void shouldParseNoStoreDirective() {
        final HttpHeaders headers = new ReadOnlyHttpHeaders(false, CACHE_CONTROL, HttpHeaderValues.NO_STORE);
        assertThat(CacheControlDecoder.decode(headers).noStore(), is(true));
    }

    @Test
    public void shouldParseNoTransformDirective() {
        final HttpHeaders headers = new ReadOnlyHttpHeaders(false, CACHE_CONTROL, HttpHeaderValues.NO_TRANSFORM);
        assertThat(CacheControlDecoder.decode(headers).noTransform(), is(true));
    }

    @Test
    public void shouldParseMustRevalidateDirective() {
        final HttpHeaders headers = new ReadOnlyHttpHeaders(false, CACHE_CONTROL, HttpHeaderValues.MUST_REVALIDATE);
        assertThat(CacheControlDecoder.decode(headers).mustRevalidate(), is(true));
    }

    @Test
    public void shouldParseProxyRevalidateDirective() {
        final HttpHeaders headers = new ReadOnlyHttpHeaders(false, CACHE_CONTROL, HttpHeaderValues.PROXY_REVALIDATE);
        assertThat(CacheControlDecoder.decode(headers).proxyRevalidate(), is(true));
    }

    @Test
    public void shouldParseOnlyIfCachedDirective() {
        final HttpHeaders headers = new ReadOnlyHttpHeaders(false, CACHE_CONTROL, HttpHeaderValues.ONLY_IF_CACHED);
        assertThat(CacheControlDecoder.decode(headers).onlyIfCached(), is(true));
    }

    @Test
    public void shouldParseImmutableDirective() {
        final HttpHeaders headers = new ReadOnlyHttpHeaders(false, CACHE_CONTROL, HttpHeaderValues.IMMUTABLE);
        assertThat(CacheControlDecoder.decode(headers).immutable(), is(true));
    }

    @Test
    public void shouldParseMaxAgeDirective() {
        final HttpHeaders headers = new ReadOnlyHttpHeaders(false, CACHE_CONTROL, "max-age=10");
        assertThat(CacheControlDecoder.decode(headers).getMaxAge(), is(10));
    }

    @Test
    public void shouldParseSMaxAgeDirective() {
        final HttpHeaders headers = new ReadOnlyHttpHeaders(false, CACHE_CONTROL, "s-maxage=10");
        assertThat(CacheControlDecoder.decode(headers).getSMaxAge(), is(10));
    }

    @Test
    public void shouldParseMaxStaleDirective() {
        final HttpHeaders headers = new ReadOnlyHttpHeaders(false, CACHE_CONTROL, "max-stale=10");
        assertThat(CacheControlDecoder.decode(headers).getMaxStale(), is(10));
    }

    @Test
    public void shouldParseMinFreshDirective() {
        final HttpHeaders headers = new ReadOnlyHttpHeaders(false, CACHE_CONTROL, "min-fresh=10");
        assertThat(CacheControlDecoder.decode(headers).getMinFresh(), is(10));
    }

    @Test
    public void shouldParseStaleWhileRevalidateDirective() {
        final HttpHeaders headers = new ReadOnlyHttpHeaders(false, CACHE_CONTROL, "stale-while-revalidate=10");
        assertThat(CacheControlDecoder.decode(headers).getStaleWhileRevalidate(), is(10));
    }

    @Test
    public void shouldParseStaleIfErrorDirective() {
        final HttpHeaders headers = new ReadOnlyHttpHeaders(false, CACHE_CONTROL, "stale-if-error=10");
        assertThat(CacheControlDecoder.decode(headers).getStaleIfError(), is(10));
    }

    @Test
    public void shouldBeCaseInsensitive() {
        final HttpHeaders headers = new ReadOnlyHttpHeaders(false, CACHE_CONTROL, "PUBLIC, Max-Age=5");
        assertThat(CacheControlDecoder.decode(headers).isPublic(), is(true));
        assertThat(CacheControlDecoder.decode(headers).getMaxAge(), is(5));
    }

    @Test
    public void shouldDecodeMultipleDirectives() {
        final HttpHeaders headers = new ReadOnlyHttpHeaders(false, CACHE_CONTROL, "public, max-age=5");
        assertThat(CacheControlDecoder.decode(headers).isPublic(), is(true));
        assertThat(CacheControlDecoder.decode(headers).getMaxAge(), is(5));
    }

    @Test
    public void shouldIgnoreFieldNameForNoCache() {
        final HttpHeaders headers = new ReadOnlyHttpHeaders(false, CACHE_CONTROL, "no-cache=test");
        assertThat(CacheControlDecoder.decode(headers).noCache(), is(true));
    }

    @Test
    public void shouldIgnoreFieldNameForPrivate() {
        final HttpHeaders headers = new ReadOnlyHttpHeaders(false, CACHE_CONTROL, "private=test");
        assertThat(CacheControlDecoder.decode(headers).isPrivate(), is(true));
    }

    @Test
    public void shouldLimitValueToMaximumAge() {
        final HttpHeaders headers = new ReadOnlyHttpHeaders(false, CACHE_CONTROL, "max-age=" + Long.toString(MAXIMUM_AGE + 100L));
        assertThat(CacheControlDecoder.decode(headers).getMaxAge(), is(MAXIMUM_AGE));
    }

    @Test
    public void shouldMergeFromMultipleCacheControlHeaders() {
        final HttpHeaders headers = new ReadOnlyHttpHeaders(false, CACHE_CONTROL, "private", CACHE_CONTROL, "min-fresh=10");
        assertThat(CacheControlDecoder.decode(headers).isPrivate(), is(true));
        assertThat(CacheControlDecoder.decode(headers).getMinFresh(), is(10));
    }
}