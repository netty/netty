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
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.ReadOnlyHttpHeaders;
import org.junit.Before;
import org.junit.Test;

import java.util.Date;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

public class HttpCacheEntryTest {
    private Date now;
    private Date tenSecondsAgo;
    private Date fifteenSecondsAgo;
    private Date twentySecondsAgo;

    private HttpResponseStatus status = HttpResponseStatus.OK;

    @Before
    public void setUp() {
        now = new Date();
        tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);
        fifteenSecondsAgo = new Date(now.getTime() - 15 * 1000L);
        twentySecondsAgo = new Date(now.getTime() - 20 * 1000L);
    }

    @Test
    public void shouldReturnSMaxAgeIfDefined() {
        final HttpHeaders headers = headers(
                HttpHeaderNames.DATE, DateFormatter.format(new Date()),
                HttpHeaderNames.CACHE_CONTROL, "s-maxage=1234567"
        );
        final HttpCacheEntry cacheEntry = new HttpCacheEntry(null, fifteenSecondsAgo, new Date(), status, headers);

        assertThat(cacheEntry.getSMaxAge(), is(1234567L));
    }

    @Test
    public void shouldBeFreshIfFreshnessLifetimeGreaterThanCurrentAge() {
        final HttpCacheEntry cacheEntry = new HttpCacheEntry(null, tenSecondsAgo, tenSecondsAgo,
                                                             status,
                                                             headers(HttpHeaderNames.CACHE_CONTROL, "max-age=20"));
        assertThat(cacheEntry.isFresh(false, now), is(true));
    }

    @Test
    public void getFreshnessLifetimeShouldUseSMaxAgeIfDefinedAndCacheIsShared() {
        final HttpCacheEntry cacheEntry = new HttpCacheEntry(null, tenSecondsAgo, tenSecondsAgo,
                                                             status,
                                                             headers(HttpHeaderNames.CACHE_CONTROL, "s-maxage=5"));
        assertThat(cacheEntry.getFreshnessLifetimeInSeconds(true), is(5L));
    }

    @Test
    public void getFreshnessLifetimeShouldNotUseSMaxAgeIfDefinedButCacheIsNotShared() {
        final HttpCacheEntry cacheEntry = new HttpCacheEntry(null, tenSecondsAgo, tenSecondsAgo,
                                                             status,
                                                             headers(HttpHeaderNames.CACHE_CONTROL, "s-maxage=5"));
        assertThat(cacheEntry.getFreshnessLifetimeInSeconds(false), is(0L));
    }

    @Test
    public void getFreshnessLifetimeShouldUseMaxAgeIfDefined() {
        final HttpCacheEntry cacheEntry = new HttpCacheEntry(null, tenSecondsAgo, tenSecondsAgo,
                                                             status,
                                                             headers(HttpHeaderNames.CACHE_CONTROL, "max-age=5"));
        assertThat(cacheEntry.getFreshnessLifetimeInSeconds(false), is(5L));
    }

    @Test
    public void getFreshnessLifetimeShouldUseExpireIfDefined() {
        final HttpCacheEntry cacheEntry = new HttpCacheEntry(null, twentySecondsAgo, twentySecondsAgo,
                                                             status, headers(HttpHeaderNames.DATE,
                                                                             DateFormatter.format(twentySecondsAgo),
                                                                             HttpHeaderNames.EXPIRES,
                                                                             DateFormatter.format(tenSecondsAgo)));
        assertThat(cacheEntry.getFreshnessLifetimeInSeconds(false), is(10L));
    }

    @Test
    public void getFreshnessLifetimeShouldReturnZeroHasDefault() {
        final HttpCacheEntry cacheEntry = new HttpCacheEntry(null, twentySecondsAgo, twentySecondsAgo,
                                                             status, headers(HttpHeaderNames.DATE,
                                                                             DateFormatter.format(twentySecondsAgo)));
        assertThat(cacheEntry.getFreshnessLifetimeInSeconds(false), is(0L));
    }

    @Test
    public void getCurrentAgeShouldBeSumOfCorrectedInitialAgeAndResidentTime() {
        final HttpCacheEntry cacheEntry = new HttpCacheEntry(null, tenSecondsAgo, tenSecondsAgo,
                                                             status, headers(HttpHeaderNames.DATE,
                                                                             DateFormatter.format(twentySecondsAgo)));
        assertThat(cacheEntry.getCurrentAgeInSeconds(now), is(cacheEntry.getResidentTimeInSeconds(now) +
                                                              cacheEntry.getCorrectedInitialAgeInSeconds()));
    }

    @Test
    public void getAgeShouldReturnAgeDefinedInHeader() {
        final HttpCacheEntry cacheEntry = new HttpCacheEntry(null, tenSecondsAgo, tenSecondsAgo,
                                                             status, headers(HttpHeaderNames.AGE, "5"));
        assertThat(cacheEntry.getAgeInSeconds(), is(5L));
    }

    @Test
    public void getAgeShouldReturnZeroIfAgeNotDefinedInHeader() {
        final HttpCacheEntry biggerApparentAgeCacheEntry =
                new HttpCacheEntry(null, tenSecondsAgo, tenSecondsAgo, status, headers());
        assertThat(biggerApparentAgeCacheEntry.getAgeInSeconds(), is(0L));
    }

    @Test
    public void getAgeShouldReturnZeroIfAgeCantBeParsed() {
        final HttpCacheEntry biggerApparentAgeCacheEntry =
                new HttpCacheEntry(null, tenSecondsAgo, tenSecondsAgo, status,
                                   headers(HttpHeaderNames.AGE, "hello"));
        assertThat(biggerApparentAgeCacheEntry.getAgeInSeconds(), is(0L));
    }

    @Test
    public void getCorrectedInitialAgeInSecondsShouldBeMaxBetweenApparentAndCorrectedAge() {
        final HttpCacheEntry biggerApparentAgeCacheEntry =
                new HttpCacheEntry(null, tenSecondsAgo, tenSecondsAgo,
                                   status, headers(HttpHeaderNames.DATE,
                                                   DateFormatter
                                                           .format(twentySecondsAgo)));
        assertThat(biggerApparentAgeCacheEntry.getCorrectedInitialAgeInSeconds(), is(10L));

        final HttpCacheEntry biggerCorrectedAge =
                new HttpCacheEntry(null, tenSecondsAgo, tenSecondsAgo,
                                   status, headers(HttpHeaderNames.AGE, "20"));
        assertThat(biggerCorrectedAge.getCorrectedInitialAgeInSeconds(), is(20L));
    }

    @Test
    public void getResidentTimeInSecondsShouldBeDifferenceBetweenResponseTimeAndNow() {
        final HttpCacheEntry cacheEntry =
                new HttpCacheEntry(null, twentySecondsAgo, twentySecondsAgo, status, headers());
        assertThat(cacheEntry.getResidentTimeInSeconds(now), is(20L));
    }

    @Test
    public void getApparentAgeShouldBeZeroIfDateHeaderNotProvided() {
        final HttpCacheEntry cacheEntry =
                new HttpCacheEntry(null, twentySecondsAgo, twentySecondsAgo, status, headers());
        assertThat(cacheEntry.getApparentAgeInSeconds(), is(0L));
    }

    @Test
    public void getApparentAgeShouldNotBeNegative() {
        final HttpCacheEntry cacheEntry = new HttpCacheEntry(null, twentySecondsAgo, twentySecondsAgo,
                                                             status, headers(HttpHeaderNames.DATE,
                                                                             DateFormatter.format(fifteenSecondsAgo)));
        assertThat(cacheEntry.getApparentAgeInSeconds(), is(0L));
    }

    @Test
    public void getApparentAgeShouldBeDifferenceBetweenResponseTimeAndDateHeader() {
        final HttpCacheEntry cacheEntry = new HttpCacheEntry(null, twentySecondsAgo, fifteenSecondsAgo,
                                                             status, headers(HttpHeaderNames.DATE,
                                                                             DateFormatter.format(twentySecondsAgo)));
        assertThat(cacheEntry.getApparentAgeInSeconds(), is(5L));
    }

    @Test
    public void getCorrectedAgeInSecondsShouldIncludeResponseTime() {
        final HttpCacheEntry cacheEntry =
                new HttpCacheEntry(null, twentySecondsAgo, fifteenSecondsAgo, status, headers());
        assertThat(cacheEntry.getCorrectedAgeInSeconds(), is(5L));
    }

    @Test
    public void getCorrectedAgeInSecondsShouldAddResponseTimeToExistingAgeHeader() {
        final HttpCacheEntry cacheEntry = new HttpCacheEntry(null, twentySecondsAgo, fifteenSecondsAgo, status,
                                                             headers(HttpHeaderNames.AGE, "5"));
        assertThat(cacheEntry.getCorrectedAgeInSeconds(), is(10L));
    }

    @Test
    public void getStalenessInSecondsShouldNotBeNegative() {
        final HttpCacheEntry cacheEntry = new HttpCacheEntry(null, now, now, status,
                                                             headers(HttpHeaderNames.AGE, "5",
                                                                     HttpHeaderNames.CACHE_CONTROL, "max-age=6"));
        assertThat(cacheEntry.getStalenessInSeconds(false, now), is(0L));
    }

    @Test
    public void getStalenessInSecondsShouldAgeMinusFreshness() {
        final HttpCacheEntry cacheEntry = new HttpCacheEntry(null, now, now, status,
                                                             headers(HttpHeaderNames.AGE, "5",
                                                                     HttpHeaderNames.CACHE_CONTROL, "max-age=3"));
        assertThat(cacheEntry.getStalenessInSeconds(false, now), is(2L));
    }

    private HttpHeaders headers(CharSequence... headerNameValuePairs) {
        return new ReadOnlyHttpHeaders(false, headerNameValuePairs);
    }
}
