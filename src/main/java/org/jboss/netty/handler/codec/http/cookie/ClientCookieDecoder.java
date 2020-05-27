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

import org.jboss.netty.handler.codec.http.HttpHeaderDateFormat;
import org.jboss.netty.handler.codec.http.cookie.CookieHeaderNames.SameSite;

import java.text.ParsePosition;
import java.util.Date;

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
        if (header == null) {
            throw new NullPointerException("header");
        }
        final int headerLen = header.length();

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
            int nameEnd = i;
            int valueBegin = -1;
            int valueEnd = -1;

            if (i != headerLen) {
                keyValLoop: for (;;) {

                    char curChar = header.charAt(i);
                    if (curChar == ';') {
                        // NAME; (no value till ';')
                        nameEnd = i;
                        valueBegin = valueEnd = -1;
                        break keyValLoop;

                    } else if (curChar == '=') {
                        // NAME=VALUE
                        nameEnd = i;
                        i++;
                        if (i == headerLen) {
                            // NAME= (empty value, i.e. nothing after '=')
                            valueBegin = valueEnd = 0;
                            break keyValLoop;
                        }

                        valueBegin = i;
                        // NAME=VALUE;
                        int semiPos = header.indexOf(';', i);
                        valueEnd = i = semiPos > 0 ? semiPos : headerLen;
                        break keyValLoop;
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

                cookieBuilder = new CookieBuilder(cookie);
            } else {
                // cookie attribute
                String attrValue = valueBegin == -1 ? null : header.substring(valueBegin, valueEnd);
                cookieBuilder.appendAttribute(header, nameBegin, nameEnd, attrValue);
            }
        }
        return cookieBuilder.cookie();
    }

    private static class CookieBuilder {

        private final DefaultCookie cookie;
        private String domain;
        private String path;
        private int maxAge = Integer.MIN_VALUE;
        private String expires;
        private boolean secure;
        private boolean httpOnly;
        private SameSite sameSite;

        public CookieBuilder(DefaultCookie cookie) {
            this.cookie = cookie;
        }

        private int mergeMaxAgeAndExpire(int maxAge, String expires) {
            // max age has precedence over expires
            if (maxAge != Integer.MIN_VALUE) {
                return maxAge;
            } else if (expires != null) {
                Date expiresDate = HttpHeaderDateFormat.get().parse(expires, new ParsePosition(0));
                if (expiresDate != null) {
                    long maxAgeMillis = expiresDate.getTime() - System.currentTimeMillis();
                    return (int) (maxAgeMillis / 1000 + (maxAgeMillis % 1000 != 0 ? 1 : 0));
                }
            }
            return Integer.MIN_VALUE;
        }

        public Cookie cookie() {
            cookie.setDomain(domain);
            cookie.setPath(path);
            cookie.setMaxAge(mergeMaxAgeAndExpire(maxAge, expires));
            cookie.setSecure(secure);
            cookie.setHttpOnly(httpOnly);
            cookie.setSameSite(sameSite);
            return cookie;
        }

        /**
         * Parse and store a key-value pair. First one is considered to be the
         * cookie name/value. Unknown attribute names are silently discarded.
         *
         * @param header
         *            the HTTP header
         * @param keyStart
         *            where the key starts in the header
         * @param keyEnd
         *            where the key ends in the header
         * @param value
         *            the decoded value
         */
        public void appendAttribute(String header, int keyStart, int keyEnd,
                String value) {
            setCookieAttribute(header, keyStart, keyEnd, value);
        }

        private void setCookieAttribute(String header, int keyStart,
                int keyEnd, String value) {
            int length = keyEnd - keyStart;

            if (length == 4) {
                parse4(header, keyStart, value);
            } else if (length == 6) {
                parse6(header, keyStart, value);
            } else if (length == 7) {
                parse7(header, keyStart, value);
            } else if (length == 8) {
                parse8(header, keyStart, value);
            }
        }

        private void parse4(String header, int nameStart, String value) {
            if (header.regionMatches(true, nameStart, CookieHeaderNames.PATH, 0, 4)) {
                path = value;
            }
        }

        private void parse6(String header, int nameStart, String value) {
            if (header.regionMatches(true, nameStart, CookieHeaderNames.DOMAIN, 0, 5)) {
                domain = value.length() > 0 ? value.toString() : null;
            } else if (header.regionMatches(true, nameStart, CookieHeaderNames.SECURE, 0, 5)) {
                secure = true;
            }
        }

        private void setExpire(String value) {
            expires = value;
        }

        private void setMaxAge(String value) {
            try {
                maxAge = Math.max(Integer.valueOf(value), 0);
            } catch (NumberFormatException e1) {
                // ignore failure to parse -> treat as session cookie
            }
        }

        private void parse7(String header, int nameStart, String value) {
            if (header.regionMatches(true, nameStart, CookieHeaderNames.EXPIRES, 0, 7)) {
                setExpire(value);
            } else if (header.regionMatches(true, nameStart, CookieHeaderNames.MAX_AGE, 0, 7)) {
                setMaxAge(value);
            }
        }

        private void parse8(String header, int nameStart, String value) {
            if (header.regionMatches(true, nameStart, CookieHeaderNames.HTTPONLY, 0, 8)) {
                httpOnly = true;
            } else if (header.regionMatches(true, nameStart, CookieHeaderNames.SAMESITE, 0, 8)) {
                sameSite = SameSite.of(value);
            }
        }
    }
}
