/*
 * Copyright 2022 The Netty Project
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
package io.netty.handler.codec.http;

import io.netty.util.AsciiString;

import static io.netty.util.AsciiString.contentEqualsIgnoreCase;

/**
 * Functions used to perform various validations of HTTP header names and values.
 */
public final class HttpHeaderValidationUtil {
    private HttpHeaderValidationUtil() {
    }

    /**
     * Check if a header name is "connection related".
     * <p>
     * The <a href="https://datatracker.ietf.org/doc/html/rfc9110#section-7.6.1">RFC9110</a> only specify an incomplete
     * list of the following headers:
     *
     * <ul>
     *     <li><tt>Connection</tt></li>
     *     <li><tt>Proxy-Connection</tt></li>
     *     <li><tt>Keep-Alive</tt></li>
     *     <li><tt>TE</tt></li>
     *     <li><tt>Transfer-Encoding</tt></li>
     *     <li><tt>Upgrade</tt></li>
     * </ul>
     *
     * @param name the name of the header to check. The check is case-insensitive.
     * @param ignoreTeHeader {@code true} if the <tt>TE</tt> header should be ignored by this check.
     * This is relevant for HTTP/2 header validation, where the <tt>TE</tt> header has special rules.
     * @return {@code true} if the given header name is one of the specified connection-related headers.
     */
    @SuppressWarnings("deprecation") // We need to check for deprecated headers as well.
    public static boolean isConnectionHeader(CharSequence name, boolean ignoreTeHeader) {
        // These are the known standard and non-standard connection related headers:
        // - upgrade (7 chars)
        // - connection (10 chars)
        // - keep-alive (10 chars)
        // - proxy-connection (16 chars)
        // - transfer-encoding (17 chars)
        //
        // See https://datatracker.ietf.org/doc/html/rfc9113#section-8.2.2
        // and https://datatracker.ietf.org/doc/html/rfc9110#section-7.6.1
        // for the list of connection related headers.
        //
        // We scan for these based on the length, then double-check any matching name.
        int len = name.length();
        switch (len) {
            case 2: return ignoreTeHeader? false : contentEqualsIgnoreCase(name, HttpHeaderNames.TE);
            case 7: return contentEqualsIgnoreCase(name, HttpHeaderNames.UPGRADE);
            case 10: return contentEqualsIgnoreCase(name, HttpHeaderNames.CONNECTION) ||
                    contentEqualsIgnoreCase(name, HttpHeaderNames.KEEP_ALIVE);
            case 16: return contentEqualsIgnoreCase(name, HttpHeaderNames.PROXY_CONNECTION);
            case 17: return contentEqualsIgnoreCase(name, HttpHeaderNames.TRANSFER_ENCODING);
            default:
                return false;
        }
    }

    /**
     * If the given header is {@link HttpHeaderNames#TE} and the given header value is <em>not</em>
     * {@link HttpHeaderValues#TRAILERS}, then return {@code true}. Otherwie, {@code false}.
     * <p>
     * The string comparisons are case-insensitive.
     * <p>
     * This check is important for HTTP/2 header validation.
     *
     * @param name the header name to check if it is <tt>TE</tt> or not.
     * @param value the header value to check if it is something other than <tt>TRAILERS</tt>.
     * @return {@code true} only if the header name is <tt>TE</tt>, and the header value is <em>not</em>
     * <tt>TRAILERS</tt>. Otherwise, {@code false}.
     */
    public static boolean isTeNotTrailers(CharSequence name, CharSequence value) {
        if (name.length() == 2) {
            return contentEqualsIgnoreCase(name, HttpHeaderNames.TE) &&
                    !contentEqualsIgnoreCase(value, HttpHeaderValues.TRAILERS);
        }
        return false;
    }

    /**
     * Validate the given HTTP header value by searching for any illegal characters.
     *
     * @param value the HTTP header value to validate.
     * @return the index of the first illegal character found, or {@code -1} if there are none and the header value is
     * valid.
     */
    public static int validateValidHeaderValue(CharSequence value) {
        int length = value.length();
        if (length == 0) {
            return -1;
        }
        if (value instanceof AsciiString) {
            return verifyValidHeaderValueAsciiString((AsciiString) value);
        }
        return verifyValidHeaderValueCharSequence(value);
    }

    private static int verifyValidHeaderValueAsciiString(AsciiString value) {
        // Validate value to field-content rule.
        //  field-content  = field-vchar [ 1*( SP / HTAB ) field-vchar ]
        //  field-vchar    = VCHAR / obs-text
        //  VCHAR          = %x21-7E ; visible (printing) characters
        //  obs-text       = %x80-FF
        //  SP             = %x20
        //  HTAB           = %x09 ; horizontal tab
        //  See: https://datatracker.ietf.org/doc/html/rfc7230#section-3.2
        //  And: https://datatracker.ietf.org/doc/html/rfc5234#appendix-B.1
        final byte[] array = value.array();
        final int start = value.arrayOffset();
        int b = array[start] & 0xFF;
        if (b < 0x21 || b == 0x7F) {
            return 0;
        }
        int end = start + value.length();
        for (int i = start + 1; i < end; i++) {
            b = array[i] & 0xFF;
            if (b < 0x20 && b != 0x09 || b == 0x7F) {
                return i - start;
            }
        }
        return -1;
    }

    private static int verifyValidHeaderValueCharSequence(CharSequence value) {
        // Validate value to field-content rule.
        //  field-content  = field-vchar [ 1*( SP / HTAB ) field-vchar ]
        //  field-vchar    = VCHAR / obs-text
        //  VCHAR          = %x21-7E ; visible (printing) characters
        //  obs-text       = %x80-FF
        //  SP             = %x20
        //  HTAB           = %x09 ; horizontal tab
        //  See: https://datatracker.ietf.org/doc/html/rfc7230#section-3.2
        //  And: https://datatracker.ietf.org/doc/html/rfc5234#appendix-B.1
        int b = value.charAt(0);
        if (b < 0x21 || b == 0x7F) {
            return 0;
        }
        int length = value.length();
        for (int i = 1; i < length; i++) {
            b = value.charAt(i);
            if (b < 0x20 && b != 0x09 || b == 0x7F) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Validate a <a href="https://tools.ietf.org/html/rfc7230#section-3.2.6">token</a> contains only allowed
     * characters.
     * <p>
     * The <a href="https://tools.ietf.org/html/rfc2616#section-2.2">token</a> format is used for variety of HTTP
     * components, like  <a href="https://tools.ietf.org/html/rfc6265#section-4.1.1">cookie-name</a>,
     * <a href="https://tools.ietf.org/html/rfc7230#section-3.2.6">field-name</a> of a
     * <a href="https://tools.ietf.org/html/rfc7230#section-3.2">header-field</a>, or
     * <a href="https://tools.ietf.org/html/rfc7231#section-4">request method</a>.
     *
     * @param token the token to validate.
     * @return the index of the first invalid token character found, or {@code -1} if there are none.
     */
    public static int validateToken(CharSequence token) {
        return HttpUtil.validateToken(token);
    }
}
