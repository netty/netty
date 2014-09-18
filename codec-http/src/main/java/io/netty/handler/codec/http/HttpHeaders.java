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

import static io.netty.handler.codec.http.HttpConstants.COLON;
import static io.netty.handler.codec.http.HttpConstants.CR;
import static io.netty.handler.codec.http.HttpConstants.LF;
import static io.netty.handler.codec.http.HttpConstants.SP;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.AsciiString;

import java.text.ParseException;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Provides the constants for the standard HTTP header names and values and
 * commonly used utility methods that accesses an {@link HttpMessage}.
 */
public abstract class HttpHeaders implements Iterable<Map.Entry<String, String>> {

    private static final byte[] HEADER_SEPERATOR = { COLON, SP };
    private static final byte[] CRLF = { CR, LF };
    private static final CharSequence CONTENT_LENGTH_ENTITY = Names.CONTENT_LENGTH;
    private static final CharSequence CONNECTION_ENTITY = Names.CONNECTION;
    private static final CharSequence CLOSE_ENTITY = Values.CLOSE;
    private static final CharSequence KEEP_ALIVE_ENTITY = Values.KEEP_ALIVE;
    private static final CharSequence HOST_ENTITY = Names.HOST;
    private static final CharSequence DATE_ENTITY = Names.DATE;
    private static final CharSequence EXPECT_ENTITY = Names.EXPECT;
    private static final CharSequence CONTINUE_ENTITY = Values.CONTINUE;
    private static final CharSequence TRANSFER_ENCODING_ENTITY = Names.TRANSFER_ENCODING;
    private static final CharSequence CHUNKED_ENTITY = Values.CHUNKED;
    private static final CharSequence SEC_WEBSOCKET_KEY1_ENTITY = Names.SEC_WEBSOCKET_KEY1;
    private static final CharSequence SEC_WEBSOCKET_KEY2_ENTITY = Names.SEC_WEBSOCKET_KEY2;
    private static final CharSequence SEC_WEBSOCKET_ORIGIN_ENTITY = Names.SEC_WEBSOCKET_ORIGIN;
    private static final CharSequence SEC_WEBSOCKET_LOCATION_ENTITY = Names.SEC_WEBSOCKET_LOCATION;

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
     * <p>
     * These are all defined as lowercase to support HTTP/2 requirements while also not
     * violating HTTP/1.x requirements.  New header names should always be lowercase.
     */
    public static final class Names {
        /**
         * {@code "accept"}
         */
        public static final AsciiString ACCEPT = new AsciiString("accept");
        /**
         * {@code "accept-charset"}
         */
        public static final AsciiString ACCEPT_CHARSET = new AsciiString("accept-charset");
        /**
         * {@code "accept-encoding"}
         */
        public static final AsciiString ACCEPT_ENCODING = new AsciiString("accept-encoding");
        /**
         * {@code "accept-language"}
         */
        public static final AsciiString ACCEPT_LANGUAGE = new AsciiString("accept-language");
        /**
         * {@code "accept-ranges"}
         */
        public static final AsciiString ACCEPT_RANGES = new AsciiString("accept-ranges");
        /**
         * {@code "accept-patch"}
         */
        public static final AsciiString ACCEPT_PATCH = new AsciiString("accept-patch");
        /**
         * {@code "access-control-allow-credentials"}
         */
        public static final AsciiString ACCESS_CONTROL_ALLOW_CREDENTIALS =
                new AsciiString("access-control-allow-credentials");
        /**
         * {@code "access-control-allow-headers"}
         */
        public static final AsciiString ACCESS_CONTROL_ALLOW_HEADERS =
                new AsciiString("access-control-allow-headers");
        /**
         * {@code "access-control-allow-methods"}
         */
        public static final AsciiString ACCESS_CONTROL_ALLOW_METHODS =
                new AsciiString("access-control-allow-methods");
        /**
         * {@code "access-control-allow-origin"}
         */
        public static final AsciiString ACCESS_CONTROL_ALLOW_ORIGIN =
                new AsciiString("access-control-allow-origin");
        /**
         * {@code "access-control-expose-headers"}
         */
        public static final AsciiString ACCESS_CONTROL_EXPOSE_HEADERS =
                new AsciiString("access-control-expose-headers");
        /**
         * {@code "access-control-max-age"}
         */
        public static final AsciiString ACCESS_CONTROL_MAX_AGE = new AsciiString("access-control-max-age");
        /**
         * {@code "access-control-request-headers"}
         */
        public static final AsciiString ACCESS_CONTROL_REQUEST_HEADERS =
                new AsciiString("access-control-request-headers");
        /**
         * {@code "access-control-request-method"}
         */
        public static final AsciiString ACCESS_CONTROL_REQUEST_METHOD =
                new AsciiString("access-control-request-method");
        /**
         * {@code "age"}
         */
        public static final AsciiString AGE = new AsciiString("age");
        /**
         * {@code "allow"}
         */
        public static final AsciiString ALLOW = new AsciiString("allow");
        /**
         * {@code "authorization"}
         */
        public static final AsciiString AUTHORIZATION = new AsciiString("authorization");
        /**
         * {@code "cache-control"}
         */
        public static final AsciiString CACHE_CONTROL = new AsciiString("cache-control");
        /**
         * {@code "connection"}
         */
        public static final AsciiString CONNECTION = new AsciiString("connection");
        /**
         * {@code "content-base"}
         */
        public static final AsciiString CONTENT_BASE = new AsciiString("content-base");
        /**
         * {@code "content-encoding"}
         */
        public static final AsciiString CONTENT_ENCODING = new AsciiString("content-encoding");
        /**
         * {@code "content-language"}
         */
        public static final AsciiString CONTENT_LANGUAGE = new AsciiString("content-language");
        /**
         * {@code "content-length"}
         */
        public static final AsciiString CONTENT_LENGTH = new AsciiString("content-length");
        /**
         * {@code "content-location"}
         */
        public static final AsciiString CONTENT_LOCATION = new AsciiString("content-location");
        /**
         * {@code "content-transfer-encoding"}
         */
        public static final AsciiString CONTENT_TRANSFER_ENCODING = new AsciiString("content-transfer-encoding");
        /**
         * {@code "content-disposition"}
         */
        public static final AsciiString CONTENT_DISPOSITION = new AsciiString("content-disposition");
        /**
         * {@code "content-md5"}
         */
        public static final AsciiString CONTENT_MD5 = new AsciiString("content-md5");
        /**
         * {@code "content-range"}
         */
        public static final AsciiString CONTENT_RANGE = new AsciiString("content-range");
        /**
         * {@code "content-type"}
         */
        public static final AsciiString CONTENT_TYPE = new AsciiString("content-type");
        /**
         * {@code "cookie"}
         */
        public static final AsciiString COOKIE = new AsciiString("cookie");
        /**
         * {@code "date"}
         */
        public static final AsciiString DATE = new AsciiString("date");
        /**
         * {@code "etag"}
         */
        public static final AsciiString ETAG = new AsciiString("etag");
        /**
         * {@code "expect"}
         */
        public static final AsciiString EXPECT = new AsciiString("expect");
        /**
         * {@code "expires"}
         */
        public static final AsciiString EXPIRES = new AsciiString("expires");
        /**
         * {@code "from"}
         */
        public static final AsciiString FROM = new AsciiString("from");
        /**
         * {@code "host"}
         */
        public static final AsciiString HOST = new AsciiString("host");
        /**
         * {@code "if-match"}
         */
        public static final AsciiString IF_MATCH = new AsciiString("if-match");
        /**
         * {@code "if-modified-since"}
         */
        public static final AsciiString IF_MODIFIED_SINCE = new AsciiString("if-modified-since");
        /**
         * {@code "if-none-match"}
         */
        public static final AsciiString IF_NONE_MATCH = new AsciiString("if-none-match");
        /**
         * {@code "if-range"}
         */
        public static final AsciiString IF_RANGE = new AsciiString("if-range");
        /**
         * {@code "if-unmodified-since"}
         */
        public static final AsciiString IF_UNMODIFIED_SINCE = new AsciiString("if-unmodified-since");
        /**
         * {@code "last-modified"}
         */
        public static final AsciiString LAST_MODIFIED = new AsciiString("last-modified");
        /**
         * {@code "location"}
         */
        public static final AsciiString LOCATION = new AsciiString("location");
        /**
         * {@code "max-forwards"}
         */
        public static final AsciiString MAX_FORWARDS = new AsciiString("max-forwards");
        /**
         * {@code "origin"}
         */
        public static final AsciiString ORIGIN = new AsciiString("origin");
        /**
         * {@code "pragma"}
         */
        public static final AsciiString PRAGMA = new AsciiString("pragma");
        /**
         * {@code "proxy-authenticate"}
         */
        public static final AsciiString PROXY_AUTHENTICATE = new AsciiString("proxy-authenticate");
        /**
         * {@code "proxy-authorization"}
         */
        public static final AsciiString PROXY_AUTHORIZATION = new AsciiString("proxy-authorization");
        /**
         * {@code "range"}
         */
        public static final AsciiString RANGE = new AsciiString("range");
        /**
         * {@code "referer"}
         */
        public static final AsciiString REFERER = new AsciiString("referer");
        /**
         * {@code "retry-after"}
         */
        public static final AsciiString RETRY_AFTER = new AsciiString("retry-after");
        /**
         * {@code "sec-websocket-key1"}
         */
        public static final AsciiString SEC_WEBSOCKET_KEY1 = new AsciiString("sec-websocket-key1");
        /**
         * {@code "sec-websocket-key2"}
         */
        public static final AsciiString SEC_WEBSOCKET_KEY2 = new AsciiString("sec-websocket-key2");
        /**
         * {@code "sec-websocket-location"}
         */
        public static final AsciiString SEC_WEBSOCKET_LOCATION = new AsciiString("sec-websocket-location");
        /**
         * {@code "sec-websocket-origin"}
         */
        public static final AsciiString SEC_WEBSOCKET_ORIGIN = new AsciiString("sec-websocket-origin");
        /**
         * {@code "sec-websocket-protocol"}
         */
        public static final AsciiString SEC_WEBSOCKET_PROTOCOL = new AsciiString("sec-websocket-protocol");
        /**
         * {@code "sec-websocket-version"}
         */
        public static final AsciiString SEC_WEBSOCKET_VERSION = new AsciiString("sec-websocket-version");
        /**
         * {@code "sec-websocket-key"}
         */
        public static final AsciiString SEC_WEBSOCKET_KEY = new AsciiString("sec-websocket-key");
        /**
         * {@code "sec-websocket-accept"}
         */
        public static final AsciiString SEC_WEBSOCKET_ACCEPT = new AsciiString("sec-websocket-accept");
        /**
         * {@code "sec-websocket-protocol"}
         */
        public static final AsciiString SEC_WEBSOCKET_EXTENSIONS = new AsciiString("sec-websocket-extensions");
        /**
         * {@code "server"}
         */
        public static final AsciiString SERVER = new AsciiString("server");
        /**
         * {@code "set-cookie"}
         */
        public static final AsciiString SET_COOKIE = new AsciiString("set-cookie");
        /**
         * {@code "set-cookie2"}
         */
        public static final AsciiString SET_COOKIE2 = new AsciiString("set-cookie2");
        /**
         * {@code "te"}
         */
        public static final AsciiString TE = new AsciiString("te");
        /**
         * {@code "trailer"}
         */
        public static final AsciiString TRAILER = new AsciiString("trailer");
        /**
         * {@code "transfer-encoding"}
         */
        public static final AsciiString TRANSFER_ENCODING = new AsciiString("transfer-encoding");
        /**
         * {@code "upgrade"}
         */
        public static final AsciiString UPGRADE = new AsciiString("upgrade");
        /**
         * {@code "user-agent"}
         */
        public static final AsciiString USER_AGENT = new AsciiString("user-agent");
        /**
         * {@code "vary"}
         */
        public static final AsciiString VARY = new AsciiString("vary");
        /**
         * {@code "via"}
         */
        public static final AsciiString VIA = new AsciiString("via");
        /**
         * {@code "warning"}
         */
        public static final AsciiString WARNING = new AsciiString("warning");
        /**
         * {@code "websocket-location"}
         */
        public static final AsciiString WEBSOCKET_LOCATION = new AsciiString("websocket-location");
        /**
         * {@code "websocket-origin"}
         */
        public static final AsciiString WEBSOCKET_ORIGIN = new AsciiString("websocket-origin");
        /**
         * {@code "websocket-protocol"}
         */
        public static final AsciiString WEBSOCKET_PROTOCOL = new AsciiString("websocket-protocol");
        /**
         * {@code "www-authenticate"}
         */
        public static final AsciiString WWW_AUTHENTICATE = new AsciiString("www-authenticate");
        /**
         * {@code "keep-alive"}
         * @deprecated use {@link #CONNECTION}
         */
        @Deprecated
        public static final AsciiString KEEP_ALIVE = new AsciiString("keep-alive");
        /**
         * {@code "proxy-connection"}
         * @deprecated use {@link #CONNECTION}
         */
        @Deprecated
        public static final AsciiString PROXY_CONNECTION = new AsciiString("proxy-connection");

        private Names() {
        }
    }

    /**
     * Standard HTTP header values.
     */
    public static final class Values {
        /**
         * {@code "application/x-www-form-urlencoded"}
         */
        public static final AsciiString APPLICATION_X_WWW_FORM_URLENCODED =
                new AsciiString("application/x-www-form-urlencoded");
        /**
         * {@code "application/octet-stream"}
         */
        public static final AsciiString APPLICATION_OCTET_STREAM = new AsciiString("application/octet-stream");
        /**
         * {@code "text/plain"}
         */
        public static final AsciiString TEXT_PLAIN = new AsciiString("text/plain");
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
         * {@code "multipart/mixed"}
         */
        public static final AsciiString MULTIPART_MIXED = new AsciiString("multipart/mixed");
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
        public static final AsciiString WEBSOCKET = new AsciiString("WebSocket");
        /**
         * {@code "name"}
         * See {@link Names#CONTENT_DISPOSITION}
         */
        public static final AsciiString NAME = new AsciiString("name");
        /**
         * {@code "filename"}
         * See {@link Names#CONTENT_DISPOSITION}
         */
        public static final AsciiString FILENAME = new AsciiString("filename");
        /**
         * {@code "form-data"}
         * See {@link Names#CONTENT_DISPOSITION}
         */
        public static final AsciiString FORM_DATA = new AsciiString("form-data");
        /**
         * {@code "attachment"}
         * See {@link Names#CONTENT_DISPOSITION}
         */
        public static final AsciiString ATTACHMENT = new AsciiString("attachment");
        /**
         * {@code "file"}
         * See {@link Names#CONTENT_DISPOSITION}
         */
        public static final AsciiString FILE = new AsciiString("file");

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
        if (connection != null && AsciiString.equalsIgnoreCase(CLOSE_ENTITY, connection)) {
            return false;
        }

        if (message.protocolVersion().isKeepAliveDefault()) {
            return !AsciiString.equalsIgnoreCase(CLOSE_ENTITY, connection);
        } else {
            return AsciiString.equalsIgnoreCase(KEEP_ALIVE_ENTITY, connection);
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
        if (message.protocolVersion().isKeepAliveDefault()) {
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
            if (HttpMethod.GET.equals(req.method()) &&
                h.contains(SEC_WEBSOCKET_KEY1_ENTITY) &&
                h.contains(SEC_WEBSOCKET_KEY2_ENTITY)) {
                return 8;
            }
        } else if (message instanceof HttpResponse) {
            HttpResponse res = (HttpResponse) message;
            if (res.status().code() == 101 &&
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
        if (message.protocolVersion().compareTo(HttpVersion.HTTP_1_1) < 0) {
            return false;
        }

        // In most cases, there will be one or zero 'Expect' header.
        String value = message.headers().get(EXPECT_ENTITY);
        if (value == null) {
            return false;
        }
        if (AsciiString.equalsIgnoreCase(CONTINUE_ENTITY, value)) {
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
     * specified message.  If the specified {@code value} is {@code true},
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
            if (AsciiString.equalsIgnoreCase(value, CHUNKED_ENTITY)) {
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
     * @deprecated Use {@link AsciiString#equalsIgnoreCase(CharSequence, CharSequence)} instead.
     */
    @Deprecated
    public static boolean equalsIgnoreCase(CharSequence name1, CharSequence name2) {
        return AsciiString.equalsIgnoreCase(name1, name2);
    }

    static void encode(HttpHeaders headers, ByteBuf buf) throws Exception {
        if (headers instanceof DefaultHttpHeaders) {
            ((DefaultHttpHeaders) headers).encode(buf);
        } else {
            for (Entry<String, String> header: headers) {
                encode(header.getKey(), header.getValue(), buf);
            }
        }
    }

    @SuppressWarnings("deprecation")
    private static void encode(CharSequence key, CharSequence value, ByteBuf buf) {
        encodeAscii(key, buf);
        buf.writeBytes(HEADER_SEPERATOR);
        encodeAscii(value, buf);
        buf.writeBytes(CRLF);
    }

    @Deprecated
    public static void encodeAscii(CharSequence seq, ByteBuf buf) {
        if (seq instanceof AsciiString) {
            ((AsciiString) seq).copy(0, buf, seq.length());
        } else {
            encodeAscii0(seq, buf);
        }
    }

    static void encodeAscii0(CharSequence seq, ByteBuf buf) {
        int length = seq.length();
        for (int i = 0 ; i < length; i++) {
            buf.writeByte((byte) seq.charAt(i));
        }
    }

    /**
     * Create a new {@link CharSequence} which is optimized for reuse as {@link HttpHeaders} name or value.
     * So if you have a Header name or value that you want to reuse you should make use of this.
     */
    public static CharSequence newEntity(String name) {
        if (name == null) {
            throw new NullPointerException("name");
        }
        return new AsciiString(name);
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

        clear();
        if (headers.isEmpty()) {
            return this;
        }

        for (Map.Entry<String, String> e: headers) {
            add(e.getKey(), e.getValue());
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
    public boolean contains(String name, String value, boolean ignoreCase) {
        List<String> values = getAll(name);
        if (values.isEmpty()) {
            return false;
        }

        for (String v: values) {
            if (ignoreCase) {
                if (AsciiString.equalsIgnoreCase(v, value)) {
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
     * Returns {@code true} if a header with the name and value exists.
     *
     * @param name         the headername
     * @param value        the value
     * @param ignoreCase   {@code true} if case should be ignored
     * @return contains    {@code true} if it contains it {@code false} otherwise
     */
    public boolean contains(CharSequence name, CharSequence value, boolean ignoreCase) {
        return contains(name.toString(), value.toString(), ignoreCase);
    }
}
