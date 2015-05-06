/*
 * Copyright 2012 The Netty Project
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

/**
 * Encodes client-side {@link Cookie}s into an HTTP header value.  This encoder can encode
 * the HTTP cookie version 0, 1, and 2.
 * <pre>
 * // Example
 * {@link HttpRequest} req = ...;
 * res.setHeader("Cookie", {@link ClientCookieEncoder}.encode("JSESSIONID", "1234"));
 * </pre>
 *
 * @see CookieDecoder
 * @deprecated Use {@link io.netty.handler.codec.http.cookie.ClientCookieEncoder} instead.
 */
@Deprecated
public final class ClientCookieEncoder {

    /**
     * Encodes the specified cookie into an HTTP header value.
     */
    @Deprecated
    public static String encode(String name, String value) {
        return io.netty.handler.codec.http.cookie.ClientCookieEncoder.LAX.encode(name, value);
    }

    @Deprecated
    public static String encode(Cookie cookie) {
        return io.netty.handler.codec.http.cookie.ClientCookieEncoder.LAX.encode(cookie);
    }

    @Deprecated
    public static String encode(Cookie... cookies) {
        return io.netty.handler.codec.http.cookie.ClientCookieEncoder.LAX.encode(cookies);
    }

    @Deprecated
    public static String encode(Iterable<Cookie> cookies) {
        return io.netty.handler.codec.http.cookie.ClientCookieEncoder.LAX.encode(cookies);
    }

    private ClientCookieEncoder() {
        // Unused
    }
}
