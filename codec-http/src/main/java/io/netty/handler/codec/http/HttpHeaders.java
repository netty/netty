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

import io.netty.handler.codec.AsciiString;
import io.netty.handler.codec.TextHeaders;


/**
 * Provides the constants for the standard HTTP header names and values and
 * commonly used utility methods that accesses an {@link HttpMessage}.
 */
public interface HttpHeaders extends TextHeaders {
    /**
     * Standard HTTP header names.
     * <p>
     * These are all defined as lowercase to support HTTP/2 requirements while also not
     * violating HTTP/1.x requirements.  New header names should always be lowercase.
     */
    final class Names {
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
    final class Values {
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
        public static final AsciiString BASE64 = new AsciiString("base64");
        /**
         * {@code "binary"}
         */
        public static final AsciiString BINARY = new AsciiString("binary");
        /**
         * {@code "boundary"}
         */
        public static final AsciiString BOUNDARY = new AsciiString("boundary");
        /**
         * {@code "bytes"}
         */
        public static final AsciiString BYTES = new AsciiString("bytes");
        /**
         * {@code "charset"}
         */
        public static final AsciiString CHARSET = new AsciiString("charset");
        /**
         * {@code "chunked"}
         */
        public static final AsciiString CHUNKED = new AsciiString("chunked");
        /**
         * {@code "close"}
         */
        public static final AsciiString CLOSE = new AsciiString("close");
        /**
         * {@code "compress"}
         */
        public static final AsciiString COMPRESS = new AsciiString("compress");
        /**
         * {@code "100-continue"}
         */
        public static final AsciiString CONTINUE = new AsciiString("100-continue");
        /**
         * {@code "deflate"}
         */
        public static final AsciiString DEFLATE = new AsciiString("deflate");
        /**
         * {@code "x-deflate"}
         */
        public static final AsciiString XDEFLATE = new AsciiString("deflate");
        /**
         * {@code "gzip"}
         */
        public static final AsciiString GZIP = new AsciiString("gzip");
        /**
         * {@code "x-gzip"}
         */
        public static final AsciiString XGZIP = new AsciiString("x-gzip");
        /**
         * {@code "identity"}
         */
        public static final AsciiString IDENTITY = new AsciiString("identity");
        /**
         * {@code "keep-alive"}
         */
        public static final AsciiString KEEP_ALIVE = new AsciiString("keep-alive");
        /**
         * {@code "max-age"}
         */
        public static final AsciiString MAX_AGE = new AsciiString("max-age");
        /**
         * {@code "max-stale"}
         */
        public static final AsciiString MAX_STALE = new AsciiString("max-stale");
        /**
         * {@code "min-fresh"}
         */
        public static final AsciiString MIN_FRESH = new AsciiString("min-fresh");
        /**
         * {@code "multipart/form-data"}
         */
        public static final AsciiString MULTIPART_FORM_DATA = new AsciiString("multipart/form-data");
        /**
         * {@code "multipart/mixed"}
         */
        public static final AsciiString MULTIPART_MIXED = new AsciiString("multipart/mixed");
        /**
         * {@code "must-revalidate"}
         */
        public static final AsciiString MUST_REVALIDATE = new AsciiString("must-revalidate");
        /**
         * {@code "no-cache"}
         */
        public static final AsciiString NO_CACHE = new AsciiString("no-cache");
        /**
         * {@code "no-store"}
         */
        public static final AsciiString NO_STORE = new AsciiString("no-store");
        /**
         * {@code "no-transform"}
         */
        public static final AsciiString NO_TRANSFORM = new AsciiString("no-transform");
        /**
         * {@code "none"}
         */
        public static final AsciiString NONE = new AsciiString("none");
        /**
         * {@code "only-if-cached"}
         */
        public static final AsciiString ONLY_IF_CACHED = new AsciiString("only-if-cached");
        /**
         * {@code "private"}
         */
        public static final AsciiString PRIVATE = new AsciiString("private");
        /**
         * {@code "proxy-revalidate"}
         */
        public static final AsciiString PROXY_REVALIDATE = new AsciiString("proxy-revalidate");
        /**
         * {@code "public"}
         */
        public static final AsciiString PUBLIC = new AsciiString("public");
        /**
         * {@code "quoted-printable"}
         */
        public static final AsciiString QUOTED_PRINTABLE = new AsciiString("quoted-printable");
        /**
         * {@code "s-maxage"}
         */
        public static final AsciiString S_MAXAGE = new AsciiString("s-maxage");
        /**
         * {@code "trailers"}
         */
        public static final AsciiString TRAILERS = new AsciiString("trailers");
        /**
         * {@code "Upgrade"}
         */
        public static final AsciiString UPGRADE = new AsciiString("Upgrade");
        /**
         * {@code "WebSocket"}
         */
        public static final AsciiString WEBSOCKET = new AsciiString("WebSocket");
        /**
         * {@code "name"}
         * See {@link #HttpHeaders.Names.CONTENT_DISPOSITION}
         */
        public static final AsciiString NAME = new AsciiString("name");
        /**
         * {@code "filename"}
         * See {@link #HttpHeaders.Names.CONTENT_DISPOSITION}
         */
        public static final AsciiString FILENAME = new AsciiString("filename");
        /**
         * {@code "form-data"}
         * See {@link #HttpHeaders.Names.CONTENT_DISPOSITION}
         */
        public static final AsciiString FORM_DATA = new AsciiString("form-data");
        /**
         * {@code "attachment"}
         * See {@link #HttpHeaders.Names.CONTENT_DISPOSITION}
         */
        public static final AsciiString ATTACHMENT = new AsciiString("attachment");
        /**
         * {@code "file"}
         * See {@link #HttpHeaders.Names.CONTENT_DISPOSITION}
         */
        public static final AsciiString FILE = new AsciiString("file");

        private Values() {
        }
    }

    @Override
    HttpHeaders add(CharSequence name, CharSequence value);

    @Override
    HttpHeaders add(CharSequence name, Iterable<? extends CharSequence> values);

    @Override
    HttpHeaders add(CharSequence name, CharSequence... values);

    @Override
    HttpHeaders addObject(CharSequence name, Object value);

    @Override
    HttpHeaders addObject(CharSequence name, Iterable<?> values);

    @Override
    HttpHeaders addObject(CharSequence name, Object... values);

    @Override
    HttpHeaders addBoolean(CharSequence name, boolean value);

    @Override
    HttpHeaders addByte(CharSequence name, byte value);

    @Override
    HttpHeaders addChar(CharSequence name, char value);

    @Override
    HttpHeaders addShort(CharSequence name, short value);

    @Override
    HttpHeaders addInt(CharSequence name, int value);

    @Override
    HttpHeaders addLong(CharSequence name, long value);

    @Override
    HttpHeaders addFloat(CharSequence name, float value);

    @Override
    HttpHeaders addDouble(CharSequence name, double value);

    @Override
    HttpHeaders add(TextHeaders headers);

    @Override
    HttpHeaders set(CharSequence name, CharSequence value);

    @Override
    HttpHeaders set(CharSequence name, Iterable<? extends CharSequence> values);

    @Override
    HttpHeaders set(CharSequence name, CharSequence... values);

    @Override
    HttpHeaders setObject(CharSequence name, Object value);

    @Override
    HttpHeaders setObject(CharSequence name, Iterable<?> values);

    @Override
    HttpHeaders setObject(CharSequence name, Object... values);

    @Override
    HttpHeaders setBoolean(CharSequence name, boolean value);

    @Override
    HttpHeaders setByte(CharSequence name, byte value);

    @Override
    HttpHeaders setChar(CharSequence name, char value);

    @Override
    HttpHeaders setShort(CharSequence name, short value);

    @Override
    HttpHeaders setInt(CharSequence name, int value);

    @Override
    HttpHeaders setLong(CharSequence name, long value);

    @Override
    HttpHeaders setFloat(CharSequence name, float value);

    @Override
    HttpHeaders setDouble(CharSequence name, double value);

    @Override
    HttpHeaders set(TextHeaders headers);

    @Override
    HttpHeaders setAll(TextHeaders headers);

    @Override
    HttpHeaders clear();
}
