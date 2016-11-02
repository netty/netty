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
import static io.netty.util.internal.ObjectUtil.checkNotNull;

import io.netty.handler.codec.CharSequenceValueConverter;
import io.netty.handler.codec.http.HttpHeaderDateFormat;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.util.AsciiString;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

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
     * defined in RFC6265, and (for methods that accept multiple cookies) that only
     * one cookie is encoded with any given name. (If multiple cookies have the same
     * name, the last one is the one that is encoded.)
     */
    public static final ServerCookieEncoder STRICT = new ServerCookieEncoder(true);

    /**
     * Lax instance that doesn't validate name and value, and that allows multiple
     * cookies with the same name.
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
    @Deprecated
    public String encode(String name, String value) {
        return encodeAsciiString(new DefaultCookie(name, value)).toString();
    }

    /**
     * Encodes the specified cookie name-value pair into a Set-Cookie header value.
     *
     * @param name the cookie name
     * @param value the cookie value
     * @return a single Set-Cookie header value
     */
    public AsciiString encodeAsciiString(AsciiString name, AsciiString value) {
        return encodeAsciiString(new DefaultCookie(name, value));
    }

    /**
     * Encodes the specified cookie into a Set-Cookie header value.
     *
     * @param cookie the cookie
     * @return a single Set-Cookie header value
     */
    @Deprecated
    public String encode(Cookie cookie) {
        return encodeAsciiString(cookie).toString();
    }

    /**
     * Encodes the specified cookie into a Set-Cookie header value.
     *
     * @param cookie the cookie
     * @return a single Set-Cookie header value
     */
    public AsciiString encodeAsciiString(Cookie cookie) {
        final AsciiString name = checkNotNull(cookie, "cookie").asciiName();
        final AsciiString value = cookie.asciiValue() != null ? cookie.asciiValue() : AsciiString.EMPTY_STRING;

        validateCookie(name, value);

        StringBuilder buf = stringBuilder();

        if (cookie.wrap()) {
            addQuoted(buf, name, value);
        } else {
            add(buf, name, value);
        }

        if (cookie.maxAge() != Long.MIN_VALUE) {
            add(buf, CookieHeaderNames.MAX_AGE_ASCII, cookie.maxAge());
            Date expires = new Date(cookie.maxAge() * 1000 + System.currentTimeMillis());
            add(buf, CookieHeaderNames.EXPIRES_ASCII, new AsciiString(HttpHeaderDateFormat.get().format(expires)));
        }

        if (cookie.asciiPath() != null) {
            add(buf, CookieHeaderNames.PATH_ASCII, cookie.asciiPath());
        }

        if (cookie.asciiDomain() != null) {
            add(buf, CookieHeaderNames.DOMAIN_ASCII, cookie.asciiDomain());
        }
        if (cookie.isSecure()) {
            add(buf, CookieHeaderNames.SECURE_ASCII);
        }
        if (cookie.isHttpOnly()) {
            add(buf, CookieHeaderNames.HTTPONLY_ASCII);
        }

        return stripTrailingSeparator(buf);
    }

    /** Deduplicate a list of encoded cookies by keeping only the last instance with a given name.
     *
     * @param encoded The list of encoded cookies.
     * @param nameToLastIndex A map from cookie name to index of last cookie instance.
     * @return The encoded list with all but the last instance of a named cookie.
     */
    private <T extends CharSequence> List<T> dedup(List<T> encoded, Map<T, Integer> nameToLastIndex) {
        boolean[] isLastInstance = new boolean[encoded.size()];
        for (int idx : nameToLastIndex.values()) {
            isLastInstance[idx] = true;
        }
        List<T> dedupd = new ArrayList<T>(nameToLastIndex.size());
        for (int i = 0, n = encoded.size(); i < n; i++) {
            if (isLastInstance[i]) {
                dedupd.add(encoded.get(i));
            }
        }
        return dedupd;
    }

    /**
     * Batch encodes cookies into Set-Cookie header values.
     *
     * @param cookies a bunch of cookies
     * @return the corresponding bunch of Set-Cookie headers
     */
    @Deprecated
    public List<String> encode(Cookie... cookies) {
        if (checkNotNull(cookies, "cookies").length == 0) {
            return Collections.emptyList();
        }

        List<String> encoded = new ArrayList<String>(cookies.length);
        Map<String, Integer> nameToIndex = strict && cookies.length > 1 ? new HashMap<String, Integer>() : null;
        boolean hasDupdName = false;
        for (int i = 0; i < cookies.length; i++) {
            Cookie c = cookies[i];
            encoded.add(encode(c));
            if (nameToIndex != null) {
                hasDupdName |= nameToIndex.put(c.name(), i) != null;
            }
        }
        return hasDupdName ? dedup(encoded, nameToIndex) : encoded;
    }

    /**
     * Batch encodes cookies into Set-Cookie header values.
     *
     * @param cookies a bunch of cookies
     * @return the corresponding bunch of Set-Cookie headers
     */
    public List<AsciiString> encodeAsciiString(Cookie... cookies) {
        if (checkNotNull(cookies, "cookies").length == 0) {
            return Collections.emptyList();
        }

        List<AsciiString> encoded = new ArrayList<AsciiString>(cookies.length);
        Map<AsciiString, Integer> nameToIndex = strict && cookies.length > 1 ? new HashMap<AsciiString, Integer>() : null;
        boolean hasDupdName = false;
        for (int i = 0; i < cookies.length; i++) {
            Cookie c = cookies[i];
            encoded.add(encodeAsciiString(c));
            if (nameToIndex != null) {
                hasDupdName |= nameToIndex.put(c.asciiName(), i) != null;
            }
        }
        return hasDupdName ? dedup(encoded, nameToIndex) : encoded;
    }

    /**
     * Batch encodes cookies into Set-Cookie header values.
     *
     * @param cookies a bunch of cookies
     * @return the corresponding bunch of Set-Cookie headers
     */
    @Deprecated
    public List<String> encode(Collection<? extends Cookie> cookies) {
        if (checkNotNull(cookies, "cookies").isEmpty()) {
            return Collections.emptyList();
        }

        List<String> encoded = new ArrayList<String>(cookies.size());
        Map<String, Integer> nameToIndex = strict && cookies.size() > 1 ? new HashMap<String, Integer>() : null;
        int i = 0;
        boolean hasDupdName = false;
        for (Cookie c : cookies) {
            encoded.add(encode(c));
            if (nameToIndex != null) {
                hasDupdName |= nameToIndex.put(c.name(), i++) != null;
            }
        }
        return hasDupdName ? dedup(encoded, nameToIndex) : encoded;
    }

    /**
     * Batch encodes cookies into Set-Cookie header values.
     *
     * @param cookies a bunch of cookies
     * @return the corresponding bunch of Set-Cookie headers
     */
    public List<AsciiString> encodeAsciiString(Collection<? extends Cookie> cookies) {
        if (checkNotNull(cookies, "cookies").isEmpty()) {
            return Collections.emptyList();
        }

        List<AsciiString> encoded = new ArrayList<AsciiString>(cookies.size());
        Map<AsciiString, Integer> nameToIndex = strict && cookies.size() > 1 ? new HashMap<AsciiString, Integer>() : null;
        int i = 0;
        boolean hasDupdName = false;
        for (Cookie c : cookies) {
            encoded.add(encodeAsciiString(c));
            if (nameToIndex != null) {
                hasDupdName |= nameToIndex.put(c.asciiName(), i++) != null;
            }
        }
        return hasDupdName ? dedup(encoded, nameToIndex) : encoded;
    }

    /**
     * Batch encodes cookies into Set-Cookie header values.
     *
     * @param cookies a bunch of cookies
     * @return the corresponding bunch of Set-Cookie headers
     */
    @Deprecated
    public List<String> encode(Iterable<? extends Cookie> cookies) {
        Iterator<? extends Cookie> cookiesIt = checkNotNull(cookies, "cookies").iterator();
        if (!cookiesIt.hasNext()) {
            return Collections.emptyList();
        }

        List<String> encoded = new ArrayList<String>();
        Cookie firstCookie = cookiesIt.next();
        Map<String, Integer> nameToIndex = strict && cookiesIt.hasNext() ? new HashMap<String, Integer>() : null;
        int i = 0;
        encoded.add(encode(firstCookie));
        boolean hasDupdName = nameToIndex != null ? nameToIndex.put(firstCookie.name(), i++) != null : false;
        while (cookiesIt.hasNext()) {
            Cookie c = cookiesIt.next();
            encoded.add(encode(c));
            if (nameToIndex != null) {
                hasDupdName |= nameToIndex.put(c.name(), i++) != null;
            }
        }
        return hasDupdName ? dedup(encoded, nameToIndex) : encoded;
    }

    /**
     * Batch encodes cookies into Set-Cookie header values.
     *
     * @param cookies a bunch of cookies
     * @return the corresponding bunch of Set-Cookie headers
     */
    public List<AsciiString> encodeAsciiString(Iterable<? extends Cookie> cookies) {
        Iterator<? extends Cookie> cookiesIt = checkNotNull(cookies, "cookies").iterator();
        if (!cookiesIt.hasNext()) {
            return Collections.emptyList();
        }

        List<AsciiString> encoded = new ArrayList<AsciiString>();
        Cookie firstCookie = cookiesIt.next();
        Map<AsciiString, Integer> nameToIndex = strict && cookiesIt.hasNext() ? new HashMap<AsciiString, Integer>() : null;
        int i = 0;
        encoded.add(encodeAsciiString(firstCookie));
        boolean hasDupdName = nameToIndex != null ? nameToIndex.put(firstCookie.asciiName(), i++) != null : false;
        while (cookiesIt.hasNext()) {
            Cookie c = cookiesIt.next();
            encoded.add(encodeAsciiString(c));
            if (nameToIndex != null) {
                hasDupdName |= nameToIndex.put(c.asciiName(), i++) != null;
            }
        }
        return hasDupdName ? dedup(encoded, nameToIndex) : encoded;
    }
}
