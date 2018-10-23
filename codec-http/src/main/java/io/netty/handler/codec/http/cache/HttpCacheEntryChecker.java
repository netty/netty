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

import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.Date;

class HttpCacheEntryChecker {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(HttpCacheEntryChecker.class);

    private final boolean sharedCache;

    HttpCacheEntryChecker(final boolean sharedCache) {
        this.sharedCache = sharedCache;
    }

    public boolean canUseCachedResponse(final CacheControlDirectives requestCacheControlDirectives,
                                        final HttpCacheEntry cacheEntry, final Date now) {
        if (!isFreshEnough(requestCacheControlDirectives, cacheEntry, now)) {
            logger.debug("Cache entry is not fresh");
            return false;
        }

        if (requestCacheControlDirectives.noCache()) {
            logger.debug("Request has no-cache directive, cache entry is not suitable.");
            return false;
        }

        // TODO: Shouldn't happen since we check that before
        if (requestCacheControlDirectives.noStore()) {
            logger.debug("Request has no-store directive, cache entry is not suitable.");
            return false;
        }

        // https://tools.ietf.org/html/rfc7234#section-5.2.1.1
        final int requestMaxAge = requestCacheControlDirectives.getMaxAge();
        final int requestMaxStale = requestCacheControlDirectives.getMaxStale();
        if (requestMaxAge != -1) {
            final int maxAgePlusStale = requestMaxAge + (requestMaxStale != -1 ? requestMaxStale : 0);
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

        final int requestMinFresh = requestCacheControlDirectives.getMinFresh();
        if (requestMinFresh != -1) {
            final long ageInSeconds = cacheEntry.getCurrentAgeInSeconds(now);
            final long freshnessInSeconds = cacheEntry.getFreshnessLifetimeInSeconds(sharedCache);
            if ((freshnessInSeconds - ageInSeconds) < requestMinFresh) {
                logger.debug("Cache entry was not suitable due to min fresh freshness requirement.");
                return false;
            }
        }

        logger.debug("Response from cache can be used.");
        return true;
    }

    private boolean isFreshEnough(final CacheControlDirectives requestCacheControlDirectives,
                                  final HttpCacheEntry cacheEntry, final Date now) {
        if (cacheEntry.isFresh(sharedCache, now)) {
            return true;
        }

        // heuristicaly fresh?

        // stale
        final long maxStale = requestCacheControlDirectives.getMaxStale();
        if (maxStale == -1) {
            return false;
        }

        return maxStale > cacheEntry.getStalenessInSeconds(sharedCache, now);
    }

}
