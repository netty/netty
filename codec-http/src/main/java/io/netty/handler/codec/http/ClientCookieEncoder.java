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

import static io.netty.handler.codec.http.CookieEncoderUtil.*;

/**
 * A <a href="http://tools.ietf.org/html/rfc6265">RFC6265</a> compliant cookie encoder to be used client side,
 * so only name=value pairs are sent.
 *
 * User-Agents are not supposed to interpret cookies, so, if present, {@link Cookie#rawValue()} will be used.
 * Otherwise, {@link Cookie#value()} will be used unquoted.
 *
 * Note that multiple cookies are supposed to be sent at once in a single "Cookie" header.
 *
 * <pre>
 * // Example
 * {@link HttpRequest} req = ...;
 * res.setHeader("Cookie", {@link ClientCookieEncoder}.encode("JSESSIONID", "1234"));
 * </pre>
 *
 * @see ClientCookieDecoder
 */
public final class ClientCookieEncoder {

    /**
     * Encodes the specified cookie into a Cookie header value.
     *
     * @param name the cookie name
     * @param value the cookie value
     * @return a Rfc6265 style Cookie header value
     */
    public static String encode(String name, String value) {
        return encode(new DefaultCookie(name, value));
    }

    /**
     * Encodes the specified cookie into a Cookie header value.
     *
     * @param specified the cookie
     * @return a Rfc6265 style Cookie header value
     */
    public static String encode(Cookie cookie) {
        if (cookie == null) {
            throw new NullPointerException("cookie");
        }

        StringBuilder buf = stringBuilder();
        encode(buf, cookie);
        return stripTrailingSeparator(buf);
    }

    /**
     * Encodes the specified cookies into a single Cookie header value.
     *
     * @param cookies some cookies
     * @return a Rfc6265 style Cookie header value, null if no cookies are passed.
     */
    public static String encode(Cookie... cookies) {
        if (cookies == null) {
            throw new NullPointerException("cookies");
        }

        if (cookies.length == 0) {
            return null;
        }

        StringBuilder buf = stringBuilder();
        for (Cookie c : cookies) {
            if (c == null) {
                break;
            }

            encode(buf, c);
        }
        return stripTrailingSeparatorOrNull(buf);
    }

    /**
     * Encodes the specified cookies into a single Cookie header value.
     *
     * @param cookies some cookies
     * @return a Rfc6265 style Cookie header value, null if no cookies are passed.
     */
    public static String encode(Iterable<Cookie> cookies) {
        if (cookies == null) {
            throw new NullPointerException("cookies");
        }

        if (!cookies.iterator().hasNext()) {
            return null;
        }

        StringBuilder buf = stringBuilder();
        for (Cookie c : cookies) {
            if (c == null) {
                break;
            }

            encode(buf, c);
        }
        return stripTrailingSeparatorOrNull(buf);
    }

    private static void encode(StringBuilder buf, Cookie c) {
        // rawValue > value > ""
        String value = c.rawValue() != null ? c.rawValue()
                : c.value() != null ? c.value() : "";
        addUnquoted(buf, c.name(), value);
    }

    private ClientCookieEncoder() {
        // unused
    }
}
