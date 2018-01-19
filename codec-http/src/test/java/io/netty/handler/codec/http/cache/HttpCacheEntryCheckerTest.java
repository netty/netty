package io.netty.handler.codec.http.cache;

import io.netty.handler.codec.DateFormatter;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
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
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class HttpCacheEntryCheckerTest {
    @Rule
    public MockitoRule rule = MockitoJUnit.rule();

    private Date now;
    private Date tenSecondsAgo;
    private Date fifteenSecondsAgo;
    private Date twentySecondsAgo;

    private HttpCacheEntryChecker checker;

    @Before
    public void setUp() {
        now = new Date();
        tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);
        fifteenSecondsAgo = new Date(now.getTime() - 15 * 1000L);
        twentySecondsAgo = new Date(now.getTime() - 20 * 1000L);

        checker = new HttpCacheEntryChecker(false);
    }

    @Test
    public void freshCacheEntryCanBeUsed() {
        final HttpHeaders headers = new ReadOnlyHttpHeaders(false,
                HttpHeaderNames.DATE, DateFormatter.format(tenSecondsAgo),
                HttpHeaderNames.CACHE_CONTROL, "max-age=3600",
                HttpHeaderNames.CONTENT_LENGTH, "0"
        );
        assertThat(checker.canUseCachedResponse(CacheControlDirectives.EMPTY, new HttpCacheEntry(null, tenSecondsAgo, tenSecondsAgo,
                HttpResponseStatus.OK, headers), now), is(true));
    }

    @Test
    public void notFreshCacheEntryCanNotBeUsed() {
        final HttpHeaders headers = new ReadOnlyHttpHeaders(false,
                HttpHeaderNames.DATE, DateFormatter.format(tenSecondsAgo),
                HttpHeaderNames.CACHE_CONTROL, "max-age=5",
                HttpHeaderNames.CONTENT_LENGTH, "0"
        );
        assertThat(checker.canUseCachedResponse(CacheControlDirectives.EMPTY, new HttpCacheEntry(null, tenSecondsAgo, tenSecondsAgo,
                HttpResponseStatus.OK, headers), now), is(false));
    }

    @Test
    public void requestWithNoCacheDirectiveCanNotUsedCacheEntry() {
        final HttpHeaders headers = new ReadOnlyHttpHeaders(false,
                HttpHeaderNames.DATE, DateFormatter.format(tenSecondsAgo),
                HttpHeaderNames.CACHE_CONTROL, "max-age=3600",
                HttpHeaderNames.CONTENT_LENGTH, "0"
        );
        assertThat(checker.canUseCachedResponse(CacheControlDirectives.builder().noCache().build(),
                new HttpCacheEntry(null, tenSecondsAgo, tenSecondsAgo,
                HttpResponseStatus.OK, headers), now), is(false));
    }

    @Test
    public void requestWithNoStoreDirectiveCanNotUsedCacheEntry() {
        final HttpHeaders headers = new ReadOnlyHttpHeaders(false,
                HttpHeaderNames.DATE, DateFormatter.format(tenSecondsAgo),
                HttpHeaderNames.CACHE_CONTROL, "max-age=3600",
                HttpHeaderNames.CONTENT_LENGTH, "0"
        );
        assertThat(checker.canUseCachedResponse(CacheControlDirectives.builder().noStore().build(),
                new HttpCacheEntry(null, tenSecondsAgo, tenSecondsAgo,
                HttpResponseStatus.OK, headers), now), is(false));
    }

    @Test
    public void cacheEntryExceedingMaxAgeCanNotUsedCacheEntry() {
        final HttpHeaders headers = new ReadOnlyHttpHeaders(false,
                HttpHeaderNames.DATE, DateFormatter.format(tenSecondsAgo),
                HttpHeaderNames.CACHE_CONTROL, "max-age=3600",
                HttpHeaderNames.CONTENT_LENGTH, "0"
        );
        assertThat(checker.canUseCachedResponse(CacheControlDirectives.builder().maxAge(5).build(),
                new HttpCacheEntry(null, tenSecondsAgo, tenSecondsAgo, HttpResponseStatus.OK, headers), now), is(false));
    }

    @Test
    public void cacheEntryExceedingMaxStaleCanNotUsedCacheEntry() {
        final HttpHeaders headers = new ReadOnlyHttpHeaders(false,
                HttpHeaderNames.DATE, DateFormatter.format(tenSecondsAgo),
                HttpHeaderNames.CACHE_CONTROL, "max-age=15",
                HttpHeaderNames.CONTENT_LENGTH, "0"
        );
        assertThat(checker.canUseCachedResponse(CacheControlDirectives.builder().maxStale(6).build(),
                new HttpCacheEntry(null, tenSecondsAgo, tenSecondsAgo, HttpResponseStatus.OK, headers), now), is(false));
    }

    @Test
    public void cacheEntryNotMeetingMinFreshRequirementCanNotUsedCacheEntry() {
        final HttpHeaders headers = new ReadOnlyHttpHeaders(false,
                HttpHeaderNames.DATE, DateFormatter.format(tenSecondsAgo),
                HttpHeaderNames.CACHE_CONTROL, "max-age=15",
                HttpHeaderNames.CONTENT_LENGTH, "0"
        );
        assertThat(checker.canUseCachedResponse(CacheControlDirectives.builder().minFresh(20).build(),
                new HttpCacheEntry(null, tenSecondsAgo, tenSecondsAgo, HttpResponseStatus.OK, headers), now), is(false));
    }

    private DefaultFullHttpRequest request(CharSequence... headerNameValuePairs) {
        return new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/test", EMPTY_BUFFER,
                new ReadOnlyHttpHeaders(false, headerNameValuePairs), new ReadOnlyHttpHeaders(false));
    }
}