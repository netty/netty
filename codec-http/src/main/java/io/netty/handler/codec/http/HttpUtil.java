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
package io.netty.handler.codec.http;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import io.netty.util.AsciiString;
import io.netty.util.CharsetUtil;
import io.netty.util.NetUtil;
import io.netty.util.internal.ObjectUtil;

import static io.netty.util.internal.StringUtil.COMMA;
import static io.netty.util.internal.ObjectUtil.checkPositiveOrZero;

/**
 * Utility methods useful in the HTTP context.
 */
public final class HttpUtil {

    private static final AsciiString CHARSET_EQUALS = AsciiString.of(HttpHeaderValues.CHARSET + "=");
    private static final AsciiString SEMICOLON = AsciiString.cached(";");
    private static final String COMMA_STRING = String.valueOf(COMMA);

    private HttpUtil() { }

    /**
     * Determine if a uri is in origin-form according to
     * <a href="https://tools.ietf.org/html/rfc7230#section-5.3">rfc7230, 5.3</a>.
     */
    public static boolean isOriginForm(URI uri) {
        return isOriginForm(uri.toString());
    }

    /**
     * Determine if a string uri is in origin-form according to
     * <a href="https://tools.ietf.org/html/rfc7230#section-5.3">rfc7230, 5.3</a>.
     */
    public static boolean isOriginForm(String uri) {
        return uri.startsWith("/");
    }

    /**
     * Determine if a uri is in asterisk-form according to
     * <a href="https://tools.ietf.org/html/rfc7230#section-5.3">rfc7230, 5.3</a>.
     */
    public static boolean isAsteriskForm(URI uri) {
        return isAsteriskForm(uri.toString());
    }

    /**
     * Determine if a string uri is in asterisk-form according to
     * <a href="https://tools.ietf.org/html/rfc7230#section-5.3">rfc7230, 5.3</a>.
     */
    public static boolean isAsteriskForm(String uri) {
        return "*".equals(uri);
    }

    /**
     * Returns {@code true} if and only if the connection can remain open and
     * thus 'kept alive'.  This methods respects the value of the.
     *
     * {@code "Connection"} header first and then the return value of
     * {@link HttpVersion#isKeepAliveDefault()}.
     */
    public static boolean isKeepAlive(HttpMessage message) {
        return !message.headers().containsValue(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE, true) &&
               (message.protocolVersion().isKeepAliveDefault() ||
                message.headers().containsValue(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE, true));
    }

    /**
     * Sets the value of the {@code "Connection"} header depending on the
     * protocol version of the specified message. This getMethod sets or removes
     * the {@code "Connection"} header depending on what the default keep alive
     * mode of the message's protocol version is, as specified by
     * {@link HttpVersion#isKeepAliveDefault()}.
     * <ul>
     * <li>If the connection is kept alive by default:
     *     <ul>
     *     <li>set to {@code "close"} if {@code keepAlive} is {@code false}.</li>
     *     <li>remove otherwise.</li>
     *     </ul></li>
     * <li>If the connection is closed by default:
     *     <ul>
     *     <li>set to {@code "keep-alive"} if {@code keepAlive} is {@code true}.</li>
     *     <li>remove otherwise.</li>
     *     </ul></li>
     * </ul>
     * @see #setKeepAlive(HttpHeaders, HttpVersion, boolean)
     */
    public static void setKeepAlive(HttpMessage message, boolean keepAlive) {
        setKeepAlive(message.headers(), message.protocolVersion(), keepAlive);
    }

    /**
     * Sets the value of the {@code "Connection"} header depending on the
     * protocol version of the specified message. This getMethod sets or removes
     * the {@code "Connection"} header depending on what the default keep alive
     * mode of the message's protocol version is, as specified by
     * {@link HttpVersion#isKeepAliveDefault()}.
     * <ul>
     * <li>If the connection is kept alive by default:
     *     <ul>
     *     <li>set to {@code "close"} if {@code keepAlive} is {@code false}.</li>
     *     <li>remove otherwise.</li>
     *     </ul></li>
     * <li>If the connection is closed by default:
     *     <ul>
     *     <li>set to {@code "keep-alive"} if {@code keepAlive} is {@code true}.</li>
     *     <li>remove otherwise.</li>
     *     </ul></li>
     * </ul>
     */
    public static void setKeepAlive(HttpHeaders h, HttpVersion httpVersion, boolean keepAlive) {
        if (httpVersion.isKeepAliveDefault()) {
            if (keepAlive) {
                h.remove(HttpHeaderNames.CONNECTION);
            } else {
                h.set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
            }
        } else {
            if (keepAlive) {
                h.set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            } else {
                h.remove(HttpHeaderNames.CONNECTION);
            }
        }
    }

    /**
     * Returns the length of the content. Please note that this value is
     * not retrieved from {@link HttpContent#content()} but from the
     * {@code "Content-Length"} header, and thus they are independent from each
     * other.
     *
     * @return the content length
     *
     * @throws NumberFormatException
     *         if the message does not have the {@code "Content-Length"} header
     *         or its value is not a number
     */
    public static long getContentLength(HttpMessage message) {
        String value = message.headers().get(HttpHeaderNames.CONTENT_LENGTH);
        if (value != null) {
            return Long.parseLong(value);
        }

        // We know the content length if it's a Web Socket message even if
        // Content-Length header is missing.
        long webSocketContentLength = getWebSocketContentLength(message);
        if (webSocketContentLength >= 0) {
            return webSocketContentLength;
        }

        // Otherwise we don't.
        throw new NumberFormatException("header not found: " + HttpHeaderNames.CONTENT_LENGTH);
    }

    /**
     * Returns the length of the content or the specified default value if the message does not have the {@code
     * "Content-Length" header}. Please note that this value is not retrieved from {@link HttpContent#content()} but
     * from the {@code "Content-Length"} header, and thus they are independent from each other.
     *
     * @param message      the message
     * @param defaultValue the default value
     * @return the content length or the specified default value
     * @throws NumberFormatException if the {@code "Content-Length"} header does not parse as a long
     */
    public static long getContentLength(HttpMessage message, long defaultValue) {
        String value = message.headers().get(HttpHeaderNames.CONTENT_LENGTH);
        if (value != null) {
            return Long.parseLong(value);
        }

        // We know the content length if it's a Web Socket message even if
        // Content-Length header is missing.
        long webSocketContentLength = getWebSocketContentLength(message);
        if (webSocketContentLength >= 0) {
            return webSocketContentLength;
        }

        // Otherwise we don't.
        return defaultValue;
    }

    /**
     * Get an {@code int} representation of {@link #getContentLength(HttpMessage, long)}.
     *
     * @return the content length or {@code defaultValue} if this message does
     *         not have the {@code "Content-Length"} header.
     *
     * @throws NumberFormatException if the {@code "Content-Length"} header does not parse as an int
     */
    public static int getContentLength(HttpMessage message, int defaultValue) {
        return (int) Math.min(Integer.MAX_VALUE, getContentLength(message, (long) defaultValue));
    }

    /**
     * Returns the content length of the specified web socket message. If the
     * specified message is not a web socket message, {@code -1} is returned.
     */
    static int getWebSocketContentLength(HttpMessage message) {
        // WebSocket messages have constant content-lengths.
        HttpHeaders h = message.headers();
        if (message instanceof HttpRequest) {
            HttpRequest req = (HttpRequest) message;
            if (HttpMethod.GET.equals(req.method()) &&
                    h.contains(HttpHeaderNames.SEC_WEBSOCKET_KEY1) &&
                    h.contains(HttpHeaderNames.SEC_WEBSOCKET_KEY2)) {
                return 8;
            }
        } else if (message instanceof HttpResponse) {
            HttpResponse res = (HttpResponse) message;
            if (res.status().code() == 101 &&
                    h.contains(HttpHeaderNames.SEC_WEBSOCKET_ORIGIN) &&
                    h.contains(HttpHeaderNames.SEC_WEBSOCKET_LOCATION)) {
                return 16;
            }
        }

        // Not a web socket message
        return -1;
    }

    /**
     * Sets the {@code "Content-Length"} header.
     */
    public static void setContentLength(HttpMessage message, long length) {
        message.headers().set(HttpHeaderNames.CONTENT_LENGTH, length);
    }

    public static boolean isContentLengthSet(HttpMessage m) {
        return m.headers().contains(HttpHeaderNames.CONTENT_LENGTH);
    }

    /**
     * Returns {@code true} if and only if the specified message contains an expect header and the only expectation
     * present is the 100-continue expectation. Note that this method returns {@code false} if the expect header is
     * not valid for the message (e.g., the message is a response, or the version on the message is HTTP/1.0).
     *
     * @param message the message
     * @return {@code true} if and only if the expectation 100-continue is present and it is the only expectation
     * present
     */
    public static boolean is100ContinueExpected(HttpMessage message) {
        return isExpectHeaderValid(message)
          // unquoted tokens in the expect header are case-insensitive, thus 100-continue is case insensitive
          && message.headers().contains(HttpHeaderNames.EXPECT, HttpHeaderValues.CONTINUE, true);
    }

    /**
     * Returns {@code true} if the specified message contains an expect header specifying an expectation that is not
     * supported. Note that this method returns {@code false} if the expect header is not valid for the message
     * (e.g., the message is a response, or the version on the message is HTTP/1.0).
     *
     * @param message the message
     * @return {@code true} if and only if an expectation is present that is not supported
     */
    static boolean isUnsupportedExpectation(HttpMessage message) {
        if (!isExpectHeaderValid(message)) {
            return false;
        }

        final String expectValue = message.headers().get(HttpHeaderNames.EXPECT);
        return expectValue != null && !HttpHeaderValues.CONTINUE.toString().equalsIgnoreCase(expectValue);
    }

    private static boolean isExpectHeaderValid(final HttpMessage message) {
        /*
         * Expect: 100-continue is for requests only and it works only on HTTP/1.1 or later. Note further that RFC 7231
         * section 5.1.1 says "A server that receives a 100-continue expectation in an HTTP/1.0 request MUST ignore
         * that expectation."
         */
        return message instanceof HttpRequest &&
                message.protocolVersion().compareTo(HttpVersion.HTTP_1_1) >= 0;
    }

    /**
     * Sets or removes the {@code "Expect: 100-continue"} header to / from the
     * specified message. If {@code expected} is {@code true},
     * the {@code "Expect: 100-continue"} header is set and all other previous
     * {@code "Expect"} headers are removed.  Otherwise, all {@code "Expect"}
     * headers are removed completely.
     */
    public static void set100ContinueExpected(HttpMessage message, boolean expected) {
        if (expected) {
            message.headers().set(HttpHeaderNames.EXPECT, HttpHeaderValues.CONTINUE);
        } else {
            message.headers().remove(HttpHeaderNames.EXPECT);
        }
    }

    /**
     * Checks to see if the transfer encoding in a specified {@link HttpMessage} is chunked
     *
     * @param message The message to check
     * @return True if transfer encoding is chunked, otherwise false
     */
    public static boolean isTransferEncodingChunked(HttpMessage message) {
        return message.headers().containsValue(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED, true);
    }

    /**
     * Set the {@link HttpHeaderNames#TRANSFER_ENCODING} to either include {@link HttpHeaderValues#CHUNKED} if
     * {@code chunked} is {@code true}, or remove {@link HttpHeaderValues#CHUNKED} if {@code chunked} is {@code false}.
     *
     * @param m The message which contains the headers to modify.
     * @param chunked if {@code true} then include {@link HttpHeaderValues#CHUNKED} in the headers. otherwise remove
     * {@link HttpHeaderValues#CHUNKED} from the headers.
     */
    public static void setTransferEncodingChunked(HttpMessage m, boolean chunked) {
        if (chunked) {
            m.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
            m.headers().remove(HttpHeaderNames.CONTENT_LENGTH);
        } else {
            List<String> encodings = m.headers().getAll(HttpHeaderNames.TRANSFER_ENCODING);
            if (encodings.isEmpty()) {
                return;
            }
            List<CharSequence> values = new ArrayList<CharSequence>(encodings);
            Iterator<CharSequence> valuesIt = values.iterator();
            while (valuesIt.hasNext()) {
                CharSequence value = valuesIt.next();
                if (HttpHeaderValues.CHUNKED.contentEqualsIgnoreCase(value)) {
                    valuesIt.remove();
                }
            }
            if (values.isEmpty()) {
                m.headers().remove(HttpHeaderNames.TRANSFER_ENCODING);
            } else {
                m.headers().set(HttpHeaderNames.TRANSFER_ENCODING, values);
            }
        }
    }

    /**
     * Fetch charset from message's Content-Type header.
     *
     * @param message entity to fetch Content-Type header from
     * @return the charset from message's Content-Type header or {@link CharsetUtil#ISO_8859_1}
     * if charset is not presented or unparsable
     */
    public static Charset getCharset(HttpMessage message) {
        return getCharset(message, CharsetUtil.ISO_8859_1);
    }

    /**
     * Fetch charset from Content-Type header value.
     *
     * @param contentTypeValue Content-Type header value to parse
     * @return the charset from message's Content-Type header or {@link CharsetUtil#ISO_8859_1}
     * if charset is not presented or unparsable
     */
    public static Charset getCharset(CharSequence contentTypeValue) {
        if (contentTypeValue != null) {
            return getCharset(contentTypeValue, CharsetUtil.ISO_8859_1);
        } else {
            return CharsetUtil.ISO_8859_1;
        }
    }

    /**
     * Fetch charset from message's Content-Type header.
     *
     * @param message        entity to fetch Content-Type header from
     * @param defaultCharset result to use in case of empty, incorrect or doesn't contain required part header value
     * @return the charset from message's Content-Type header or {@code defaultCharset}
     * if charset is not presented or unparsable
     */
    public static Charset getCharset(HttpMessage message, Charset defaultCharset) {
        CharSequence contentTypeValue = message.headers().get(HttpHeaderNames.CONTENT_TYPE);
        if (contentTypeValue != null) {
            return getCharset(contentTypeValue, defaultCharset);
        } else {
            return defaultCharset;
        }
    }

    /**
     * Fetch charset from Content-Type header value.
     *
     * @param contentTypeValue Content-Type header value to parse
     * @param defaultCharset   result to use in case of empty, incorrect or doesn't contain required part header value
     * @return the charset from message's Content-Type header or {@code defaultCharset}
     * if charset is not presented or unparsable
     */
    public static Charset getCharset(CharSequence contentTypeValue, Charset defaultCharset) {
        if (contentTypeValue != null) {
            CharSequence charsetRaw = getCharsetAsSequence(contentTypeValue);
            if (charsetRaw != null) {
                if (charsetRaw.length() > 2) { // at least contains 2 quotes(")
                    if (charsetRaw.charAt(0) == '"' && charsetRaw.charAt(charsetRaw.length() - 1) == '"') {
                        charsetRaw = charsetRaw.subSequence(1, charsetRaw.length() - 1);
                    }
                }
                try {
                    return Charset.forName(charsetRaw.toString());
                } catch (IllegalCharsetNameException ignored) {
                    // just return the default charset
                } catch (UnsupportedCharsetException ignored) {
                    // just return the default charset
                }
            }
        }
        return defaultCharset;
    }

    /**
     * Fetch charset from message's Content-Type header as a char sequence.
     *
     * A lot of sites/possibly clients have charset="CHARSET", for example charset="utf-8". Or "utf8" instead of "utf-8"
     * This is not according to standard, but this method provide an ability to catch desired mistakes manually in code
     *
     * @param message entity to fetch Content-Type header from
     * @return the {@code CharSequence} with charset from message's Content-Type header
     * or {@code null} if charset is not presented
     * @deprecated use {@link #getCharsetAsSequence(HttpMessage)}
     */
    @Deprecated
    public static CharSequence getCharsetAsString(HttpMessage message) {
        return getCharsetAsSequence(message);
    }

    /**
     * Fetch charset from message's Content-Type header as a char sequence.
     *
     * A lot of sites/possibly clients have charset="CHARSET", for example charset="utf-8". Or "utf8" instead of "utf-8"
     * This is not according to standard, but this method provide an ability to catch desired mistakes manually in code
     *
     * @return the {@code CharSequence} with charset from message's Content-Type header
     * or {@code null} if charset is not presented
     */
    public static CharSequence getCharsetAsSequence(HttpMessage message) {
        CharSequence contentTypeValue = message.headers().get(HttpHeaderNames.CONTENT_TYPE);
        if (contentTypeValue != null) {
            return getCharsetAsSequence(contentTypeValue);
        } else {
            return null;
        }
    }

    /**
     * Fetch charset from Content-Type header value as a char sequence.
     *
     * A lot of sites/possibly clients have charset="CHARSET", for example charset="utf-8". Or "utf8" instead of "utf-8"
     * This is not according to standard, but this method provide an ability to catch desired mistakes manually in code
     *
     * @param contentTypeValue Content-Type header value to parse
     * @return the {@code CharSequence} with charset from message's Content-Type header
     * or {@code null} if charset is not presented
     * @throws NullPointerException in case if {@code contentTypeValue == null}
     */
    public static CharSequence getCharsetAsSequence(CharSequence contentTypeValue) {
        ObjectUtil.checkNotNull(contentTypeValue, "contentTypeValue");

        int indexOfCharset = AsciiString.indexOfIgnoreCaseAscii(contentTypeValue, CHARSET_EQUALS, 0);
        if (indexOfCharset == AsciiString.INDEX_NOT_FOUND) {
            return null;
        }

        int indexOfEncoding = indexOfCharset + CHARSET_EQUALS.length();
        if (indexOfEncoding < contentTypeValue.length()) {
            CharSequence charsetCandidate = contentTypeValue.subSequence(indexOfEncoding, contentTypeValue.length());
            int indexOfSemicolon = AsciiString.indexOfIgnoreCaseAscii(charsetCandidate, SEMICOLON, 0);
            if (indexOfSemicolon == AsciiString.INDEX_NOT_FOUND) {
                return charsetCandidate;
            }

            return charsetCandidate.subSequence(0, indexOfSemicolon);
        }

        return null;
    }

    /**
     * Fetch MIME type part from message's Content-Type header as a char sequence.
     *
     * @param message entity to fetch Content-Type header from
     * @return the MIME type as a {@code CharSequence} from message's Content-Type header
     * or {@code null} if content-type header or MIME type part of this header are not presented
     * <p/>
     * "content-type: text/html; charset=utf-8" - "text/html" will be returned <br/>
     * "content-type: text/html" - "text/html" will be returned <br/>
     * "content-type: " or no header - {@code null} we be returned
     */
    public static CharSequence getMimeType(HttpMessage message) {
        CharSequence contentTypeValue = message.headers().get(HttpHeaderNames.CONTENT_TYPE);
        if (contentTypeValue != null) {
            return getMimeType(contentTypeValue);
        } else {
            return null;
        }
    }

    /**
     * Fetch MIME type part from Content-Type header value as a char sequence.
     *
     * @param contentTypeValue Content-Type header value to parse
     * @return the MIME type as a {@code CharSequence} from message's Content-Type header
     * or {@code null} if content-type header or MIME type part of this header are not presented
     * <p/>
     * "content-type: text/html; charset=utf-8" - "text/html" will be returned <br/>
     * "content-type: text/html" - "text/html" will be returned <br/>
     * "content-type: empty header - {@code null} we be returned
     * @throws NullPointerException in case if {@code contentTypeValue == null}
     */
    public static CharSequence getMimeType(CharSequence contentTypeValue) {
        ObjectUtil.checkNotNull(contentTypeValue, "contentTypeValue");

        int indexOfSemicolon = AsciiString.indexOfIgnoreCaseAscii(contentTypeValue, SEMICOLON, 0);
        if (indexOfSemicolon != AsciiString.INDEX_NOT_FOUND) {
            return contentTypeValue.subSequence(0, indexOfSemicolon);
        } else {
            return contentTypeValue.length() > 0 ? contentTypeValue : null;
        }
    }

    /**
     * Formats the host string of an address so it can be used for computing an HTTP component
     * such as a URL or a Host header
     *
     * @param addr the address
     * @return the formatted String
     */
    public static String formatHostnameForHttp(InetSocketAddress addr) {
        String hostString = NetUtil.getHostname(addr);
        if (NetUtil.isValidIpV6Address(hostString)) {
            if (!addr.isUnresolved()) {
                hostString = NetUtil.toAddressString(addr.getAddress());
            } else if (hostString.charAt(0) == '[' && hostString.charAt(hostString.length() - 1) == ']') {
                // If IPv6 address already contains brackets, let's return as is.
                return hostString;
            }

            return '[' + hostString + ']';
        }
        return hostString;
    }

    /**
     * Validates, and optionally extracts the content length from headers. This method is not intended for
     * general use, but is here to be shared between HTTP/1 and HTTP/2 parsing.
     *
     * @param contentLengthFields the content-length header fields.
     * @param isHttp10OrEarlier {@code true} if we are handling HTTP/1.0 or earlier
     * @param allowDuplicateContentLengths {@code true}  if multiple, identical-value content lengths should be allowed.
     * @return the normalized content length from the headers or {@code -1} if the fields were empty.
     * @throws IllegalArgumentException if the content-length fields are not valid
     */
    public static long normalizeAndGetContentLength(
            List<? extends CharSequence> contentLengthFields, boolean isHttp10OrEarlier,
            boolean allowDuplicateContentLengths) {
        if (contentLengthFields.isEmpty()) {
            return -1;
        }

        // Guard against multiple Content-Length headers as stated in
        // https://tools.ietf.org/html/rfc7230#section-3.3.2:
        //
        // If a message is received that has multiple Content-Length header
        //   fields with field-values consisting of the same decimal value, or a
        //   single Content-Length header field with a field value containing a
        //   list of identical decimal values (e.g., "Content-Length: 42, 42"),
        //   indicating that duplicate Content-Length header fields have been
        //   generated or combined by an upstream message processor, then the
        //   recipient MUST either reject the message as invalid or replace the
        //   duplicated field-values with a single valid Content-Length field
        //   containing that decimal value prior to determining the message body
        //   length or forwarding the message.
        String firstField = contentLengthFields.get(0).toString();
        boolean multipleContentLengths =
                contentLengthFields.size() > 1 || firstField.indexOf(COMMA) >= 0;

        if (multipleContentLengths && !isHttp10OrEarlier) {
            if (allowDuplicateContentLengths) {
                // Find and enforce that all Content-Length values are the same
                String firstValue = null;
                for (CharSequence field : contentLengthFields) {
                    String[] tokens = field.toString().split(COMMA_STRING, -1);
                    for (String token : tokens) {
                        String trimmed = token.trim();
                        if (firstValue == null) {
                            firstValue = trimmed;
                        } else if (!trimmed.equals(firstValue)) {
                            throw new IllegalArgumentException(
                                    "Multiple Content-Length values found: " + contentLengthFields);
                        }
                    }
                }
                // Replace the duplicated field-values with a single valid Content-Length field
                firstField = firstValue;
            } else {
                // Reject the message as invalid
                throw new IllegalArgumentException(
                        "Multiple Content-Length values found: " + contentLengthFields);
            }
        }
        // Ensure we not allow sign as part of the content-length:
        // See https://github.com/squid-cache/squid/security/advisories/GHSA-qf3v-rc95-96j5
        if (firstField.isEmpty() || !Character.isDigit(firstField.charAt(0))) {
            // Reject the message as invalid
            throw new IllegalArgumentException(
                    "Content-Length value is not a number: " + firstField);
        }
        try {
            final long value = Long.parseLong(firstField);
            return checkPositiveOrZero(value, "Content-Length value");
        } catch (NumberFormatException e) {
            // Reject the message as invalid
            throw new IllegalArgumentException(
                    "Content-Length value is not a number: " + firstField, e);
        }
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
    static int validateToken(CharSequence token) {
        if (token instanceof AsciiString) {
            return validateAsciiStringToken((AsciiString) token);
        }
        return validateCharSequenceToken(token);
    }

    /**
     * Validate that an {@link AsciiString} contain onlu valid
     * <a href="https://tools.ietf.org/html/rfc7230#section-3.2.6">token</a> characters.
     *
     * @param token the ascii string to validate.
     */
    private static int validateAsciiStringToken(AsciiString token) {
        byte[] array = token.array();
        for (int i = token.arrayOffset(), len = token.arrayOffset() + token.length(); i < len; i++) {
            if (!BitSet128.contains(array[i], TOKEN_CHARS_HIGH, TOKEN_CHARS_LOW)) {
                return i - token.arrayOffset();
            }
        }
        return -1;
    }

    /**
     * Validate that a {@link CharSequence} contain onlu valid
     * <a href="https://tools.ietf.org/html/rfc7230#section-3.2.6">token</a> characters.
     *
     * @param token the character sequence to validate.
     */
    private static int validateCharSequenceToken(CharSequence token) {
        for (int i = 0, len = token.length(); i < len; i++) {
            byte value = (byte) token.charAt(i);
            if (!BitSet128.contains(value, TOKEN_CHARS_HIGH, TOKEN_CHARS_LOW)) {
                return i;
            }
        }
        return -1;
    }

    private static final long TOKEN_CHARS_HIGH;
    private static final long TOKEN_CHARS_LOW;
    static {
        // HEADER
        // header-field   = field-name ":" OWS field-value OWS
        //
        // field-name     = token
        // token          = 1*tchar
        //
        // tchar          = "!" / "#" / "$" / "%" / "&" / "'" / "*"
        //                    / "+" / "-" / "." / "^" / "_" / "`" / "|" / "~"
        //                    / DIGIT / ALPHA
        //                    ; any VCHAR, except delimiters.
        //  Delimiters are chosen
        //   from the set of US-ASCII visual characters not allowed in a token
        //   (DQUOTE and "(),/:;<=>?@[\]{}")
        //
        // COOKIE
        // cookie-pair       = cookie-name "=" cookie-value
        // cookie-name       = token
        // token          = 1*<any CHAR except CTLs or separators>
        // CTL = <any US-ASCII control character
        //       (octets 0 - 31) and DEL (127)>
        // separators     = "(" | ")" | "<" | ">" | "@"
        //                      | "," | ";" | ":" | "\" | <">
        //                      | "/" | "[" | "]" | "?" | "="
        //                      | "{" | "}" | SP | HT
        //
        // field-name's token is equivalent to cookie-name's token, we can reuse the tchar mask for both:
        BitSet128 tokenChars = new BitSet128()
                .range('0', '9').range('a', 'z').range('A', 'Z') // Alphanumeric.
                .bits('-', '.', '_', '~') // Unreserved characters.
                .bits('!', '#', '$', '%', '&', '\'', '*', '+', '^', '`', '|'); // Token special characters.
        TOKEN_CHARS_HIGH = tokenChars.high();
        TOKEN_CHARS_LOW = tokenChars.low();
    }

    private static final class BitSet128 {
        private long high;
        private long low;

        BitSet128 range(char fromInc, char toInc) {
            for (int bit = fromInc; bit <= toInc; bit++) {
                if (bit < 64) {
                    low |= 1L << bit;
                } else {
                    high |= 1L << bit - 64;
                }
            }
            return this;
        }

        BitSet128 bits(char... bits) {
            for (char bit : bits) {
                if (bit < 64) {
                    low |= 1L << bit;
                } else {
                    high |= 1L << bit - 64;
                }
            }
            return this;
        }

        long high() {
            return high;
        }

        long low() {
            return low;
        }

        static boolean contains(byte bit, long high, long low) {
            if (bit < 0) {
                return false;
            }
            if (bit < 64) {
                return 0 != (low & 1L << bit);
            }
            return 0 != (high & 1L << bit - 64);
        }
    }
}
