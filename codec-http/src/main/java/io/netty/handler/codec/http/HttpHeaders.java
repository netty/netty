/*
 * Copyright 2012 The Netty Project
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

import io.netty.buffer.ByteBuf;

import java.text.ParseException;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import static io.netty.handler.codec.http.HttpConstants.*;

/**
 * Provides the constants for the standard HTTP header names and values and
 * commonly used utility methods that accesses an {@link HttpMessage}.
 */
public abstract class HttpHeaders implements Iterable<Map.Entry<String, String>> {

    private static final byte[] HEADER_SEPERATOR = { COLON, SP };
    private static final byte[] CRLF = { CR, LF };
    private static final CharSequence CONTENT_LENGTH_ENTITY = newEntity(Names.CONTENT_LENGTH);
    private static final CharSequence CONNECTION_ENTITY = newEntity(Names.CONNECTION);
    private static final CharSequence CLOSE_ENTITY = newEntity(Values.CLOSE);
    private static final CharSequence KEEP_ALIVE_ENTITY = newEntity(Values.KEEP_ALIVE);
    private static final CharSequence HOST_ENTITY = newEntity(Names.HOST);
    private static final CharSequence DATE_ENTITY = newEntity(Names.DATE);
    private static final CharSequence EXPECT_ENTITY = newEntity(Names.EXPECT);
    private static final CharSequence CONTINUE_ENTITY = newEntity(Values.CONTINUE);
    private static final CharSequence TRANSFER_ENCODING_ENTITY = newEntity(Names.TRANSFER_ENCODING);
    private static final CharSequence CHUNKED_ENTITY = newEntity(Values.CHUNKED);
    private static final CharSequence SEC_WEBSOCKET_KEY1_ENTITY = newEntity(Names.SEC_WEBSOCKET_KEY1);
    private static final CharSequence SEC_WEBSOCKET_KEY2_ENTITY = newEntity(Names.SEC_WEBSOCKET_KEY2);
    private static final CharSequence SEC_WEBSOCKET_ORIGIN_ENTITY = newEntity(Names.SEC_WEBSOCKET_ORIGIN);
    private static final CharSequence SEC_WEBSOCKET_LOCATION_ENTITY = newEntity(Names.SEC_WEBSOCKET_LOCATION);

    public static final HttpHeaders EMPTY_HEADERS = new HttpHeaders() {
        @Override
        public String get(String name) {
            return null;
        }

        @Override
        public List<String> getAll(String name) {
            return Collections.emptyList();
        }

        @Override
        public List<Entry<String, String>> entries() {
            return Collections.emptyList();
        }

        @Override
        public boolean contains(String name) {
            return false;
        }

        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public Set<String> names() {
            return Collections.emptySet();
        }

        @Override
        public HttpHeaders add(String name, Object value) {
            throw new UnsupportedOperationException("read only");
        }

        @Override
        public HttpHeaders add(String name, Iterable<?> values) {
            throw new UnsupportedOperationException("read only");
        }

        @Override
        public HttpHeaders set(String name, Object value) {
            throw new UnsupportedOperationException("read only");
        }

        @Override
        public HttpHeaders set(String name, Iterable<?> values) {
            throw new UnsupportedOperationException("read only");
        }

        @Override
        public HttpHeaders remove(String name) {
            throw new UnsupportedOperationException("read only");
        }

        @Override
        public HttpHeaders clear() {
            throw new UnsupportedOperationException("read only");
        }

        @Override
        public Iterator<Entry<String, String>> iterator() {
            return entries().iterator();
        }
    };

    /**
     * Standard HTTP header names.
     */
    public static final class Names {
        /**
         * {@code "Accept"}
         */
        public static final String ACCEPT = "Accept";
        /**
         * {@code "Accept-Charset"}
         */
        public static final String ACCEPT_CHARSET = "Accept-Charset";
        /**
         * {@code "Accept-Encoding"}
         */
        public static final String ACCEPT_ENCODING = "Accept-Encoding";
        /**
         * {@code "Accept-Language"}
         */
        public static final String ACCEPT_LANGUAGE = "Accept-Language";
        /**
         * {@code "Accept-Ranges"}
         */
        public static final String ACCEPT_RANGES = "Accept-Ranges";
        /**
         * {@code "Accept-Patch"}
         */
        public static final String ACCEPT_PATCH = "Accept-Patch";
        /**
         * {@code "Access-Control-Allow-Credentials"}
         */
        public static final String ACCESS_CONTROL_ALLOW_CREDENTIALS = "Access-Control-Allow-Credentials";
        /**
         * {@code "Access-Control-Allow-Headers"}
         */
        public static final String ACCESS_CONTROL_ALLOW_HEADERS = "Access-Control-Allow-Headers";
        /**
         * {@code "Access-Control-Allow-Methods"}
         */
        public static final String ACCESS_CONTROL_ALLOW_METHODS = "Access-Control-Allow-Methods";
        /**
         * {@code "Access-Control-Allow-Origin"}
         */
        public static final String ACCESS_CONTROL_ALLOW_ORIGIN = "Access-Control-Allow-Origin";
        /**
         * {@code "Access-Control-Expose-Headers"}
         */
        public static final String ACCESS_CONTROL_EXPOSE_HEADERS = "Access-Control-Expose-Headers";
        /**
         * {@code "Access-Control-Max-Age"}
         */
        public static final String ACCESS_CONTROL_MAX_AGE = "Access-Control-Max-Age";
        /**
         * {@code "Access-Control-Request-Headers"}
         */
        public static final String ACCESS_CONTROL_REQUEST_HEADERS = "Access-Control-Request-Headers";
        /**
         * {@code "Access-Control-Request-Method"}
         */
        public static final String ACCESS_CONTROL_REQUEST_METHOD = "Access-Control-Request-Method";
        /**
         * {@code "Age"}
         */
        public static final String AGE = "Age";
        /**
         * {@code "Allow"}
         */
        public static final String ALLOW = "Allow";
        /**
         * {@code "Authorization"}
         */
        public static final String AUTHORIZATION = "Authorization";
        /**
         * {@code "Cache-Control"}
         */
        public static final String CACHE_CONTROL = "Cache-Control";
        /**
         * {@code "Connection"}
         */
        public static final String CONNECTION = "Connection";
        /**
         * {@code "Content-Base"}
         */
        public static final String CONTENT_BASE = "Content-Base";
        /**
         * {@code "Content-Encoding"}
         */
        public static final String CONTENT_ENCODING = "Content-Encoding";
        /**
         * {@code "Content-Language"}
         */
        public static final String CONTENT_LANGUAGE = "Content-Language";
        /**
         * {@code "Content-Length"}
         */
        public static final String CONTENT_LENGTH = "Content-Length";
        /**
         * {@code "Content-Location"}
         */
        public static final String CONTENT_LOCATION = "Content-Location";
        /**
         * {@code "Content-Transfer-Encoding"}
         */
        public static final String CONTENT_TRANSFER_ENCODING = "Content-Transfer-Encoding";
        /**
         * {@code "Content-MD5"}
         */
        public static final String CONTENT_MD5 = "Content-MD5";
        /**
         * {@code "Content-Range"}
         */
        public static final String CONTENT_RANGE = "Content-Range";
        /**
         * {@code "Content-Type"}
         */
        public static final String CONTENT_TYPE = "Content-Type";
        /**
         * {@code "Cookie"}
         */
        public static final String COOKIE = "Cookie";
        /**
         * {@code "Date"}
         */
        public static final String DATE = "Date";
        /**
         * {@code "ETag"}
         */
        public static final String ETAG = "ETag";
        /**
         * {@code "Expect"}
         */
        public static final String EXPECT = "Expect";
        /**
         * {@code "Expires"}
         */
        public static final String EXPIRES = "Expires";
        /**
         * {@code "From"}
         */
        public static final String FROM = "From";
        /**
         * {@code "Host"}
         */
        public static final String HOST = "Host";
        /**
         * {@code "If-Match"}
         */
        public static final String IF_MATCH = "If-Match";
        /**
         * {@code "If-Modified-Since"}
         */
        public static final String IF_MODIFIED_SINCE = "If-Modified-Since";
        /**
         * {@code "If-None-Match"}
         */
        public static final String IF_NONE_MATCH = "If-None-Match";
        /**
         * {@code "If-Range"}
         */
        public static final String IF_RANGE = "If-Range";
        /**
         * {@code "If-Unmodified-Since"}
         */
        public static final String IF_UNMODIFIED_SINCE = "If-Unmodified-Since";
        /**
         * {@code "Last-Modified"}
         */
        public static final String LAST_MODIFIED = "Last-Modified";
        /**
         * {@code "Location"}
         */
        public static final String LOCATION = "Location";
        /**
         * {@code "Max-Forwards"}
         */
        public static final String MAX_FORWARDS = "Max-Forwards";
        /**
         * {@code "Origin"}
         */
        public static final String ORIGIN = "Origin";
        /**
         * {@code "Pragma"}
         */
        public static final String PRAGMA = "Pragma";
        /**
         * {@code "Proxy-Authenticate"}
         */
        public static final String PROXY_AUTHENTICATE = "Proxy-Authenticate";
        /**
         * {@code "Proxy-Authorization"}
         */
        public static final String PROXY_AUTHORIZATION = "Proxy-Authorization";
        /**
         * {@code "Range"}
         */
        public static final String RANGE = "Range";
        /**
         * {@code "Referer"}
         */
        public static final String REFERER = "Referer";
        /**
         * {@code "Retry-After"}
         */
        public static final String RETRY_AFTER = "Retry-After";
        /**
         * {@code "Sec-WebSocket-Key1"}
         */
        public static final String SEC_WEBSOCKET_KEY1 = "Sec-WebSocket-Key1";
        /**
         * {@code "Sec-WebSocket-Key2"}
         */
        public static final String SEC_WEBSOCKET_KEY2 = "Sec-WebSocket-Key2";
        /**
         * {@code "Sec-WebSocket-Location"}
         */
        public static final String SEC_WEBSOCKET_LOCATION = "Sec-WebSocket-Location";
        /**
         * {@code "Sec-WebSocket-Origin"}
         */
        public static final String SEC_WEBSOCKET_ORIGIN = "Sec-WebSocket-Origin";
        /**
         * {@code "Sec-WebSocket-Protocol"}
         */
        public static final String SEC_WEBSOCKET_PROTOCOL = "Sec-WebSocket-Protocol";
        /**
         * {@code "Sec-WebSocket-Version"}
         */
        public static final String SEC_WEBSOCKET_VERSION = "Sec-WebSocket-Version";
        /**
         * {@code "Sec-WebSocket-Key"}
         */
        public static final String SEC_WEBSOCKET_KEY = "Sec-WebSocket-Key";
        /**
         * {@code "Sec-WebSocket-Accept"}
         */
        public static final String SEC_WEBSOCKET_ACCEPT = "Sec-WebSocket-Accept";
        /**
         * {@code "Server"}
         */
        public static final String SERVER = "Server";
        /**
         * {@code "Set-Cookie"}
         */
        public static final String SET_COOKIE = "Set-Cookie";
        /**
         * {@code "Set-Cookie2"}
         */
        public static final String SET_COOKIE2 = "Set-Cookie2";
        /**
         * {@code "TE"}
         */
        public static final String TE = "TE";
        /**
         * {@code "Trailer"}
         */
        public static final String TRAILER = "Trailer";
        /**
         * {@code "Transfer-Encoding"}
         */
        public static final String TRANSFER_ENCODING = "Transfer-Encoding";
        /**
         * {@code "Upgrade"}
         */
        public static final String UPGRADE = "Upgrade";
        /**
         * {@code "User-Agent"}
         */
        public static final String USER_AGENT = "User-Agent";
        /**
         * {@code "Vary"}
         */
        public static final String VARY = "Vary";
        /**
         * {@code "Via"}
         */
        public static final String VIA = "Via";
        /**
         * {@code "Warning"}
         */
        public static final String WARNING = "Warning";
        /**
         * {@code "WebSocket-Location"}
         */
        public static final String WEBSOCKET_LOCATION = "WebSocket-Location";
        /**
         * {@code "WebSocket-Origin"}
         */
        public static final String WEBSOCKET_ORIGIN = "WebSocket-Origin";
        /**
         * {@code "WebSocket-Protocol"}
         */
        public static final String WEBSOCKET_PROTOCOL = "WebSocket-Protocol";
        /**
         * {@code "WWW-Authenticate"}
         */
        public static final String WWW_AUTHENTICATE = "WWW-Authenticate";

        private Names() {
        }
    }

    /**
     * Standard HTTP header values.
     */
    public static final class Values {
        /**
         * {@code "application/json"}
         */
        public static final String APPLICATION_JSON = "application/json";
        /**
         * {@code "application/x-www-form-urlencoded"}
         */
         public static final String APPLICATION_X_WWW_FORM_URLENCODED =
             "application/x-www-form-urlencoded";
        /**
         * {@code "base64"}
         */
        public static final String BASE64 = "base64";
        /**
         * {@code "binary"}
         */
        public static final String BINARY = "binary";
        /**
         * {@code "boundary"}
         */
        public static final String BOUNDARY = "boundary";
        /**
         * {@code "bytes"}
         */
        public static final String BYTES = "bytes";
        /**
         * {@code "charset"}
         */
        public static final String CHARSET = "charset";
        /**
         * {@code "chunked"}
         */
        public static final String CHUNKED = "chunked";
        /**
         * {@code "close"}
         */
        public static final String CLOSE = "close";
        /**
         * {@code "compress"}
         */
        public static final String COMPRESS = "compress";
        /**
         * {@code "100-continue"}
         */
        public static final String CONTINUE =  "100-continue";
        /**
         * {@code "deflate"}
         */
        public static final String DEFLATE = "deflate";
        /**
         * {@code "gzip"}
         */
        public static final String GZIP = "gzip";
        /**
         * {@code "gzip,deflate"}
         */
        public static final String GZIP_DEFLATE = "gzip,deflate";
        /**
         * {@code "identity"}
         */
        public static final String IDENTITY = "identity";
        /**
         * {@code "keep-alive"}
         */
        public static final String KEEP_ALIVE = "keep-alive";
        /**
         * {@code "max-age"}
         */
        public static final String MAX_AGE = "max-age";
        /**
         * {@code "max-stale"}
         */
        public static final String MAX_STALE = "max-stale";
        /**
         * {@code "min-fresh"}
         */
        public static final String MIN_FRESH = "min-fresh";
        /**
         * {@code "multipart/form-data"}
         */
        public static final String MULTIPART_FORM_DATA = "multipart/form-data";
        /**
         * {@code "must-revalidate"}
         */
        public static final String MUST_REVALIDATE = "must-revalidate";
        /**
         * {@code "no-cache"}
         */
        public static final String NO_CACHE = "no-cache";
        /**
         * {@code "no-store"}
         */
        public static final String NO_STORE = "no-store";
        /**
         * {@code "no-transform"}
         */
        public static final String NO_TRANSFORM = "no-transform";
        /**
         * {@code "none"}
         */
        public static final String NONE = "none";
        /**
         * {@code "only-if-cached"}
         */
        public static final String ONLY_IF_CACHED = "only-if-cached";
        /**
         * {@code "private"}
         */
        public static final String PRIVATE = "private";
        /**
         * {@code "proxy-revalidate"}
         */
        public static final String PROXY_REVALIDATE = "proxy-revalidate";
        /**
         * {@code "public"}
         */
        public static final String PUBLIC = "public";
        /**
         * {@code "quoted-printable"}
         */
        public static final String QUOTED_PRINTABLE = "quoted-printable";
        /**
         * {@code "s-maxage"}
         */
        public static final String S_MAXAGE = "s-maxage";
        /**
         * {@code "trailers"}
         */
        public static final String TRAILERS = "trailers";
        /**
         * {@code "Upgrade"}
         */
        public static final String UPGRADE = "Upgrade";
        /**
         * {@code "WebSocket"}
         */
        public static final String WEBSOCKET = "WebSocket";

        private Values() {
        }
    }

    /**
     * Returns {@code true} if and only if the connection can remain open and
     * thus 'kept alive'.  This methods respects the value of the
     * {@code "Connection"} header first and then the return value of
     * {@link HttpVersion#isKeepAliveDefault()}.
     */
    public static boolean isKeepAlive(HttpMessage message) {
        String connection = message.headers().get(CONNECTION_ENTITY);
        if (connection != null && equalsIgnoreCase(CLOSE_ENTITY, connection)) {
            return false;
        }

        if (message.getProtocolVersion().isKeepAliveDefault()) {
            return !equalsIgnoreCase(CLOSE_ENTITY, connection);
        } else {
            return equalsIgnoreCase(KEEP_ALIVE_ENTITY, connection);
        }
    }

    /**
     * Sets the value of the {@code "Connection"} header depending on the
     * protocol version of the specified message.  This getMethod sets or removes
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
    public static void setKeepAlive(HttpMessage message, boolean keepAlive) {
        HttpHeaders h = message.headers();
        if (message.getProtocolVersion().isKeepAliveDefault()) {
            if (keepAlive) {
                h.remove(CONNECTION_ENTITY);
            } else {
                h.set(CONNECTION_ENTITY, CLOSE_ENTITY);
            }
        } else {
            if (keepAlive) {
                h.set(CONNECTION_ENTITY, KEEP_ALIVE_ENTITY);
            } else {
                h.remove(CONNECTION_ENTITY);
            }
        }
    }

    /**
     * @see {@link #getHeader(HttpMessage, CharSequence)}
     */
    public static String getHeader(HttpMessage message, String name) {
        return message.headers().get(name);
    }

    /**
     * Returns the header value with the specified header name.  If there are
     * more than one header value for the specified header name, the first
     * value is returned.
     *
     * @return the header value or {@code null} if there is no such header
     */
    public static String getHeader(HttpMessage message, CharSequence name) {
        return message.headers().get(name);
    }

    /**
     * @see {@link #getHeader(HttpMessage, CharSequence, String)}
     */
    public static String getHeader(HttpMessage message, String name, String defaultValue) {
        return getHeader(message, (CharSequence) name, defaultValue);
    }

    /**
     * Returns the header value with the specified header name.  If there are
     * more than one header value for the specified header name, the first
     * value is returned.
     *
     * @return the header value or the {@code defaultValue} if there is no such
     *         header
     */
    public static String getHeader(HttpMessage message, CharSequence name, String defaultValue) {
        String value = message.headers().get(name);
        if (value == null) {
            return defaultValue;
        }
        return value;
    }

    /**
     * @see {@link #setHeader(HttpMessage, CharSequence, Object)}
     */
    public static void setHeader(HttpMessage message, String name, Object value) {
        message.headers().set(name, value);
    }

    /**
     * Sets a new header with the specified name and value.  If there is an
     * existing header with the same name, the existing header is removed.
     * If the specified value is not a {@link String}, it is converted into a
     * {@link String} by {@link Object#toString()}, except for {@link Date}
     * and {@link Calendar} which are formatted to the date format defined in
     * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.3.1">RFC2616</a>.
     */
    public static void setHeader(HttpMessage message, CharSequence name, Object value) {
        message.headers().set(name, value);
    }

    /**
     *
     * @see {@link #setHeader(HttpMessage, CharSequence, Iterable)}
     */
    public static void setHeader(HttpMessage message, String name, Iterable<?> values) {
        message.headers().set(name, values);
    }

    /**
     * Sets a new header with the specified name and values.  If there is an
     * existing header with the same name, the existing header is removed.
     * This getMethod can be represented approximately as the following code:
     * <pre>
     * removeHeader(message, name);
     * for (Object v: values) {
     *     if (v == null) {
     *         break;
     *     }
     *     addHeader(message, name, v);
     * }
     * </pre>
     */
    public static void setHeader(HttpMessage message, CharSequence name, Iterable<?> values) {
        message.headers().set(name, values);
    }

    /**
     * @see {@link #addHeader(HttpMessage, CharSequence, Object)}
     */
    public static void addHeader(HttpMessage message, String name, Object value) {
        message.headers().add(name, value);
    }

    /**
     * Adds a new header with the specified name and value.
     * If the specified value is not a {@link String}, it is converted into a
     * {@link String} by {@link Object#toString()}, except for {@link Date}
     * and {@link Calendar} which are formatted to the date format defined in
     * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.3.1">RFC2616</a>.
     */
    public static void addHeader(HttpMessage message, CharSequence name, Object value) {
        message.headers().add(name, value);
    }

    /**
     * @see {@link #removeHeader(HttpMessage, CharSequence)}
     */
    public static void removeHeader(HttpMessage message, String name) {
        message.headers().remove(name);
    }

    /**
     * Removes the header with the specified name.
     */
    public static void removeHeader(HttpMessage message, CharSequence name) {
        message.headers().remove(name);
    }

    /**
     * Removes all headers from the specified message.
     */
    public static void clearHeaders(HttpMessage message) {
        message.headers().clear();
    }

    /**
     * @see {@link #getIntHeader(HttpMessage, CharSequence)}
     */
    public static int getIntHeader(HttpMessage message, String name) {
        return getIntHeader(message, (CharSequence) name);
    }

    /**
     * Returns the integer header value with the specified header name.  If
     * there are more than one header value for the specified header name, the
     * first value is returned.
     *
     * @return the header value
     * @throws NumberFormatException
     *         if there is no such header or the header value is not a number
     */
    public static int getIntHeader(HttpMessage message, CharSequence name) {
        String value = getHeader(message, name);
        if (value == null) {
            throw new NumberFormatException("header not found: " + name);
        }
        return Integer.parseInt(value);
    }

    /**
     * @see {@link #getIntHeader(HttpMessage, CharSequence, int)}
     */
    public static int getIntHeader(HttpMessage message, String name, int defaultValue) {
        return getIntHeader(message, (CharSequence) name, defaultValue);
    }

    /**
     * Returns the integer header value with the specified header name.  If
     * there are more than one header value for the specified header name, the
     * first value is returned.
     *
     * @return the header value or the {@code defaultValue} if there is no such
     *         header or the header value is not a number
     */
    public static int getIntHeader(HttpMessage message, CharSequence name, int defaultValue) {
        String value = getHeader(message, name);
        if (value == null) {
            return defaultValue;
        }

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    /**
     * @see {@link #setIntHeader(HttpMessage, CharSequence, int)}
     */
    public static void setIntHeader(HttpMessage message, String name, int value) {
        message.headers().set(name, value);
    }

    /**
     * Sets a new integer header with the specified name and value.  If there
     * is an existing header with the same name, the existing header is removed.
     */
    public static void setIntHeader(HttpMessage message, CharSequence name, int value) {
        message.headers().set(name, value);
    }

    /**
     * @see {@link #setIntHeader(HttpMessage, CharSequence, Iterable)}
     */
    public static void setIntHeader(HttpMessage message, String name, Iterable<Integer> values) {
        message.headers().set(name, values);
    }

    /**
     * Sets a new integer header with the specified name and values.  If there
     * is an existing header with the same name, the existing header is removed.
     */
    public static void setIntHeader(HttpMessage message, CharSequence name, Iterable<Integer> values) {
        message.headers().set(name, values);
    }

    /**
     *
     * @see {@link #addIntHeader(HttpMessage, CharSequence, int)}
     */
    public static void addIntHeader(HttpMessage message, String name, int value) {
        message.headers().add(name, value);
    }

    /**
     * Adds a new integer header with the specified name and value.
     */
    public static void addIntHeader(HttpMessage message, CharSequence name, int value) {
        message.headers().add(name, value);
    }

    /**
     * @see {@link #getDateHeader(HttpMessage, CharSequence)}
     */
    public static Date getDateHeader(HttpMessage message, String name) throws ParseException {
        return getDateHeader(message, (CharSequence) name);
    }

    /**
     * Returns the date header value with the specified header name.  If
     * there are more than one header value for the specified header name, the
     * first value is returned.
     *
     * @return the header value
     * @throws ParseException
     *         if there is no such header or the header value is not a formatted date
     */
    public static Date getDateHeader(HttpMessage message, CharSequence name) throws ParseException {
        String value = getHeader(message, name);
        if (value == null) {
            throw new ParseException("header not found: " + name, 0);
        }
        return HttpHeaderDateFormat.get().parse(value);
    }

    /**
     * @see {@link #getDateHeader(HttpMessage, CharSequence, Date)}
     */
    public static Date getDateHeader(HttpMessage message, String name, Date defaultValue) {
        return getDateHeader(message, (CharSequence) name, defaultValue);
    }

    /**
     * Returns the date header value with the specified header name.  If
     * there are more than one header value for the specified header name, the
     * first value is returned.
     *
     * @return the header value or the {@code defaultValue} if there is no such
     *         header or the header value is not a formatted date
     */
    public static Date getDateHeader(HttpMessage message, CharSequence name, Date defaultValue) {
        final String value = getHeader(message, name);
        if (value == null) {
            return defaultValue;
        }

        try {
            return HttpHeaderDateFormat.get().parse(value);
        } catch (ParseException ignored) {
            return defaultValue;
        }
    }

    /**
     * @see {@link #setDateHeader(HttpMessage, CharSequence, Date)}
     */
    public static void setDateHeader(HttpMessage message, String name, Date value) {
        setDateHeader(message, (CharSequence) name, value);
    }

    /**
     * Sets a new date header with the specified name and value.  If there
     * is an existing header with the same name, the existing header is removed.
     * The specified value is formatted as defined in
     * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.3.1">RFC2616</a>
     */
    public static void setDateHeader(HttpMessage message, CharSequence name, Date value) {
        if (value != null) {
            message.headers().set(name, HttpHeaderDateFormat.get().format(value));
        } else {
            message.headers().set(name, null);
        }
    }

    /**
     * @see {@link #setDateHeader(HttpMessage, CharSequence, Iterable)}
     */
    public static void setDateHeader(HttpMessage message, String name, Iterable<Date> values) {
        message.headers().set(name, values);
    }

    /**
     * Sets a new date header with the specified name and values.  If there
     * is an existing header with the same name, the existing header is removed.
     * The specified values are formatted as defined in
     * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.3.1">RFC2616</a>
     */
    public static void setDateHeader(HttpMessage message, CharSequence name, Iterable<Date> values) {
        message.headers().set(name, values);
    }

    /**
     * @see {@link #addDateHeader(HttpMessage, CharSequence, Date)}
     */
    public static void addDateHeader(HttpMessage message, String name, Date value) {
        message.headers().add(name, value);
    }

    /**
     * Adds a new date header with the specified name and value.  The specified
     * value is formatted as defined in
     * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.3.1">RFC2616</a>
     */
    public static void addDateHeader(HttpMessage message, CharSequence name, Date value) {
        message.headers().add(name, value);
    }

    /**
     * Returns the length of the content.  Please note that this value is
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
        String value = getHeader(message, CONTENT_LENGTH_ENTITY);
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
        throw new NumberFormatException("header not found: " + Names.CONTENT_LENGTH);
    }

    /**
     * Returns the length of the content.  Please note that this value is
     * not retrieved from {@link HttpContent#content()} but from the
     * {@code "Content-Length"} header, and thus they are independent from each
     * other.
     *
     * @return the content length or {@code defaultValue} if this message does
     *         not have the {@code "Content-Length"} header or its value is not
     *         a number
     */
    public static long getContentLength(HttpMessage message, long defaultValue) {
        String contentLength = message.headers().get(CONTENT_LENGTH_ENTITY);
        if (contentLength != null) {
            try {
                return Long.parseLong(contentLength);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
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
     * Returns the content length of the specified web socket message.  If the
     * specified message is not a web socket message, {@code -1} is returned.
     */
    private static int getWebSocketContentLength(HttpMessage message) {
        // WebSockset messages have constant content-lengths.
        HttpHeaders h = message.headers();
        if (message instanceof HttpRequest) {
            HttpRequest req = (HttpRequest) message;
            if (HttpMethod.GET.equals(req.getMethod()) &&
                h.contains(SEC_WEBSOCKET_KEY1_ENTITY) &&
                h.contains(SEC_WEBSOCKET_KEY2_ENTITY)) {
                return 8;
            }
        } else if (message instanceof HttpResponse) {
            HttpResponse res = (HttpResponse) message;
            if (res.getStatus().code() == 101 &&
                h.contains(SEC_WEBSOCKET_ORIGIN_ENTITY) &&
                h.contains(SEC_WEBSOCKET_LOCATION_ENTITY)) {
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
        message.headers().set(CONTENT_LENGTH_ENTITY, length);
    }

    /**
     * Returns the value of the {@code "Host"} header.
     */
    public static String getHost(HttpMessage message) {
        return message.headers().get(HOST_ENTITY);
    }

    /**
     * Returns the value of the {@code "Host"} header.  If there is no such
     * header, the {@code defaultValue} is returned.
     */
    public static String getHost(HttpMessage message, String defaultValue) {
        return getHeader(message, HOST_ENTITY, defaultValue);
    }

    /**
     * @see {@link #setHost(HttpMessage, CharSequence)}
     */
    public static void setHost(HttpMessage message, String value) {
        message.headers().set(HOST_ENTITY, value);
    }

    /**
     * Sets the {@code "Host"} header.
     */
    public static void setHost(HttpMessage message, CharSequence value) {
        message.headers().set(HOST_ENTITY, value);
    }

    /**
     * Returns the value of the {@code "Date"} header.
     *
     * @throws ParseException
     *         if there is no such header or the header value is not a formatted date
     */
    public static Date getDate(HttpMessage message) throws ParseException {
        return getDateHeader(message, DATE_ENTITY);
    }

    /**
     * Returns the value of the {@code "Date"} header. If there is no such
     * header or the header is not a formatted date, the {@code defaultValue}
     * is returned.
     */
    public static Date getDate(HttpMessage message, Date defaultValue) {
        return getDateHeader(message, DATE_ENTITY, defaultValue);
    }

    /**
     * Sets the {@code "Date"} header.
     */
    public static void setDate(HttpMessage message, Date value) {
        if (value != null) {
            message.headers().set(DATE_ENTITY, HttpHeaderDateFormat.get().format(value));
        } else {
            message.headers().set(DATE_ENTITY, null);
        }
    }

    /**
     * Returns {@code true} if and only if the specified message contains the
     * {@code "Expect: 100-continue"} header.
     */
    public static boolean is100ContinueExpected(HttpMessage message) {
        // Expect: 100-continue is for requests only.
        if (!(message instanceof HttpRequest)) {
            return false;
        }

        // It works only on HTTP/1.1 or later.
        if (message.getProtocolVersion().compareTo(HttpVersion.HTTP_1_1) < 0) {
            return false;
        }

        // In most cases, there will be one or zero 'Expect' header.
        String value = message.headers().get(EXPECT_ENTITY);
        if (value == null) {
            return false;
        }
        if (equalsIgnoreCase(CONTINUE_ENTITY, value)) {
            return true;
        }

        // Multiple 'Expect' headers.  Search through them.
        return message.headers().contains(EXPECT_ENTITY, CONTINUE_ENTITY, true);
    }

    /**
     * Sets the {@code "Expect: 100-continue"} header to the specified message.
     * If there is any existing {@code "Expect"} header, they are replaced with
     * the new one.
     */
    public static void set100ContinueExpected(HttpMessage message) {
        set100ContinueExpected(message, true);
    }

    /**
     * Sets or removes the {@code "Expect: 100-continue"} header to / from the
     * specified message.  If {@code set} is {@code true},
     * the {@code "Expect: 100-continue"} header is set and all other previous
     * {@code "Expect"} headers are removed.  Otherwise, all {@code "Expect"}
     * headers are removed completely.
     */
    public static void set100ContinueExpected(HttpMessage message, boolean set) {
        if (set) {
            message.headers().set(EXPECT_ENTITY, CONTINUE_ENTITY);
        } else {
            message.headers().remove(EXPECT_ENTITY);
        }
    }

    /**
     * Validates the name of a header
     *
     * @param headerName The header name being validated
     */
    static void validateHeaderName(CharSequence headerName) {
        //Check to see if the name is null
        if (headerName == null) {
            throw new NullPointerException("Header names cannot be null");
        }
        //Go through each of the characters in the name
        for (int index = 0; index < headerName.length(); index ++) {
            //Actually get the character
            char character = headerName.charAt(index);

            //Check to see if the character is not an ASCII character
            if (character > 127) {
                throw new IllegalArgumentException(
                        "Header name cannot contain non-ASCII characters: " + headerName);
            }

            //Check for prohibited characters.
            switch (character) {
                case '\t': case '\n': case 0x0b: case '\f': case '\r':
                case ' ':  case ',':  case ':':  case ';':  case '=':
                    throw new IllegalArgumentException(
                            "Header name cannot contain the following prohibited characters: " +
                                    "=,;: \\t\\r\\n\\v\\f: " + headerName);
            }
        }
    }

    /**
     * Validates the specified header value
     *
     * @param headerValue The value being validated
     */
    static void validateHeaderValue(CharSequence headerValue) {
        //Check to see if the value is null
        if (headerValue == null) {
            throw new NullPointerException("Header values cannot be null");
        }

        /*
         * Set up the state of the validation
         *
         * States are as follows:
         *
         * 0: Previous character was neither CR nor LF
         * 1: The previous character was CR
         * 2: The previous character was LF
         */
        int state = 0;

        //Start looping through each of the character

        for (int index = 0; index < headerValue.length(); index ++) {
            char character = headerValue.charAt(index);

            //Check the absolutely prohibited characters.
            switch (character) {
                case 0x0b: // Vertical tab
                    throw new IllegalArgumentException(
                            "Header value contains a prohibited character '\\v': " + headerValue);
                case '\f':
                    throw new IllegalArgumentException(
                            "Header value contains a prohibited character '\\f': " + headerValue);
            }

            // Check the CRLF (HT | SP) pattern
            switch (state) {
                case 0:
                    switch (character) {
                        case '\r':
                            state = 1;
                            break;
                        case '\n':
                            state = 2;
                            break;
                    }
                    break;
                case 1:
                    switch (character) {
                        case '\n':
                            state = 2;
                            break;
                        default:
                            throw new IllegalArgumentException(
                                    "Only '\\n' is allowed after '\\r': " + headerValue);
                    }
                    break;
                case 2:
                    switch (character) {
                        case '\t': case ' ':
                            state = 0;
                            break;
                        default:
                            throw new IllegalArgumentException(
                                    "Only ' ' and '\\t' are allowed after '\\n': " + headerValue);
                    }
            }
        }

        if (state != 0) {
            throw new IllegalArgumentException(
                    "Header value must not end with '\\r' or '\\n':" + headerValue);
        }
    }

    /**
     * Checks to see if the transfer encoding in a specified {@link HttpMessage} is chunked
     *
     * @param message The message to check
     * @return True if transfer encoding is chunked, otherwise false
     */
    public static boolean isTransferEncodingChunked(HttpMessage message) {
        return message.headers().contains(TRANSFER_ENCODING_ENTITY, CHUNKED_ENTITY, true);
    }

    public static void removeTransferEncodingChunked(HttpMessage m) {
        List<String> values = m.headers().getAll(TRANSFER_ENCODING_ENTITY);
        if (values.isEmpty()) {
            return;
        }
        Iterator<String> valuesIt = values.iterator();
        while (valuesIt.hasNext()) {
            String value = valuesIt.next();
            if (equalsIgnoreCase(value, CHUNKED_ENTITY)) {
                valuesIt.remove();
            }
        }
        if (values.isEmpty()) {
            m.headers().remove(TRANSFER_ENCODING_ENTITY);
        } else {
            m.headers().set(TRANSFER_ENCODING_ENTITY, values);
        }
    }

    public static void setTransferEncodingChunked(HttpMessage m) {
        addHeader(m, TRANSFER_ENCODING_ENTITY, CHUNKED_ENTITY);
        removeHeader(m, CONTENT_LENGTH_ENTITY);
    }

    public static boolean isContentLengthSet(HttpMessage m) {
        return m.headers().contains(CONTENT_LENGTH_ENTITY);
    }

    /**
     * Returns {@code true} if both {@link CharSequence}'s are equals when ignore the case.
     * This only supports US_ASCII.
     */
    public static boolean equalsIgnoreCase(CharSequence name1, CharSequence name2) {
        if (name1 == name2) {
            return true;
        }

        if (name1 == null || name2 == null) {
            return false;
        }

        int nameLen = name1.length();
        if (nameLen != name2.length()) {
            return false;
        }

        for (int i = nameLen - 1; i >= 0; i --) {
            char c1 = name1.charAt(i);
            char c2 = name2.charAt(i);
            if (c1 != c2) {
                if (c1 >= 'A' && c1 <= 'Z') {
                    c1 += 32;
                }
                if (c2 >= 'A' && c2 <= 'Z') {
                    c2 += 32;
                }
                if (c1 != c2) {
                    return false;
                }
            }
        }
        return true;
    }

    static int hash(CharSequence name) {
        if (name instanceof HttpHeaderEntity) {
            return ((HttpHeaderEntity) name).hash();
        }
        int h = 0;
        for (int i = name.length() - 1; i >= 0; i --) {
            char c = name.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                c += 32;
            }
            h = 31 * h + c;
        }

        if (h > 0) {
            return h;
        } else if (h == Integer.MIN_VALUE) {
            return Integer.MAX_VALUE;
        } else {
            return -h;
        }
    }

    static void encode(HttpHeaders headers, ByteBuf buf) {
        if (headers instanceof DefaultHttpHeaders) {
            ((DefaultHttpHeaders) headers).encode(buf);
        } else {
            for (Entry<String, String> header: headers) {
                encode(header.getKey(), header.getValue(), buf);
            }
        }
    }

    @SuppressWarnings("deprecation")
    static void encode(CharSequence key, CharSequence value, ByteBuf buf) {
        if (!encodeAscii(key, buf)) {
            buf.writeBytes(HEADER_SEPERATOR);
        }
        if (!encodeAscii(value, buf)) {
            buf.writeBytes(CRLF);
        }
    }

    public static boolean encodeAscii(CharSequence seq, ByteBuf buf) {
        if (seq instanceof HttpHeaderEntity) {
            return ((HttpHeaderEntity) seq).encode(buf);
        } else {
            encodeAscii0(seq, buf);
            return false;
        }
    }

    static void encodeAscii0(CharSequence seq, ByteBuf buf) {
        int length = seq.length();
        for (int i = 0 ; i < length; i++) {
            buf.writeByte(c2b(seq.charAt(i)));
        }
    }

    private static byte c2b(char c) {
        if (c > 255) {
            return '?';
        }
        return (byte) c;
    }

    /**
     * Create a new {@link CharSequence} which is optimized for reuse as {@link HttpHeaders} name or value.
     * So if you have a Header name or value that you want to reuse you should make use of this.
     */
    public static CharSequence newEntity(String name) {
        if (name == null) {
            throw new NullPointerException("name");
        }
        return new HttpHeaderEntity(name);
    }

    /**
     * Create a new {@link CharSequence} which is optimized for reuse as {@link HttpHeaders} name.
     * So if you have a Header name that you want to reuse you should make use of this.
     */
    public static CharSequence newNameEntity(String name) {
        if (name == null) {
            throw new NullPointerException("name");
        }
        return new HttpHeaderEntity(name, HEADER_SEPERATOR);
    }

    /**
     * Create a new {@link CharSequence} which is optimized for reuse as {@link HttpHeaders} value.
     * So if you have a Header value that you want to reuse you should make use of this.
     */
    public static CharSequence newValueEntity(String name) {
        if (name == null) {
            throw new NullPointerException("name");
        }
        return new HttpHeaderEntity(name, CRLF);
    }

    protected HttpHeaders() { }

    /**
     * @see {@link #get(CharSequence)}
     */
    public abstract String get(String name);

    /**
     * Returns the value of a header with the specified name.  If there are
     * more than one values for the specified name, the first value is returned.
     *
     * @param name The name of the header to search
     * @return The first header value or {@code null} if there is no such header
     */
    public String get(CharSequence name) {
        return get(name.toString());
    }

    /**
     * @see {@link #getAll(CharSequence)}
     */
    public abstract List<String> getAll(String name);

    /**
     * Returns the values of headers with the specified name
     *
     * @param name The name of the headers to search
     * @return A {@link List} of header values which will be empty if no values
     *         are found
     */
    public List<String> getAll(CharSequence name) {
        return getAll(name.toString());
    }

    /**
     * Returns a new {@link List} that contains all headers in this object.  Note that modifying the
     * returned {@link List} will not affect the state of this object.  If you intend to enumerate over the header
     * entries only, use {@link #iterator()} instead, which has much less overhead.
     */
    public abstract List<Map.Entry<String, String>> entries();

    /**
     * @see {@link #contains(CharSequence)}
     */
    public abstract boolean contains(String name);

    /**
     * Checks to see if there is a header with the specified name
     *
     * @param name The name of the header to search for
     * @return True if at least one header is found
     */
    public boolean contains(CharSequence name) {
        return contains(name.toString());
    }

    /**
     * Checks if no header exists.
     */
    public abstract boolean isEmpty();

    /**
     * Returns a new {@link Set} that contains the names of all headers in this object.  Note that modifying the
     * returned {@link Set} will not affect the state of this object.  If you intend to enumerate over the header
     * entries only, use {@link #iterator()} instead, which has much less overhead.
     */
    public abstract Set<String> names();

    /**
     * @see {@link #add(CharSequence, Object)}
     */
    public abstract HttpHeaders add(String name, Object value);

    /**
     * Adds a new header with the specified name and value.
     *
     * If the specified value is not a {@link String}, it is converted
     * into a {@link String} by {@link Object#toString()}, except in the cases
     * of {@link Date} and {@link Calendar}, which are formatted to the date
     * format defined in <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.3.1">RFC2616</a>.
     *
     * @param name The name of the header being added
     * @param value The value of the header being added
     *
     * @return {@code this}
     */
    public HttpHeaders add(CharSequence name, Object value) {
        return add(name.toString(), value);
    }

    /**
     * @see {@link #add(CharSequence, Iterable)}
     */
    public abstract HttpHeaders add(String name, Iterable<?> values);

    /**
     * Adds a new header with the specified name and values.
     *
     * This getMethod can be represented approximately as the following code:
     * <pre>
     * for (Object v: values) {
     *     if (v == null) {
     *         break;
     *     }
     *     headers.add(name, v);
     * }
     * </pre>
     *
     * @param name The name of the headers being set
     * @param values The values of the headers being set
     * @return {@code this}
     */
    public HttpHeaders add(CharSequence name, Iterable<?> values) {
        return add(name.toString(), values);
    }

    /**
     * Adds all header entries of the specified {@code headers}.
     *
     * @return {@code this}
     */
    public HttpHeaders add(HttpHeaders headers) {
        if (headers == null) {
            throw new NullPointerException("headers");
        }
        for (Map.Entry<String, String> e: headers) {
            add(e.getKey(), e.getValue());
        }
        return this;
    }

    /**
     * @see {@link #set(CharSequence, Object)}
     */
    public abstract HttpHeaders set(String name, Object value);

    /**
     * Sets a header with the specified name and value.
     *
     * If there is an existing header with the same name, it is removed.
     * If the specified value is not a {@link String}, it is converted into a
     * {@link String} by {@link Object#toString()}, except for {@link Date}
     * and {@link Calendar}, which are formatted to the date format defined in
     * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.3.1">RFC2616</a>.
     *
     * @param name The name of the header being set
     * @param value The value of the header being set
     * @return {@code this}
     */
    public HttpHeaders set(CharSequence name, Object value) {
        return set(name.toString(), value);
    }

    /**
     * @see {@link #set(CharSequence, Iterable)}
     */
    public abstract HttpHeaders set(String name, Iterable<?> values);

    /**
     * Sets a header with the specified name and values.
     *
     * If there is an existing header with the same name, it is removed.
     * This getMethod can be represented approximately as the following code:
     * <pre>
     * headers.remove(name);
     * for (Object v: values) {
     *     if (v == null) {
     *         break;
     *     }
     *     headers.add(name, v);
     * }
     * </pre>
     *
     * @param name The name of the headers being set
     * @param values The values of the headers being set
     * @return {@code this}
     */
    public HttpHeaders set(CharSequence name, Iterable<?> values) {
        return set(name.toString(), values);
    }

    /**
     * Cleans the current header entries and copies all header entries of the specified {@code headers}.
     *
     * @return {@code this}
     */
    public HttpHeaders set(HttpHeaders headers) {
        if (headers == null) {
            throw new NullPointerException("headers");
        }
        if (headers != this) {
            clear();
            for (Map.Entry<String, String> e : headers) {
                add(e.getKey(), e.getValue());
            }
        }
        return this;
    }

    /**
     * @see {@link #remove(CharSequence)}
     */
    public abstract HttpHeaders remove(String name);

    /**
     * Removes the header with the specified name.
     *
     * @param name The name of the header to remove
     * @return {@code this}
     */
    public HttpHeaders remove(CharSequence name) {
        return remove(name.toString());
    }

    /**
     * Removes all headers from this {@link HttpMessage}.
     *
     * @return {@code this}
     */
    public abstract HttpHeaders clear();

    /**
     * @see {@link #contains(CharSequence, CharSequence, boolean)}
     */
    public boolean contains(String name, String value, boolean ignoreCaseValue) {
        List<String> values = getAll(name);
        if (values.isEmpty()) {
            return false;
        }

        for (String v: values) {
            if (ignoreCaseValue) {
                if (equalsIgnoreCase(v, value)) {
                    return true;
                }
            } else {
                if (v.equals(value)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns {@code true} if a header with the {@code name} and {@code value} exists, {@code false} otherwise.
     * This also handles multiple values that are seperated with a {@code ,}.
     * <p>
     * If {@code ignoreCase} is {@code true} then a case insensitive compare is done on the value.
     * @param name the name of the header to find
     * @param value the value of the header to find
     * @param ignoreCase {@code true} then a case insensitive compare is run to compare values.
     * otherwise a case sensitive compare is run to compare values.
     */
    public boolean containsValue(CharSequence name, CharSequence value, boolean ignoreCase) {
        List<String> values = getAll(name);
        if (values.isEmpty()) {
            return false;
        }

        for (String v: values) {
            if (contains(v, value, ignoreCase)) {
                return true;
            }
        }
        return false;
    }

    private static boolean contains(String value, CharSequence expected, boolean ignoreCase) {
        String[] parts = value.split(",");
        if (ignoreCase) {
            for (String s: parts) {
                if (equalsIgnoreCase(expected, s.trim())) {
                    return true;
                }
            }
        } else {
            for (String s: parts) {
                if (s.trim().contentEquals(expected)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns {@code true} if a header with the name and value exists.
     *
     * @param name              the headername
     * @param value             the value
     * @param ignoreCaseValue   {@code true} if case should be ignored
     * @return contains         {@code true} if it contains it {@code false} otherwise
     */
    public boolean contains(CharSequence name, CharSequence value, boolean ignoreCaseValue) {
        return contains(name.toString(), value.toString(), ignoreCaseValue);
    }
}
