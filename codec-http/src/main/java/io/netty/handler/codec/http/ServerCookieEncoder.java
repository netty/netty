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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
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
 */
public final class ServerCookieEncoder {

    /**
     * Encodes the specified cookie name-value pair into a Set-Cookie header value.
     *
     * @param name the cookie name
     * @param value the cookie value
     * @return a single Set-Cookie header value
     */
    public static String encode(String name, String value) {
        return encode(new DefaultCookie(name, value));
    }

    /**
     * Encodes the specified cookie into a Set-Cookie header value.
     *
     * @param cookie the cookie
     * @return a single Set-Cookie header value
     */
    public static String encode(Cookie cookie) {
        if (cookie == null) {
            throw new NullPointerException("cookie");
        }

        StringBuilder buf = stringBuilder();

        addUnquoted(buf, cookie.name(), cookie.value());

        if (cookie.maxAge() != Long.MIN_VALUE) {
            add(buf, CookieHeaderNames.MAX_AGE, cookie.maxAge());
            Date expires = new Date(cookie.maxAge() * 1000 + System.currentTimeMillis());
            addUnquoted(buf, CookieHeaderNames.EXPIRES, HttpHeaderDateFormat.get().format(expires));
        }

        if (cookie.path() != null) {
            addUnquoted(buf, CookieHeaderNames.PATH, cookie.path());
        }

        if (cookie.domain() != null) {
            addUnquoted(buf, CookieHeaderNames.DOMAIN, cookie.domain());
        }
        if (cookie.isSecure()) {
            buf.append(CookieHeaderNames.SECURE);
            buf.append((char) HttpConstants.SEMICOLON);
            buf.append((char) HttpConstants.SP);
        }
        if (cookie.isHttpOnly()) {
            buf.append(CookieHeaderNames.HTTPONLY);
            buf.append((char) HttpConstants.SEMICOLON);
            buf.append((char) HttpConstants.SP);
        }

        return stripTrailingSeparator(buf);
    }

    /**
     * Batch encodes cookies into Set-Cookie header values.
     *
     * @param cookies a bunch of cookies
     * @return the corresponding bunch of Set-Cookie headers
     */
    public static List<String> encode(Cookie... cookies) {
        if (cookies == null) {
            throw new NullPointerException("cookies");
        }

        if (cookies.length == 0) {
            return Collections.emptyList();
        }

        List<String> encoded = new ArrayList<String>(cookies.length);
        for (Cookie c : cookies) {
            if (c == null) {
                break;
            }
            encoded.add(encode(c));
        }
        return encoded;
    }

    /**
     * Batch encodes cookies into Set-Cookie header values.
     *
     * @param cookies a bunch of cookies
     * @return the corresponding bunch of Set-Cookie headers
     */
    public static List<String> encode(Collection<Cookie> cookies) {
        if (cookies == null) {
            throw new NullPointerException("cookies");
        }

        if (cookies.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> encoded = new ArrayList<String>(cookies.size());
        for (Cookie c : cookies) {
            if (c == null) {
                break;
            }
            encoded.add(encode(c));
        }
        return encoded;
    }

    /**
     * Batch encodes cookies into Set-Cookie header values.
     *
     * @param cookies a bunch of cookies
     * @return the corresponding bunch of Set-Cookie headers
     */
    public static List<String> encode(Iterable<Cookie> cookies) {
        if (cookies == null) {
            throw new NullPointerException("cookies");
        }

        if (!cookies.iterator().hasNext()) {
            return Collections.emptyList();
        }

        List<String> encoded = new ArrayList<String>();
        for (Cookie c : cookies) {
            if (c == null) {
                break;
            }
            encoded.add(encode(c));
        }
        return encoded;
    }

    private ServerCookieEncoder() {
        // Unused
    }
}
