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

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class ResponseCachingPolicy {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(RequestCachingPolicy.class);

    private final Set<HttpResponseStatus> uncacheableStatusCodes = new HashSet<HttpResponseStatus>(
            Collections.singletonList(
                    HttpResponseStatus.PARTIAL_CONTENT
            ));

    private final boolean sharedCache;

    ResponseCachingPolicy(boolean sharedCache) {
        this.sharedCache = sharedCache;
    }

    public boolean canBeCached(HttpRequest request, HttpResponse response) {
        if (request.protocolVersion().compareTo(HttpVersion.HTTP_1_1) < 0) {
            logger.debug("HTTP/1.0 request can not be cached.");
            return false;
        }

        HttpMethod httpMethod = request.method();
        if (httpMethod != HttpMethod.GET && httpMethod != HttpMethod.HEAD) {
            if (logger.isDebugEnabled()) {
                logger.debug(httpMethod + " method response is not cacheable.");
            }

            return false;
        }

        HttpResponseStatus status = response.status();
        if (uncacheableStatusCodes.contains(status)) {
            if (logger.isDebugEnabled()) {
                logger.debug(status + " response is not cacheable.");
            }

            return false;
        }

        if (unknownStatusCode(status)) {
            if (logger.isDebugEnabled()) {
                logger.debug(status + " response status is unknown.");
            }

            return false;
        }

        HttpHeaders headers = response.headers();
        CacheControlDirectives cacheControlDirectives = CacheControlDecoder.decode(headers);
        List<String> dateHeader = headers.getAll(HttpHeaderNames.DATE);
        if (dateHeader.size() > 1) {
            logger.debug("Multiple Date headers.");
            return false;
        }

        Long dateHeaderInMilliseconds = headers.getTimeMillis(HttpHeaderNames.DATE);
        if (dateHeaderInMilliseconds == null) {
            logger.debug("Invalid Date header.");
            return false;
        }

        if (isForbiddenByCacheControl(cacheControlDirectives)) {
            logger.debug("Response can not be cached because of Cache-Control header.");
            return false;
        }

        if (sharedCache) {
            if (headers.contains(HttpHeaderNames.AUTHORIZATION) &&
                    !isCacheableAuthorizedResponse(cacheControlDirectives)) {
                logger.debug("Shared cache can not cache request with credentials.");
                return false;
            }
        }

        return true;
    }

    private static boolean isForbiddenByCacheControl(final CacheControlDirectives cacheControlDirectives) {
        return cacheControlDirectives.noStore() || cacheControlDirectives.noCache();
    }

    private static boolean isCacheableAuthorizedResponse(final CacheControlDirectives cacheControlDirectives) {
        return cacheControlDirectives.isPublic() ||
                cacheControlDirectives.mustRevalidate() ||
                cacheControlDirectives.getSMaxAge() != -1;
    }

    private static boolean unknownStatusCode(final HttpResponseStatus status) {
        final int code = status.code();
        return (code < 100 || code > 101) &&
                (code < 200 || code > 206) &&
                (code < 300 || code > 307) &&
                (code < 400 || code > 417) &&
                (code < 500 || code > 505);
    }
}
