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

public class CacheControlDirectivesBuilder {
    private EnumSet<CacheControlDirectives.CacheControlFlags> flags =
            EnumSet.noneOf(CacheControlDirectives.CacheControlFlags.class);
    private int maxAge = -1;
    private int sMaxAge = -1;
    private int maxStale = -1;
    private int minFresh = -1;
    private int staleWhileRevalidate = -1;
    private int staleIfError = -1;

    public CacheControlDirectivesBuilder publicCache() {
        flags.add(CacheControlDirectives.CacheControlFlags.PUBLIC);
        return this;
    }

    public CacheControlDirectivesBuilder privateCache() {
        flags.add(CacheControlDirectives.CacheControlFlags.PRIVATE);
        return this;
    }

    public CacheControlDirectivesBuilder noCache() {
        flags.add(CacheControlDirectives.CacheControlFlags.NO_CACHE);
        return this;
    }

    public CacheControlDirectivesBuilder noStore() {
        flags.add(CacheControlDirectives.CacheControlFlags.NO_STORE);
        return this;
    }

    public CacheControlDirectivesBuilder noTransform() {
        flags.add(CacheControlDirectives.CacheControlFlags.NO_TRANSFORM);
        return this;
    }

    public CacheControlDirectivesBuilder mustRevalidate() {
        flags.add(CacheControlDirectives.CacheControlFlags.MUST_REVALIDATE);
        return this;
    }

    public CacheControlDirectivesBuilder proxyRevalidate() {
        flags.add(CacheControlDirectives.CacheControlFlags.PROXY_REVALIDATE);
        return this;
    }

    public CacheControlDirectivesBuilder onlyIfCached() {
        flags.add(CacheControlDirectives.CacheControlFlags.ONLY_IF_CACHED);
        return this;
    }

    public CacheControlDirectivesBuilder immutable() {
        flags.add(CacheControlDirectives.CacheControlFlags.IMMUTABLE);
        return this;
    }

    public CacheControlDirectivesBuilder flags(final EnumSet<CacheControlDirectives.CacheControlFlags> flags) {
        this.flags = EnumSet.copyOf(flags);
        return this;
    }

    public CacheControlDirectivesBuilder maxAge(final int maxAge) {
        this.maxAge = maxAge;
        return this;
    }

    public CacheControlDirectivesBuilder sMaxAge(final int sMaxAge) {
        this.sMaxAge = sMaxAge;
        return this;
    }

    public CacheControlDirectivesBuilder maxStale(final int maxStale) {
        this.maxStale = maxStale;
        return this;
    }

    public CacheControlDirectivesBuilder minFresh(final int minFresh) {
        this.minFresh = minFresh;
        return this;
    }

    public CacheControlDirectivesBuilder staleWhileRevalidate(final int staleWhileRevalidate) {
        this.staleWhileRevalidate = staleWhileRevalidate;
        return this;
    }

    public CacheControlDirectivesBuilder staleIfError(final int staleIfError) {
        this.staleIfError = staleIfError;
        return this;
    }

    public CacheControlDirectives build() {
        return new CacheControlDirectives(flags, maxAge, sMaxAge, maxStale, minFresh, staleWhileRevalidate,
                                          staleIfError);
    }
}
