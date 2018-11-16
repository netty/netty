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

import io.netty.handler.codec.DateFormatter;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
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

import static io.netty.buffer.Unpooled.*;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

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
        assertThat(checker.canUseCachedResponse(request(), CacheControlDirectives.EMPTY,
                                                new HttpCacheEntry(null, tenSecondsAgo, tenSecondsAgo,
                                                                   HttpResponseStatus.OK, headers), now), is(true));
    }

    @Test
    public void notFreshCacheEntryCanNotBeUsed() {
        final HttpHeaders headers = new ReadOnlyHttpHeaders(false,
                                                            HttpHeaderNames.DATE, DateFormatter.format(tenSecondsAgo),
                                                            HttpHeaderNames.CACHE_CONTROL, "max-age=5",
                                                            HttpHeaderNames.CONTENT_LENGTH, "0"
        );
        assertThat(checker.canUseCachedResponse(request(), CacheControlDirectives.EMPTY,
                                                new HttpCacheEntry(null, tenSecondsAgo, tenSecondsAgo,
                                                                   HttpResponseStatus.OK, headers), now), is(false));
    }

    @Test
    public void requestWithNoCacheDirectiveCanNotUsedCacheEntry() {
        final HttpHeaders headers = new ReadOnlyHttpHeaders(false,
                                                            HttpHeaderNames.DATE, DateFormatter.format(tenSecondsAgo),
                                                            HttpHeaderNames.CACHE_CONTROL, "max-age=3600",
                                                            HttpHeaderNames.CONTENT_LENGTH, "0"
        );
        assertThat(checker.canUseCachedResponse(request(), CacheControlDirectives.builder().noCache().build(),
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
        assertThat(checker.canUseCachedResponse(request(), CacheControlDirectives.builder().noStore().build(),
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
        assertThat(checker.canUseCachedResponse(request(), CacheControlDirectives.builder().maxAge(5).build(),
                                                new HttpCacheEntry(null, tenSecondsAgo, tenSecondsAgo,
                                                                   HttpResponseStatus.OK, headers), now), is(false));
    }

    @Test
    public void cacheEntryExceedingMaxStaleCanNotUsedCacheEntry() {
        final HttpHeaders headers = new ReadOnlyHttpHeaders(false,
                                                            HttpHeaderNames.DATE, DateFormatter.format(tenSecondsAgo),
                                                            HttpHeaderNames.CACHE_CONTROL, "max-age=15",
                                                            HttpHeaderNames.CONTENT_LENGTH, "0"
        );
        assertThat(checker.canUseCachedResponse(request(), CacheControlDirectives.builder().maxStale(6).build(),
                                                new HttpCacheEntry(null, tenSecondsAgo, tenSecondsAgo,
                                                                   HttpResponseStatus.OK, headers), now), is(false));
    }

    @Test
    public void cacheEntryNotMeetingMinFreshRequirementCanNotUsedCacheEntry() {
        final HttpHeaders headers = new ReadOnlyHttpHeaders(false,
                                                            HttpHeaderNames.DATE, DateFormatter.format(tenSecondsAgo),
                                                            HttpHeaderNames.CACHE_CONTROL, "max-age=15",
                                                            HttpHeaderNames.CONTENT_LENGTH, "0"
        );
        assertThat(checker.canUseCachedResponse(request(), CacheControlDirectives.builder().minFresh(20).build(),
                                                new HttpCacheEntry(null, tenSecondsAgo, tenSecondsAgo,
                                                                   HttpResponseStatus.OK, headers), now), is(false));
    }

    @Test
    public void conditionalRequestCanNotUsedMismatchedCacheEntryEtag() {
        final HttpHeaders headers = new ReadOnlyHttpHeaders(false,
                                                            HttpHeaderNames.DATE, DateFormatter.format(tenSecondsAgo),
                                                            HttpHeaderNames.CACHE_CONTROL, "max-age=3600",
                                                            HttpHeaderNames.ETAG, "etagValue"
        );
        assertThat(checker.canUseCachedResponse(request(HttpHeaderNames.IF_NONE_MATCH, "oldEtagValue"),
                                                CacheControlDirectives.builder().build(),
                                                new HttpCacheEntry(null, tenSecondsAgo, tenSecondsAgo,
                                                                   HttpResponseStatus.OK, headers), now), is(false));
    }

    @Test
    public void conditionalRequestCanNotUsedMismatchedCacheEntryIfModified() {
        final HttpHeaders headers = new ReadOnlyHttpHeaders(false,
                                                            HttpHeaderNames.DATE, DateFormatter.format(tenSecondsAgo),
                                                            HttpHeaderNames.CACHE_CONTROL, "max-age=3600",
                                                            HttpHeaderNames.LAST_MODIFIED,
                                                            DateFormatter.format(tenSecondsAgo)
        );
        assertThat(checker.canUseCachedResponse(
                request(HttpHeaderNames.IF_MODIFIED_SINCE, DateFormatter.format(twentySecondsAgo)),
                CacheControlDirectives.builder().build(),
                new HttpCacheEntry(null, tenSecondsAgo, tenSecondsAgo,
                                   HttpResponseStatus.OK, headers), now), is(false));
    }

    @Test
    public void requestWithUnsupportedHeaderCanNotBeUsed() {
        final HttpHeaders headers = new ReadOnlyHttpHeaders(false,
                                                            HttpHeaderNames.DATE, DateFormatter.format(tenSecondsAgo),
                                                            HttpHeaderNames.CACHE_CONTROL, "max-age=3600",
                                                            HttpHeaderNames.CONTENT_LENGTH, "0"
        );
        assertThat(checker.canUseCachedResponse(request(HttpHeaderNames.IF_RANGE, "rangeValue"),
                                                CacheControlDirectives.EMPTY,
                                                new HttpCacheEntry(null, tenSecondsAgo, tenSecondsAgo,
                                                                   HttpResponseStatus.OK, headers), now), is(false));

        assertThat(checker.canUseCachedResponse(request(HttpHeaderNames.IF_MATCH, "etag"), CacheControlDirectives.EMPTY,
                                                new HttpCacheEntry(null, tenSecondsAgo, tenSecondsAgo,
                                                                   HttpResponseStatus.OK, headers), now), is(false));

        assertThat(checker.canUseCachedResponse(
                request(HttpHeaderNames.IF_UNMODIFIED_SINCE, DateFormatter.format(tenSecondsAgo)),
                CacheControlDirectives.EMPTY,
                new HttpCacheEntry(null, tenSecondsAgo, tenSecondsAgo,
                                   HttpResponseStatus.OK, headers), now), is(false));
    }

    private DefaultFullHttpRequest request(CharSequence... headerNameValuePairs) {
        return new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/test", EMPTY_BUFFER,
                                          new ReadOnlyHttpHeaders(false, headerNameValuePairs),
                                          new ReadOnlyHttpHeaders(false));
    }
}
