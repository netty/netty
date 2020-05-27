/*
 * Copyright 2015 The Netty Project
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
package org.jboss.netty.handler.codec.http.cookie;

import static org.jboss.netty.handler.codec.http.cookie.CookieUtil.*;

import org.jboss.netty.handler.codec.http.HttpHeaderDateFormat;
import org.jboss.netty.handler.codec.http.HttpRequest;

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
public final class ServerCookieEncoder extends CookieEncoder {

    /**
     * Strict encoder that validates that name and value chars are in the valid scope
     * defined in RFC6265
     */
    public static final ServerCookieEncoder STRICT = new ServerCookieEncoder(true);

    /**
     * Lax instance that doesn't validate name and value
     */
    public static final ServerCookieEncoder LAX = new ServerCookieEncoder(false);

    private ServerCookieEncoder(boolean strict) {
        super(strict);
    }

    /**
     * Encodes the specified cookie name-value pair into a Set-Cookie header value.
     *
     * @param name the cookie name
     * @param value the cookie value
     * @return a single Set-Cookie header value
     */
    public String encode(String name, String value) {
        return encode(new DefaultCookie(name, value));
    }

    /**
     * Encodes the specified cookie into a Set-Cookie header value.
     *
     * @param cookie the cookie
     * @return a single Set-Cookie header value
     */
    public String encode(Cookie cookie) {
        if (cookie == null) {
            throw new NullPointerException("cookie");
        }
        final String name = cookie.name();
        final String value = cookie.value() != null ? cookie.value() : "";

        validateCookie(name, value);

        StringBuilder buf = new StringBuilder();

        if (cookie.wrap()) {
            addQuoted(buf, name, value);
        } else {
            add(buf, name, value);
        }

        if (cookie.maxAge() != Integer.MIN_VALUE) {
            add(buf, CookieHeaderNames.MAX_AGE, cookie.maxAge());
            Date expires = new Date((long) cookie.maxAge() * 1000 + System.currentTimeMillis());
            add(buf, CookieHeaderNames.EXPIRES, HttpHeaderDateFormat.get().format(expires));
        }

        if (cookie.path() != null) {
            add(buf, CookieHeaderNames.PATH, cookie.path());
        }

        if (cookie.domain() != null) {
            add(buf, CookieHeaderNames.DOMAIN, cookie.domain());
        }
        if (cookie.isSecure()) {
            add(buf, CookieHeaderNames.SECURE);
        }
        if (cookie.isHttpOnly()) {
            add(buf, CookieHeaderNames.HTTPONLY);
        }
        if (cookie instanceof DefaultCookie) {
            DefaultCookie c = (DefaultCookie) cookie;
            if (c.sameSite() != null) {
                add(buf, CookieHeaderNames.SAMESITE, c.sameSite().name());
            }
        }

        return stripTrailingSeparator(buf);
    }

    /**
     * Batch encodes cookies into Set-Cookie header values.
     *
     * @param cookies a bunch of cookies
     * @return the corresponding bunch of Set-Cookie headers
     */
    public List<String> encode(Cookie... cookies) {
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
    public List<String> encode(Collection<? extends Cookie> cookies) {
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
    public List<String> encode(Iterable<? extends Cookie> cookies) {
        if (cookies == null) {
            throw new NullPointerException("cookies");
        }
        if (cookies.iterator().hasNext()) {
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
}
