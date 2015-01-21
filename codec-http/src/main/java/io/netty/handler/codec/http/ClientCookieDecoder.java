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

import java.text.ParsePosition;
import java.util.Date;

import static io.netty.handler.codec.http.CookieEncoderUtil.*;

/**
 * A <a href="http://tools.ietf.org/html/rfc6265">RFC6265</a> compliant cookie decoder to be used client side.
 *
 * It will store the raw value in {@link Cookie#setRawValue(String)} so it can be
 * eventually sent back to the Origin server as is.
 *
 * @see ClientCookieEncoder
 */
public final class ClientCookieDecoder {

    /**
     * Decodes the specified Set-Cookie HTTP header value into a {@link Cookie}.
     *
     * @return the decoded {@link Cookie}
     */
    public static Cookie decode(String header) {

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

            int newNameStart = i;
            int newNameEnd = i;
            String value, rawValue;

            if (i == headerLen) {
                value = rawValue = null;
            } else {
                keyValLoop: for (;;) {

                    char curChar = header.charAt(i);
                    if (curChar == ';') {
                        // NAME; (no value till ';')
                        newNameEnd = i;
                        value = rawValue = null;
                        break keyValLoop;
                    } else if (curChar == '=') {
                        // NAME=VALUE
                        newNameEnd = i;
                        i++;
                        if (i == headerLen) {
                            // NAME= (empty value, i.e. nothing after '=')
                            value = rawValue = "";
                            break keyValLoop;
                        }

                        int newValueStart = i;
                        char c = header.charAt(i);
                        if (c == '"') {
                            // NAME="VALUE"
                            StringBuilder newValueBuf = stringBuilder();

                            int rawValueStart = i;
                            int rawValueEnd = i;

                            final char q = c;
                            boolean hadBackslash = false;
                            i++;
                            for (;;) {
                                if (i == headerLen) {
                                    value = newValueBuf.toString();
                                    // only need to compute raw value for cookie
                                    // value which is in first position
                                    rawValue = header.substring(rawValueStart, rawValueEnd);
                                    break keyValLoop;
                                }
                                if (hadBackslash) {
                                    hadBackslash = false;
                                    c = header.charAt(i++);
                                    rawValueEnd = i;
                                    if (c == '\\' || c == '"') {
                                        newValueBuf.setCharAt(newValueBuf.length() - 1, c);
                                    } else {
                                        // Do not escape last backslash.
                                        newValueBuf.append(c);
                                    }
                                } else {
                                    c = header.charAt(i++);
                                    rawValueEnd = i;
                                    if (c == q) {
                                        value = newValueBuf.toString();
                                        // only need to compute raw value for
                                        // cookie value which is in first
                                        // position
                                        rawValue = header.substring(rawValueStart, rawValueEnd);
                                        break keyValLoop;
                                    }
                                    newValueBuf.append(c);
                                    if (c == '\\') {
                                        hadBackslash = true;
                                    }
                                }
                            }
                        } else {
                            // NAME=VALUE;
                            int semiPos = header.indexOf(';', i);
                            if (semiPos > 0) {
                                value = rawValue = header.substring(newValueStart, semiPos);
                                i = semiPos;
                            } else {
                                value = rawValue = header.substring(newValueStart);
                                i = headerLen;
                            }
                        }
                        break keyValLoop;
                    } else {
                        i++;
                    }

                    if (i == headerLen) {
                        // NAME (no value till the end of string)
                        newNameEnd = i;
                        value = rawValue = null;
                        break;
                    }
                }
            }

            if (cookieBuilder == null) {
                cookieBuilder = new CookieBuilder(header, newNameStart, newNameEnd, value, rawValue);
            } else {
                cookieBuilder.appendAttribute(header, newNameStart, newNameEnd, value);
            }
        }
        return cookieBuilder.cookie();
    }

    private static class CookieBuilder {

        private final String name;
        private final String value;
        private final String rawValue;
        private String domain;
        private String path;
        private long maxAge = Long.MIN_VALUE;
        private String expires;
        private boolean secure;
        private boolean httpOnly;

        public CookieBuilder(String header, int keyStart, int keyEnd,
                String value, String rawValue) {
            name = header.substring(keyStart, keyEnd);
            this.value = value;
            this.rawValue = rawValue;
        }

        private long mergeMaxAgeAndExpire(long maxAge, String expires) {
            // max age has precedence over expires
            if (maxAge != Long.MIN_VALUE) {
                return maxAge;
            } else if (expires != null) {
                Date expiresDate = HttpHeaderDateFormat.get().parse(expires, new ParsePosition(0));
                if (expiresDate != null) {
                    long maxAgeMillis = expiresDate.getTime() - System.currentTimeMillis();
                    return maxAgeMillis / 1000 + (maxAgeMillis % 1000 != 0 ? 1 : 0);
                }
            }
            return Long.MIN_VALUE;
        }

        public Cookie cookie() {
            if (name == null) {
                return null;
            }

            DefaultCookie cookie = new DefaultCookie(name, value);
            cookie.setValue(value);
            cookie.setRawValue(rawValue);
            cookie.setDomain(domain);
            cookie.setPath(path);
            cookie.setMaxAge(mergeMaxAgeAndExpire(maxAge, expires));
            cookie.setSecure(secure);
            cookie.setHttpOnly(httpOnly);
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
            if (header.regionMatches(true, nameStart, "Path", 0, 4)) {
                path = value;
            }
        }

        private void parse6(String header, int nameStart, String value) {
            if (header.regionMatches(true, nameStart, "Domain", 0, 5)) {
                domain = value.isEmpty() ? null : value;
            } else if (header.regionMatches(true, nameStart, "Secure", 0, 5)) {
                secure = true;
            }
        }

        private void setExpire(String value) {
            expires = value;
        }

        private void setMaxAge(String value) {
            try {
                maxAge = Math.max(Long.valueOf(value), 0L);
            } catch (NumberFormatException e1) {
                // ignore failure to parse -> treat as session cookie
            }
        }

        private void parse7(String header, int nameStart, String value) {
            if (header.regionMatches(true, nameStart, "Expires", 0, 7)) {
                setExpire(value);
            } else if (header.regionMatches(true, nameStart, "Max-Age", 0, 7)) {
                setMaxAge(value);
            }
        }

        private void parse8(String header, int nameStart, String value) {

            if (header.regionMatches(true, nameStart, "HttpOnly", 0, 8)) {
                httpOnly = true;
            }
        }
    }

    private ClientCookieDecoder() {
        // unused
    }
}
