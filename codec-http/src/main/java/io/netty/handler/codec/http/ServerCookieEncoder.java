/*
 * Copyright 2014 The Netty Project
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
package io.netty.handler.codec.http;

import java.util.Collection;
import java.util.List;

/**
 * A <a href="http://tools.ietf.org/html/rfc6265">RFC6265</a> compliant cookie encoder to be used server side,
 * so some fields are sent (Version is typically ignored).
 *
 * As Netty's Cookie merges Expires and MaxAge into one single field, only Max-Age field is sent.
 *
 * Note that multiple cookies are supposed to be sent at once in a single "Set-Cookie" header.
 *
 * <pre>
 * // Example
 * {@link HttpRequest} req = ...;
 * res.setHeader("Cookie", {@link ServerCookieEncoder}.encode("JSESSIONID", "1234"));
 * </pre>
 *
 * @see ServerCookieDecoder
 *
 * @deprecated Use {@link io.netty.handler.codec.http.cookie.ServerCookieEncoder} instead
 */
@Deprecated
public final class ServerCookieEncoder {

    /**
     * Encodes the specified cookie name-value pair into a Set-Cookie header value.
     *
     * @param name the cookie name
     * @param value the cookie value
     * @return a single Set-Cookie header value
     */
    @Deprecated
    public static String encode(String name, String value) {
        return io.netty.handler.codec.http.cookie.ServerCookieEncoder.LAX.encode(name, value);
    }

    /**
     * Encodes the specified cookie into a Set-Cookie header value.
     *
     * @param cookie the cookie
     * @return a single Set-Cookie header value
     */
    @Deprecated
    public static String encode(Cookie cookie) {
        return io.netty.handler.codec.http.cookie.ServerCookieEncoder.LAX.encode(cookie);
    }

    /**
     * Batch encodes cookies into Set-Cookie header values.
     *
     * @param cookies a bunch of cookies
     * @return the corresponding bunch of Set-Cookie headers
     */
    @Deprecated
    public static List<String> encode(Cookie... cookies) {
        return io.netty.handler.codec.http.cookie.ServerCookieEncoder.LAX.encode(cookies);
    }

    /**
     * Batch encodes cookies into Set-Cookie header values.
     *
     * @param cookies a bunch of cookies
     * @return the corresponding bunch of Set-Cookie headers
     */
    @Deprecated
    public static List<String> encode(Collection<Cookie> cookies) {
        return io.netty.handler.codec.http.cookie.ServerCookieEncoder.LAX.encode(cookies);
    }

    /**
     * Batch encodes cookies into Set-Cookie header values.
     *
     * @param cookies a bunch of cookies
     * @return the corresponding bunch of Set-Cookie headers
     */
    @Deprecated
    public static List<String> encode(Iterable<Cookie> cookies) {
        return io.netty.handler.codec.http.cookie.ServerCookieEncoder.LAX.encode(cookies);
    }

    private ServerCookieEncoder() {
        // Unused
    }
}
