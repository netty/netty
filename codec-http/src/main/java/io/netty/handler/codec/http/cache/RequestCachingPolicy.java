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

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

class RequestCachingPolicy {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(RequestCachingPolicy.class);

    public boolean canBeServedFromCache(HttpRequest request) {
        if (request.protocolVersion().compareTo(HttpVersion.HTTP_1_1) < 0) {
            logger.debug("Non HTTP/1.1 request can not be served from cache.");
            return false;
        }

        final HttpMethod method = request.method();
        if (!method.equals(HttpMethod.GET) && !method.equals(HttpMethod.HEAD)) {
            if (logger.isDebugEnabled()) {
                logger.debug(method + " request can not be served from cache.");
            }
            return false;
        }

        if (request.headers().contains(HttpHeaderNames.VARY)) {
            // TODO: handle Vary
            logger.debug("Vary header is not yet supported, request can not be served from cache.");
            return false;
        }

        final CacheControlDirectives cacheControlDirectives = CacheControlDecoder.decode(request.headers());
        if (cacheControlDirectives.noStore()) {
            logger.debug("Request with 'cache-control: no-store' header can not be served from cache.");

            return false;
        }

        return true;
    }
}
