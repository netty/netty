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

import io.netty.handler.codec.DateFormatter;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.Date;

class HttpCacheEntryChecker {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(HttpCacheEntryChecker.class);

    private final boolean sharedCache;

    HttpCacheEntryChecker(boolean sharedCache) {
        this.sharedCache = sharedCache;
    }

    public static boolean isConditional(HttpRequest request) {
        final HttpHeaders headers = request.headers();
        return headers.contains(HttpHeaderNames.IF_NONE_MATCH) ||
               headers.getTimeMillis(HttpHeaderNames.IF_MODIFIED_SINCE) != null;
    }

    public static boolean allConditionsMatch(HttpRequest request, HttpCacheEntry cacheEntry, Date now) {
        HttpHeaders headers = request.headers();
        boolean hasIfNoneMatchHeader = headers.contains(HttpHeaderNames.IF_NONE_MATCH);
        boolean hasIfModifiedSinceHeader = headers.getTimeMillis(HttpHeaderNames.IF_MODIFIED_SINCE) != null;

        boolean etagMatches = hasIfNoneMatchHeader && etagMatches(request, cacheEntry);
        boolean lastModifiedMatches = hasIfModifiedSinceHeader && ifModifiedMatches(request, cacheEntry, now);

        if (hasIfNoneMatchHeader && hasIfModifiedSinceHeader &&
            !(etagMatches && lastModifiedMatches)) {
            return false;
        }

        if (hasIfNoneMatchHeader && !etagMatches) {
            return false;
        }

        return !hasIfModifiedSinceHeader || lastModifiedMatches;
    }

    private static boolean etagMatches(HttpRequest request, HttpCacheEntry cacheEntry) {
        final String cachedEtag = cacheEntry.getResponseHeaders().get(HttpHeaderNames.ETAG);
        for (String ifNoneMatchHeader : request.headers().getAll(HttpHeaderNames.IF_NONE_MATCH)) {
            if ("*".equals(ifNoneMatchHeader) && cachedEtag != null ||
                ifNoneMatchHeader.equals(cachedEtag)) {
                return true;
            }
        }

        return false;
    }

    private static boolean ifModifiedMatches(HttpRequest request, HttpCacheEntry cacheEntry, Date now) {
        Date lastModified = DateFormatter.parseHttpDate(
                cacheEntry.getResponseHeaders().get(HttpHeaderNames.LAST_MODIFIED));
        if (lastModified == null) {
            return false;
        }

        for (String ifModified : request.headers().getAll(HttpHeaderNames.IF_MODIFIED_SINCE)) {
            Date ifModifiedSince = DateFormatter.parseHttpDate(ifModified);
            if (ifModifiedSince != null) {
                if (ifModifiedSince.after(now) || lastModified.after(ifModifiedSince)) {
                    return false;
                }
            }
        }

        return true;
    }

    private static boolean requestHasUnsupportedHeaders(HttpRequest request) {
        return request.headers().contains(HttpHeaderNames.IF_RANGE) ||
                request.headers().contains(HttpHeaderNames.IF_MATCH) ||
                request.headers().contains(HttpHeaderNames.IF_UNMODIFIED_SINCE);
    }

    public boolean canUseCachedResponse(HttpRequest request, CacheControlDirectives requestCacheControlDirectives,
                                        HttpCacheEntry cacheEntry, Date now) {
        if (!isFreshEnough(requestCacheControlDirectives, cacheEntry, now)) {
            logger.debug("Cache entry is not fresh");
            return false;
        }

        if (requestHasUnsupportedHeaders(request)) {
            logger.debug("Request contains unsupported headers.");
            return false;
        }

        if (!isConditional(request) && cacheEntry.getStatus() == HttpResponseStatus.NOT_MODIFIED) {
            logger.debug("Non-modified cached response can only match conditional request.");
            return false;
        }

        if (isConditional(request) && !allConditionsMatch(request, cacheEntry, now)) {
            logger.debug("Conditional request with non matching conditions, cache entry is not suitable.");
            return false;
        }

        if (requestCacheControlDirectives.noCache()) {
            logger.debug("Request has no-cache directive, cache entry is not suitable.");
            return false;
        }

        if (requestCacheControlDirectives.noStore()) {
            logger.debug("Request has no-store directive, cache entry is not suitable.");
            return false;
        }

        // https://tools.ietf.org/html/rfc7234#section-5.2.1.1
        int requestMaxAge = requestCacheControlDirectives.getMaxAge();
        int requestMaxStale = requestCacheControlDirectives.getMaxStale();
        if (requestMaxAge != -1) {
            int maxAgePlusStale = requestMaxAge + (requestMaxStale != -1 ? requestMaxStale : 0);
            if (cacheEntry.getCurrentAgeInSeconds(now) > maxAgePlusStale) {
                logger.debug("Cache entry was not suitable due to max age.");
                return false;
            }
        }

        if (requestMaxStale != -1) {
            if (cacheEntry.getFreshnessLifetimeInSeconds(sharedCache) > requestMaxStale) {
                logger.debug("Cache entry was not suitable due to max stale freshness.");
                return false;
            }
        }

        int requestMinFresh = requestCacheControlDirectives.getMinFresh();
        if (requestMinFresh != -1) {
            long ageInSeconds = cacheEntry.getCurrentAgeInSeconds(now);
            long freshnessInSeconds = cacheEntry.getFreshnessLifetimeInSeconds(sharedCache);
            if ((freshnessInSeconds - ageInSeconds) < requestMinFresh) {
                logger.debug("Cache entry was not suitable due to min fresh freshness requirement.");
                return false;
            }
        }

        logger.debug("Response from cache can be used.");
        return true;
    }

    private boolean isFreshEnough(CacheControlDirectives requestCacheControlDirectives,
                                  HttpCacheEntry cacheEntry, Date now) {
        if (cacheEntry.isFresh(sharedCache, now)) {
            return true;
        }

        // heuristicaly fresh?

        // stale
        long maxStale = requestCacheControlDirectives.getMaxStale();
        if (maxStale == -1) {
            return false;
        }

        return maxStale > cacheEntry.getStalenessInSeconds(sharedCache, now);
    }
}
