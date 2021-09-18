/*
 * Copyright 2015 The Netty Project
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
package io.netty.handler.codec.http.cookie;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * A <a href="https://tools.ietf.org/html/rfc6265">RFC6265</a> compliant cookie decoder to be used server side.
 *
 * Only name and value fields are expected, so old fields are not populated (path, domain, etc).
 *
 * Old <a href="https://tools.ietf.org/html/rfc2965">RFC2965</a> cookies are still supported,
 * old fields will simply be ignored.
 *
 * @see ServerCookieEncoder
 */
public final class ServerCookieDecoder extends CookieDecoder {

    private static final String RFC2965_VERSION = "$Version";

    private static final String RFC2965_PATH = "$" + CookieHeaderNames.PATH;

    private static final String RFC2965_DOMAIN = "$" + CookieHeaderNames.DOMAIN;

    private static final String RFC2965_PORT = "$Port";

    /**
     * Strict encoder that validates that name and value chars are in the valid scope
     * defined in RFC6265
     */
    public static final ServerCookieDecoder STRICT = new ServerCookieDecoder(true);

    /**
     * Lax instance that doesn't validate name and value
     */
    public static final ServerCookieDecoder LAX = new ServerCookieDecoder(false);

    private ServerCookieDecoder(boolean strict) {
        super(strict);
    }

    /**
     * Decodes the specified {@code Cookie} HTTP header value into a {@link Cookie}. Unlike {@link #decode(String)},
     * this includes all cookie values present, even if they have the same name.
     *
     * @return the decoded {@link Cookie}
     */
    public List<Cookie> decodeAll(String header) {
        List<Cookie> cookies = new ArrayList<Cookie>();
        decode(cookies, header);
        return Collections.unmodifiableList(cookies);
    }

    /**
     * Decodes the specified {@code Cookie} HTTP header value into a {@link Cookie}.
     *
     * @return the decoded {@link Cookie}
     */
    public Set<Cookie> decode(String header) {
        Set<Cookie> cookies = new TreeSet<Cookie>();
        decode(cookies, header);
        return cookies;
    }

    /**
     * Decodes the specified {@code Cookie} HTTP header value into a {@link Cookie}.
     */
    private void decode(Collection<? super Cookie> cookies, String header) {
        final int headerLen = checkNotNull(header, "header").length();

        if (headerLen == 0) {
            return;
        }

        int i = 0;

        boolean rfc2965Style = false;
        if (header.regionMatches(true, 0, RFC2965_VERSION, 0, RFC2965_VERSION.length())) {
            // RFC 2965 style cookie, move to after version value
            i = header.indexOf(';') + 1;
            rfc2965Style = true;
        }

        loop: for (;;) {

            // Skip spaces and separators.
            for (;;) {
                if (i == headerLen) {
                    break loop;
                }
                char c = header.charAt(i);
                if (c == '\t' || c == '\n' || c == 0x0b || c == '\f'
                        || c == '\r' || c == ' ' || c == ',' || c == ';') {
                    i++;
                    continue;
                }
                break;
            }

            int nameBegin = i;
            int nameEnd;
            int valueBegin;
            int valueEnd;

            for (;;) {

                char curChar = header.charAt(i);
                if (curChar == ';') {
                    // NAME; (no value till ';')
                    nameEnd = i;
                    valueBegin = valueEnd = -1;
                    break;

                } else if (curChar == '=') {
                    // NAME=VALUE
                    nameEnd = i;
                    i++;
                    if (i == headerLen) {
                        // NAME= (empty value, i.e. nothing after '=')
                        valueBegin = valueEnd = 0;
                        break;
                    }

                    valueBegin = i;
                    // NAME=VALUE;
                    int semiPos = header.indexOf(';', i);
                    valueEnd = i = semiPos > 0 ? semiPos : headerLen;
                    break;
                } else {
                    i++;
                }

                if (i == headerLen) {
                    // NAME (no value till the end of string)
                    nameEnd = headerLen;
                    valueBegin = valueEnd = -1;
                    break;
                }
            }

            if (rfc2965Style && (header.regionMatches(nameBegin, RFC2965_PATH, 0, RFC2965_PATH.length()) ||
                    header.regionMatches(nameBegin, RFC2965_DOMAIN, 0, RFC2965_DOMAIN.length()) ||
                    header.regionMatches(nameBegin, RFC2965_PORT, 0, RFC2965_PORT.length()))) {

                // skip obsolete RFC2965 fields
                continue;
            }

            DefaultCookie cookie = initCookie(header, nameBegin, nameEnd, valueBegin, valueEnd);
            if (cookie != null) {
                cookies.add(cookie);
            }
        }
    }
}
