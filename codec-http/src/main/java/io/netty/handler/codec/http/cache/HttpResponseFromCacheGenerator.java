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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.ReadOnlyHttpHeaders;
import io.netty.util.AsciiString;

import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import static io.netty.buffer.Unpooled.*;
import static io.netty.handler.codec.http.cache.CacheControlDecoder.*;

class HttpResponseFromCacheGenerator {

    FullHttpResponse generate(HttpRequest request, HttpCacheEntry cacheEntry) {
        Date now = new Date();

        DefaultHttpHeaders headers = new DefaultHttpHeaders(false);
        headers.set(cacheEntry.getResponseHeaders());

        ByteBuf content = EMPTY_BUFFER;
        ByteBufHolder contentHolder = cacheEntry.getContent();
        if (request.method().equals(HttpMethod.GET) && content != null) {
            content = contentHolder.content();

            if (headers.get(HttpHeaderNames.TRANSFER_ENCODING) == null &&
                headers.get(HttpHeaderNames.CONTENT_LENGTH) == null) {
                headers.setInt(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());
            }
        }

        long age = cacheEntry.getCurrentAgeInSeconds(now);
        if (age > 0) {
            if (age >= MAXIMUM_AGE) {
                headers.add(HttpHeaderNames.AGE, Integer.toString(MAXIMUM_AGE));
            } else {
                headers.add(HttpHeaderNames.AGE, Long.toString(age));
            }
        }

        return new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, cacheEntry.getStatus(), content, headers,
                                           new ReadOnlyHttpHeaders(false));
    }

    HttpResponse generateNotModifiedResponse(final HttpCacheEntry entry) {

        HttpHeaders headers = new DefaultHttpHeaders();
        Set<AsciiString> headerNames = new HashSet<AsciiString>(Arrays.asList(HttpHeaderNames.DATE,
                HttpHeaderNames.ETAG, HttpHeaderNames.CONTENT_LOCATION, HttpHeaderNames.EXPIRES,
                HttpHeaderNames.CACHE_CONTROL, HttpHeaderNames.VARY));
        for (AsciiString headerName : headerNames) {
            String headerValue = entry.getResponseHeaders().get(headerName);
            if (headerValue != null) {
                headers.add(headerName, headerValue);
            }
        }

        return new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_MODIFIED, headers);
    }
}
