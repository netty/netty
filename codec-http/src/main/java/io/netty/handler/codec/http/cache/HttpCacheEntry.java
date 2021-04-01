/*
 * Copyright 2018 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.http.cache;

import io.netty.buffer.ByteBufHolder;
import io.netty.handler.codec.DateFormatter;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.internal.UnstableApi;

import java.io.Serializable;
import java.util.Date;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

@UnstableApi
public class HttpCacheEntry implements Serializable {
    private static final long serialVersionUID = -7024260271684161731L;

    private final ByteBufHolder content;

    private final Date requestDate;
    private final Date responseDate;
    private final HttpResponseStatus status;
    private final HttpHeaders responseHeaders;
    private final Date date;
    private final CacheControlDirectives responseCacheControlDirectives;

    public HttpCacheEntry(final ByteBufHolder content,
                          final Date requestDate,
                          final Date responseDate,
                          final HttpResponseStatus status, final HttpHeaders responseHeaders) {
        this.content = content;
        this.requestDate = requestDate;
        this.responseDate = checkNotNull(responseDate, "responseDate");
        this.status = checkNotNull(status, "status");
        this.responseHeaders = responseHeaders;
        this.responseCacheControlDirectives = CacheControlDecoder.decode(responseHeaders);

        final String dateString = responseHeaders.get(HttpHeaderNames.DATE);
        this.date = dateString != null ? DateFormatter.parseHttpDate(dateString) : null;
    }

    public ByteBufHolder getContent() {
        return content;
    }

    public HttpHeaders getResponseHeaders() {
        return responseHeaders;
    }

    public HttpResponseStatus getStatus() {
        return status;
    }

    public Date getRequestDate() {
        return requestDate;
    }

    public Date getResponseDate() {
        return responseDate;
    }

    public Date getDate() {
        return date;
    }

    public long getAgeInSeconds() {
        final String age = responseHeaders.get(HttpHeaderNames.AGE);
        if (age != null) {
            try {
                return Long.parseLong(age);
            } catch (NumberFormatException e) {
                return 0L;
            }
        }
        return 0L;
    }

    public long getSMaxAge() {
        return responseCacheControlDirectives.getSMaxAge();
    }

    public long getMaxAge() {
        return responseCacheControlDirectives.getMaxAge();
    }

    public long getStalenessInSeconds(final boolean shared, final Date now) {
        final long age = getCurrentAgeInSeconds(now);
        final long freshness = getFreshnessLifetimeInSeconds(shared);
        return Math.max(0L, age - freshness);
    }

    /**
     * @see <a href="https://tools.ietf.org/html/rfc7234#section-4.2">rfc7234#section-4.2</a>
     */
    boolean isFresh(final boolean sharedCache, final Date now) {
        return getFreshnessLifetimeInSeconds(sharedCache) > getCurrentAgeInSeconds(now);
    }

    /**
     * @see <a href="https://tools.ietf.org/html/rfc7234#section-4.2.1">rfc7234#section-4.2.1</a>
     */
    long getFreshnessLifetimeInSeconds(final boolean sharedCache) {
        final long sMaxAge = getSMaxAge();
        if (sharedCache && sMaxAge != -1L) {
            return sMaxAge;
        }

        final long maxAge = responseCacheControlDirectives.getMaxAge();
        if (maxAge != -1L) {
            return maxAge;
        }

        final Date date = getDate();
        if (date == null) {
            return 0L;
        }

        final long expires = responseHeaders.getTimeMillis(HttpHeaderNames.EXPIRES, -1L);
        if (expires != -1L) {
            return (expires - date.getTime()) / 1000L;
        }

        return 0L;
    }

    /**
     * @see <a href="https://tools.ietf.org/html/rfc7234#section-4.2.3">rfc7234#section-4.2.3</a>
     */
    long getCurrentAgeInSeconds(final Date now) {
        return getCorrectedInitialAgeInSeconds() + getResidentTimeInSeconds(now);
    }

    /**
     * @see <a href="https://tools.ietf.org/html/rfc7234#section-4.2.3">rfc7234#section-4.2.3</a>
     */
    long getCorrectedInitialAgeInSeconds() {
        return Math.max(getApparentAgeInSeconds(), getCorrectedAgeInSeconds());
    }

    /**
     * @see <a href="https://tools.ietf.org/html/rfc7234#section-4.2.3">rfc7234#section-4.2.3</a>
     */
    long getResidentTimeInSeconds(final Date now) {
        return (now.getTime() - responseDate.getTime()) / 1000L;
    }

    long getApparentAgeInSeconds() {
        if (date == null) {
            return 0L;
        }

        return Math.max(0L, (responseDate.getTime() - date.getTime()) / 1000L);
    }

    long getCorrectedAgeInSeconds() {
        long responseDelayInSeconds = (responseDate.getTime() - requestDate.getTime()) / 1000L;
        return getAgeInSeconds() + responseDelayInSeconds;
    }

    boolean mustRevalidate() {
        return responseCacheControlDirectives.mustRevalidate();
    }

    boolean proxyRevalidate() {
        return responseCacheControlDirectives.proxyRevalidate();
    }

    CacheControlDirectives getCacheControlDirectives() {
        return responseCacheControlDirectives;
    }
}
