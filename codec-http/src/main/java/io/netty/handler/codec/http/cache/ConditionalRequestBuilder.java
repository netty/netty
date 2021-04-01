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

import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpRequest;

import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;

public class ConditionalRequestBuilder {

    private static final Set<CharSequence> UNCONDITIONAL_EXCLUDED_HEADERS;

    static {
        UNCONDITIONAL_EXCLUDED_HEADERS = new HashSet<CharSequence>();
        UNCONDITIONAL_EXCLUDED_HEADERS.add(HttpHeaderNames.IF_RANGE);
        UNCONDITIONAL_EXCLUDED_HEADERS.add(HttpHeaderNames.IF_MATCH);
        UNCONDITIONAL_EXCLUDED_HEADERS.add(HttpHeaderNames.IF_NONE_MATCH);
        UNCONDITIONAL_EXCLUDED_HEADERS.add(HttpHeaderNames.IF_UNMODIFIED_SINCE);
        UNCONDITIONAL_EXCLUDED_HEADERS.add(HttpHeaderNames.IF_MODIFIED_SINCE);
    }

    HttpRequest conditionalRequest(HttpRequest request, HttpCacheEntry cacheEntry) {
        final DefaultHttpHeaders copiedHeaders = new DefaultHttpHeaders();
        for (Entry<String, String> header : request.headers()) {
            copiedHeaders.add(header.getKey(), header.getValue());
        }

        final HttpRequest newRequest =
                new DefaultHttpRequest(request.protocolVersion(), request.method(), request.uri(), copiedHeaders);

        final String etag = cacheEntry.getResponseHeaders().get(HttpHeaderNames.ETAG);
        if (etag != null) {
            copiedHeaders.set(HttpHeaderNames.IF_NONE_MATCH, etag);
        }

        final String lastModified = cacheEntry.getResponseHeaders().get(HttpHeaderNames.LAST_MODIFIED);
        if (lastModified != null) {
            copiedHeaders.set(HttpHeaderNames.IF_MODIFIED_SINCE, lastModified);
        }

        final CacheControlDirectives cacheEntryCacheControlDirectives =
                CacheControlDecoder.decode(cacheEntry.getResponseHeaders());
        boolean mustRevalidate = cacheEntryCacheControlDirectives.mustRevalidate() ||
                                 cacheEntryCacheControlDirectives.proxyRevalidate();

        if (mustRevalidate) {
            copiedHeaders.add(HttpHeaderNames.CACHE_CONTROL, HttpHeaderValues.MAX_AGE + "=0");
        }

        return newRequest;
    }

    /**
     * Request with all caching disabled. Sometimes we can receive a response older than the current cache entry, when
     * this happens the recommendation is to retry the validation and force syncup.
     *
     * @param request request we are trying to satisfy
     *
     * @return
     */
    HttpRequest unconditionalRequest(HttpRequest request) {
        final DefaultHttpHeaders copiedHeaders = new DefaultHttpHeaders();
        for (Entry<String, String> header : request.headers()) {
            if (!UNCONDITIONAL_EXCLUDED_HEADERS.contains(header.getKey())) {
                copiedHeaders.add(header.getKey(), header.getValue());
            }
        }

        copiedHeaders.add(HttpHeaderNames.CACHE_CONTROL, HttpHeaderValues.NO_CACHE);
        copiedHeaders.add(HttpHeaderNames.PRAGMA, HttpHeaderValues.NO_CACHE);

        // TODO: Copy full request including body
        return new DefaultHttpRequest(request.protocolVersion(), request.method(), request.uri(), copiedHeaders);
    }
}
