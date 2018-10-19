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
package io.netty.handler.codec.http.cookie;

import static io.netty.handler.codec.http.cookie.CookieUtil.add;
import static io.netty.handler.codec.http.cookie.CookieUtil.addQuoted;
import static io.netty.handler.codec.http.cookie.CookieUtil.stringBuilder;
import static io.netty.handler.codec.http.cookie.CookieUtil.stripTrailingSeparator;
import static io.netty.handler.codec.http.cookie.CookieUtil.stripTrailingSeparatorOrNull;
import static io.netty.util.internal.ObjectUtil.checkNotNull;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.util.internal.InternalThreadLocalMap;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * A <a href="http://tools.ietf.org/html/rfc6265">RFC6265</a> compliant cookie encoder to be used client side, so
 * only name=value pairs are sent.
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
public final class ClientCookieEncoder extends CookieEncoder {

    /**
     * Strict encoder that validates that name and value chars are in the valid scope and (for methods that accept
     * multiple cookies) sorts cookies into order of decreasing path length, as specified in RFC6265.
     */
    public static final ClientCookieEncoder STRICT = new ClientCookieEncoder(true);

    /**
     * Lax instance that doesn't validate name and value, and (for methods that accept multiple cookies) keeps
     * cookies in the order in which they were given.
     */
    public static final ClientCookieEncoder LAX = new ClientCookieEncoder(false);

    private ClientCookieEncoder(boolean strict) {
        super(strict);
    }

    /**
     * Encodes the specified cookie into a Cookie header value.
     *
     * @param name
     *            the cookie name
     * @param value
     *            the cookie value
     * @return a Rfc6265 style Cookie header value
     */
    public String encode(String name, String value) {
        return encode(new DefaultCookie(name, value));
    }

    /**
     * Encodes the specified cookie into a Cookie header value.
     *
     * @param cookie the specified cookie
     * @return a Rfc6265 style Cookie header value
     */
    public String encode(Cookie cookie) {
        StringBuilder buf = stringBuilder();
        encode(buf, checkNotNull(cookie, "cookie"));
        return stripTrailingSeparator(buf);
    }

    /**
     * Sort cookies into decreasing order of path length, breaking ties by sorting into increasing chronological
     * order of creation time, as recommended by RFC 6265.
     */
    private static final Comparator<Cookie> COOKIE_COMPARATOR = new Comparator<Cookie>() {
        @Override
        public int compare(Cookie c1, Cookie c2) {
            String path1 = c1.path();
            String path2 = c2.path();
            // Cookies with unspecified path default to the path of the request. We don't
            // know the request path here, but we assume that the length of an unspecified
            // path is longer than any specified path (i.e. pathless cookies come first),
            // because setting cookies with a path longer than the request path is of
            // limited use.
            int len1 = path1 == null ? Integer.MAX_VALUE : path1.length();
            int len2 = path2 == null ? Integer.MAX_VALUE : path2.length();
            int diff = len2 - len1;
            if (diff != 0) {
                return diff;
            }
            // Rely on Java's sort stability to retain creation order in cases where
            // cookies have same path length.
            return -1;
        }
    };

    /**
     * Encodes the specified cookies into a single Cookie header value.
     *
     * @param cookies
     *            some cookies
     * @return a Rfc6265 style Cookie header value, null if no cookies are passed.
     */
    public String encode(Cookie... cookies) {
        if (checkNotNull(cookies, "cookies").length == 0) {
            return null;
        }

        StringBuilder buf = stringBuilder();
        if (strict) {
            if (cookies.length == 1) {
                encode(buf, cookies[0]);
            } else {
                Cookie[] cookiesSorted = Arrays.copyOf(cookies, cookies.length);
                Arrays.sort(cookiesSorted, COOKIE_COMPARATOR);
                for (Cookie c : cookiesSorted) {
                    encode(buf, c);
                }
            }
        } else {
            for (Cookie c : cookies) {
                encode(buf, c);
            }
        }
        return stripTrailingSeparatorOrNull(buf);
    }

    /**
     * Encodes the specified cookies into a single Cookie header value.
     *
     * @param cookies
     *            some cookies
     * @return a Rfc6265 style Cookie header value, null if no cookies are passed.
     */
    public String encode(Collection<? extends Cookie> cookies) {
        if (checkNotNull(cookies, "cookies").isEmpty()) {
            return null;
        }

        StringBuilder buf = stringBuilder();
        if (strict) {
            if (cookies.size() == 1) {
                encode(buf, cookies.iterator().next());
            } else {
                Cookie[] cookiesSorted = cookies.toArray(new Cookie[0]);
                Arrays.sort(cookiesSorted, COOKIE_COMPARATOR);
                for (Cookie c : cookiesSorted) {
                    encode(buf, c);
                }
            }
        } else {
            for (Cookie c : cookies) {
                encode(buf, c);
            }
        }
        return stripTrailingSeparatorOrNull(buf);
    }

    /**
     * Encodes the specified cookies into a single Cookie header value.
     *
     * @param cookies some cookies
     * @return a Rfc6265 style Cookie header value, null if no cookies are passed.
     */
    public String encode(Iterable<? extends Cookie> cookies) {
        Iterator<? extends Cookie> cookiesIt = checkNotNull(cookies, "cookies").iterator();
        if (!cookiesIt.hasNext()) {
            return null;
        }

        StringBuilder buf = stringBuilder();
        if (strict) {
            Cookie firstCookie = cookiesIt.next();
            if (!cookiesIt.hasNext()) {
                encode(buf, firstCookie);
            } else {
                List<Cookie> cookiesList = InternalThreadLocalMap.get().arrayList();
                cookiesList.add(firstCookie);
                while (cookiesIt.hasNext()) {
                    cookiesList.add(cookiesIt.next());
                }
                Cookie[] cookiesSorted = cookiesList.toArray(new Cookie[0]);
                Arrays.sort(cookiesSorted, COOKIE_COMPARATOR);
                for (Cookie c : cookiesSorted) {
                    encode(buf, c);
                }
            }
        } else {
            while (cookiesIt.hasNext()) {
                encode(buf, cookiesIt.next());
            }
        }
        return stripTrailingSeparatorOrNull(buf);
    }

    private void encode(StringBuilder buf, Cookie c) {
        final String name = c.name();
        final String value = c.value() != null ? c.value() : "";

        validateCookie(name, value);

        if (c.wrap()) {
            addQuoted(buf, name, value);
        } else {
            add(buf, name, value);
        }
    }
}
