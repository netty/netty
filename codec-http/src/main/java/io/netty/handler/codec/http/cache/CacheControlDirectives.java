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

import java.util.EnumSet;

class CacheControlDirectives {
    public static final CacheControlDirectives EMPTY = builder().build();

    private final EnumSet<CacheControlFlags> flags;
    private final int maxAge;
    private final int sMaxAge;
    private final int maxStale;
    private final int minFresh;
    private final int staleWhileRevalidate;
    private final int staleIfError;

    public static CacheControlDirectivesBuilder builder() {
        return new CacheControlDirectivesBuilder();
    }

    public CacheControlDirectives(final EnumSet<CacheControlFlags> flags, final int maxAge, final int sMaxAge,
                                  final int maxStale, final int minFresh, final int staleWhileRevalidate,
                                  final int staleIfError) {
        this.flags = EnumSet.copyOf(flags);
        this.maxAge = maxAge;
        this.sMaxAge = sMaxAge;
        this.maxStale = maxStale;
        this.minFresh = minFresh;
        this.staleWhileRevalidate = staleWhileRevalidate;
        this.staleIfError = staleIfError;
    }

    public boolean noCache() {
        return flags.contains(CacheControlFlags.NO_CACHE);
    }

    public boolean noStore() {
        return flags.contains(CacheControlFlags.NO_STORE);
    }

    public boolean noTransform() {
        return flags.contains(CacheControlFlags.NO_TRANSFORM);
    }

    public boolean mustRevalidate() {
        return flags.contains(CacheControlFlags.MUST_REVALIDATE);
    }

    public boolean proxyRevalidate() {
        return flags.contains(CacheControlFlags.PROXY_REVALIDATE);
    }

    public boolean onlyIfCached() {
        return flags.contains(CacheControlFlags.ONLY_IF_CACHED);
    }

    public boolean immutable() {
        return flags.contains(CacheControlFlags.IMMUTABLE);
    }

    public boolean isPrivate() {
        return flags.contains(CacheControlFlags.PRIVATE);
    }

    public boolean isPublic() {
        return flags.contains(CacheControlFlags.PUBLIC);
    }

    public int getMaxAge() {
        return maxAge;
    }

    public int getSMaxAge() {
        return sMaxAge;
    }

    public int getMaxStale() {
        return maxStale;
    }

    public int getMinFresh() {
        return minFresh;
    }

    public int getStaleWhileRevalidate() {
        return staleWhileRevalidate;
    }

    public int getStaleIfError() {
        return staleIfError;
    }

    enum CacheControlFlags {
        PUBLIC,
        PRIVATE,
        NO_CACHE,
        NO_STORE,
        NO_TRANSFORM,
        MUST_REVALIDATE,
        PROXY_REVALIDATE,
        ONLY_IF_CACHED,
        IMMUTABLE
    }
}
