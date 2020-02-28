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

import io.netty.handler.codec.DateFormatter;
import io.netty.handler.codec.http.cookie.CookieHeaderNames.SameSite;

import java.util.Date;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * A <a href="http://tools.ietf.org/html/rfc6265">RFC6265</a> compliant cookie decoder to be used client side.
 *
 * It will store the way the raw value was wrapped in {@link Cookie#setWrap(boolean)} so it can be
 * eventually sent back to the Origin server as is.
 *
 * @see ClientCookieEncoder
 */
public final class ClientCookieDecoder extends CookieDecoder {

    /**
     * Strict encoder that validates that name and value chars are in the valid scope
     * defined in RFC6265
     */
    public static final ClientCookieDecoder STRICT = new ClientCookieDecoder(true);

    /**
     * Lax instance that doesn't validate name and value
     */
    public static final ClientCookieDecoder LAX = new ClientCookieDecoder(false);

    private ClientCookieDecoder(boolean strict) {
        super(strict);
    }

    /**
     * Decodes the specified Set-Cookie HTTP header value into a {@link Cookie}.
     *
     * @return the decoded {@link Cookie}
     */
    public Cookie decode(String header) {
        final int headerLen = checkNotNull(header, "header").length();

        if (headerLen == 0) {
            return null;
        }

        CookieBuilder cookieBuilder = null;

        loop: for (int i = 0;;) {

            // Skip spaces and separators.
            for (;;) {
                if (i == headerLen) {
                    break loop;
                }
                char c = header.charAt(i);
                if (c == ',') {
                    // Having multiple cookies in a single Set-Cookie header is
                    // deprecated, modern browsers only parse the first one
                    break loop;

                } else if (c == '\t' || c == '\n' || c == 0x0b || c == '\f'
                        || c == '\r' || c == ' ' || c == ';') {
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

            if (valueEnd > 0 && header.charAt(valueEnd - 1) == ',') {
                // old multiple cookies separator, skipping it
                valueEnd--;
            }

            if (cookieBuilder == null) {
                // cookie name-value pair
                DefaultCookie cookie = initCookie(header, nameBegin, nameEnd, valueBegin, valueEnd);

                if (cookie == null) {
                    return null;
                }

                cookieBuilder = new CookieBuilder(cookie, header);
            } else {
                // cookie attribute
                cookieBuilder.appendAttribute(nameBegin, nameEnd, valueBegin, valueEnd);
            }
        }
        return cookieBuilder != null ? cookieBuilder.cookie() : null;
    }

    private static class CookieBuilder {

        private final String header;
        private final DefaultCookie cookie;
        private String domain;
        private String path;
        private long maxAge = Long.MIN_VALUE;
        private int expiresStart;
        private int expiresEnd;
        private boolean secure;
        private boolean httpOnly;
        private SameSite sameSite;

        CookieBuilder(DefaultCookie cookie, String header) {
            this.cookie = cookie;
            this.header = header;
        }

        private long mergeMaxAgeAndExpires() {
            // max age has precedence over expires
            if (maxAge != Long.MIN_VALUE) {
                return maxAge;
            } else if (isValueDefined(expiresStart, expiresEnd)) {
                Date expiresDate = DateFormatter.parseHttpDate(header, expiresStart, expiresEnd);
                if (expiresDate != null) {
                    long maxAgeMillis = expiresDate.getTime() - System.currentTimeMillis();
                    return maxAgeMillis / 1000 + (maxAgeMillis % 1000 != 0 ? 1 : 0);
                }
            }
            return Long.MIN_VALUE;
        }

        Cookie cookie() {
            cookie.setDomain(domain);
            cookie.setPath(path);
            cookie.setMaxAge(mergeMaxAgeAndExpires());
            cookie.setSecure(secure);
            cookie.setHttpOnly(httpOnly);
            cookie.setSameSite(sameSite);
            return cookie;
        }

        /**
         * Parse and store a key-value pair. First one is considered to be the
         * cookie name/value. Unknown attribute names are silently discarded.
         *
         * @param keyStart
         *            where the key starts in the header
         * @param keyEnd
         *            where the key ends in the header
         * @param valueStart
         *            where the value starts in the header
         * @param valueEnd
         *            where the value ends in the header
         */
        void appendAttribute(int keyStart, int keyEnd, int valueStart, int valueEnd) {
            int length = keyEnd - keyStart;

            if (length == 4) {
                parse4(keyStart, valueStart, valueEnd);
            } else if (length == 6) {
                parse6(keyStart, valueStart, valueEnd);
            } else if (length == 7) {
                parse7(keyStart, valueStart, valueEnd);
            } else if (length == 8) {
                parse8(keyStart, valueStart, valueEnd);
            }
        }

        private void parse4(int nameStart, int valueStart, int valueEnd) {
            if (header.regionMatches(true, nameStart, CookieHeaderNames.PATH, 0, 4)) {
                path = computeValue(valueStart, valueEnd);
            }
        }

        private void parse6(int nameStart, int valueStart, int valueEnd) {
            if (header.regionMatches(true, nameStart, CookieHeaderNames.DOMAIN, 0, 5)) {
                domain = computeValue(valueStart, valueEnd);
            } else if (header.regionMatches(true, nameStart, CookieHeaderNames.SECURE, 0, 5)) {
                secure = true;
            }
        }

        private void setMaxAge(String value) {
            try {
                maxAge = Math.max(Long.parseLong(value), 0L);
            } catch (NumberFormatException e1) {
                // ignore failure to parse -> treat as session cookie
            }
        }

        private void parse7(int nameStart, int valueStart, int valueEnd) {
            if (header.regionMatches(true, nameStart, CookieHeaderNames.EXPIRES, 0, 7)) {
                expiresStart = valueStart;
                expiresEnd = valueEnd;
            } else if (header.regionMatches(true, nameStart, CookieHeaderNames.MAX_AGE, 0, 7)) {
                setMaxAge(computeValue(valueStart, valueEnd));
            }
        }

        private void parse8(int nameStart, int valueStart, int valueEnd) {
            if (header.regionMatches(true, nameStart, CookieHeaderNames.HTTPONLY, 0, 8)) {
                httpOnly = true;
            } else if (header.regionMatches(true, nameStart, CookieHeaderNames.SAMESITE, 0, 8)) {
                sameSite = SameSite.of(computeValue(valueStart, valueEnd));
            }
        }

        private static boolean isValueDefined(int valueStart, int valueEnd) {
            return valueStart != -1 && valueStart != valueEnd;
        }

        private String computeValue(int valueStart, int valueEnd) {
            return isValueDefined(valueStart, valueEnd) ? header.substring(valueStart, valueEnd) : null;
        }
    }
}
