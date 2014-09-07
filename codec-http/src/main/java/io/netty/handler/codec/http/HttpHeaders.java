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
import io.netty.handler.codec.TextHeaderProcessor;
import io.netty.handler.codec.TextHeaders;


/**
 * Provides the constants for the standard HTTP header names and values and
 * commonly used utility methods that accesses an {@link HttpMessage}.
 */
public interface HttpHeaders extends TextHeaders {

    /**
     * Standard HTTP header names.
     */
    final class Names {
        /**
         * {@code "Accept"}
         */
        public static final AsciiString ACCEPT = new AsciiString("Accept");
        /**
         * {@code "Accept-Charset"}
         */
        public static final AsciiString ACCEPT_CHARSET = new AsciiString("Accept-Charset");
        /**
         * {@code "Accept-Encoding"}
         */
        public static final AsciiString ACCEPT_ENCODING = new AsciiString("Accept-Encoding");
        /**
         * {@code "Accept-Language"}
         */
        public static final AsciiString ACCEPT_LANGUAGE = new AsciiString("Accept-Language");
        /**
         * {@code "Accept-Ranges"}
         */
        public static final AsciiString ACCEPT_RANGES = new AsciiString("Accept-Ranges");
        /**
         * {@code "Accept-Patch"}
         */
        public static final AsciiString ACCEPT_PATCH = new AsciiString("Accept-Patch");
        /**
         * {@code "Access-Control-Allow-Credentials"}
         */
        public static final AsciiString ACCESS_CONTROL_ALLOW_CREDENTIALS =
                new AsciiString("Access-Control-Allow-Credentials");
        /**
         * {@code "Access-Control-Allow-Headers"}
         */
        public static final AsciiString ACCESS_CONTROL_ALLOW_HEADERS =
                new AsciiString("Access-Control-Allow-Headers");
        /**
         * {@code "Access-Control-Allow-Methods"}
         */
        public static final AsciiString ACCESS_CONTROL_ALLOW_METHODS =
                new AsciiString("Access-Control-Allow-Methods");
        /**
         * {@code "Access-Control-Allow-Origin"}
         */
        public static final AsciiString ACCESS_CONTROL_ALLOW_ORIGIN =
                new AsciiString("Access-Control-Allow-Origin");
        /**
         * {@code "Access-Control-Expose-Headers"}
         */
        public static final AsciiString ACCESS_CONTROL_EXPOSE_HEADERS =
                new AsciiString("Access-Control-Expose-Headers");
        /**
         * {@code "Access-Control-Max-Age"}
         */
        public static final AsciiString ACCESS_CONTROL_MAX_AGE = new AsciiString("Access-Control-Max-Age");
        /**
         * {@code "Access-Control-Request-Headers"}
         */
        public static final AsciiString ACCESS_CONTROL_REQUEST_HEADERS =
                new AsciiString("Access-Control-Request-Headers");
        /**
         * {@code "Access-Control-Request-Method"}
         */
        public static final AsciiString ACCESS_CONTROL_REQUEST_METHOD =
                new AsciiString("Access-Control-Request-Method");
        /**
         * {@code "Age"}
         */
        public static final AsciiString AGE = new AsciiString("Age");
        /**
         * {@code "Allow"}
         */
        public static final AsciiString ALLOW = new AsciiString("Allow");
        /**
         * {@code "Authorization"}
         */
        public static final AsciiString AUTHORIZATION = new AsciiString("Authorization");
        /**
         * {@code "Cache-Control"}
         */
        public static final AsciiString CACHE_CONTROL = new AsciiString("Cache-Control");
        /**
         * {@code "Connection"}
         */
        public static final AsciiString CONNECTION = new AsciiString("Connection");
        /**
         * {@code "Content-Base"}
         */
        public static final AsciiString CONTENT_BASE = new AsciiString("Content-Base");
        /**
         * {@code "Content-Encoding"}
         */
        public static final AsciiString CONTENT_ENCODING = new AsciiString("Content-Encoding");
        /**
         * {@code "Content-Language"}
         */
        public static final AsciiString CONTENT_LANGUAGE = new AsciiString("Content-Language");
        /**
         * {@code "Content-Length"}
         */
        public static final AsciiString CONTENT_LENGTH = new AsciiString("Content-Length");
        /**
         * {@code "Content-Location"}
         */
        public static final AsciiString CONTENT_LOCATION = new AsciiString("Content-Location");
        /**
         * {@code "Content-Transfer-Encoding"}
         */
        public static final AsciiString CONTENT_TRANSFER_ENCODING = new AsciiString("Content-Transfer-Encoding");
        /**
         * {@code "Content-MD5"}
         */
        public static final AsciiString CONTENT_MD5 = new AsciiString("Content-MD5");
        /**
         * {@code "Content-Range"}
         */
        public static final AsciiString CONTENT_RANGE = new AsciiString("Content-Range");
        /**
         * {@code "Content-Type"}
         */
        public static final AsciiString CONTENT_TYPE = new AsciiString("Content-Type");
        /**
         * {@code "Cookie"}
         */
        public static final AsciiString COOKIE = new AsciiString("Cookie");
        /**
         * {@code "Date"}
         */
        public static final AsciiString DATE = new AsciiString("Date");
        /**
         * {@code "ETag"}
         */
        public static final AsciiString ETAG = new AsciiString("ETag");
        /**
         * {@code "Expect"}
         */
        public static final AsciiString EXPECT = new AsciiString("Expect");
        /**
         * {@code "Expires"}
         */
        public static final AsciiString EXPIRES = new AsciiString("Expires");
        /**
         * {@code "From"}
         */
        public static final AsciiString FROM = new AsciiString("From");
        /**
         * {@code "Host"}
         */
        public static final AsciiString HOST = new AsciiString("Host");
        /**
         * {@code "If-Match"}
         */
        public static final AsciiString IF_MATCH = new AsciiString("If-Match");
        /**
         * {@code "If-Modified-Since"}
         */
        public static final AsciiString IF_MODIFIED_SINCE = new AsciiString("If-Modified-Since");
        /**
         * {@code "If-None-Match"}
         */
        public static final AsciiString IF_NONE_MATCH = new AsciiString("If-None-Match");
        /**
         * {@code "If-Range"}
         */
        public static final AsciiString IF_RANGE = new AsciiString("If-Range");
        /**
         * {@code "If-Unmodified-Since"}
         */
        public static final AsciiString IF_UNMODIFIED_SINCE = new AsciiString("If-Unmodified-Since");
        /**
         * {@code "Last-Modified"}
         */
        public static final AsciiString LAST_MODIFIED = new AsciiString("Last-Modified");
        /**
         * {@code "Location"}
         */
        public static final AsciiString LOCATION = new AsciiString("Location");
        /**
         * {@code "Max-Forwards"}
         */
        public static final AsciiString MAX_FORWARDS = new AsciiString("Max-Forwards");
        /**
         * {@code "Origin"}
         */
        public static final AsciiString ORIGIN = new AsciiString("Origin");
        /**
         * {@code "Pragma"}
         */
        public static final AsciiString PRAGMA = new AsciiString("Pragma");
        /**
         * {@code "Proxy-Authenticate"}
         */
        public static final AsciiString PROXY_AUTHENTICATE = new AsciiString("Proxy-Authenticate");
        /**
         * {@code "Proxy-Authorization"}
         */
        public static final AsciiString PROXY_AUTHORIZATION = new AsciiString("Proxy-Authorization");
        /**
         * {@code "Range"}
         */
        public static final AsciiString RANGE = new AsciiString("Range");
        /**
         * {@code "Referer"}
         */
        public static final AsciiString REFERER = new AsciiString("Referer");
        /**
         * {@code "Retry-After"}
         */
        public static final AsciiString RETRY_AFTER = new AsciiString("Retry-After");
        /**
         * {@code "Sec-WebSocket-Key1"}
         */
        public static final AsciiString SEC_WEBSOCKET_KEY1 = new AsciiString("Sec-WebSocket-Key1");
        /**
         * {@code "Sec-WebSocket-Key2"}
         */
        public static final AsciiString SEC_WEBSOCKET_KEY2 = new AsciiString("Sec-WebSocket-Key2");
        /**
         * {@code "Sec-WebSocket-Location"}
         */
        public static final AsciiString SEC_WEBSOCKET_LOCATION = new AsciiString("Sec-WebSocket-Location");
        /**
         * {@code "Sec-WebSocket-Origin"}
         */
        public static final AsciiString SEC_WEBSOCKET_ORIGIN = new AsciiString("Sec-WebSocket-Origin");
        /**
         * {@code "Sec-WebSocket-Protocol"}
         */
        public static final AsciiString SEC_WEBSOCKET_PROTOCOL = new AsciiString("Sec-WebSocket-Protocol");
        /**
         * {@code "Sec-WebSocket-Version"}
         */
        public static final AsciiString SEC_WEBSOCKET_VERSION = new AsciiString("Sec-WebSocket-Version");
        /**
         * {@code "Sec-WebSocket-Key"}
         */
        public static final AsciiString SEC_WEBSOCKET_KEY = new AsciiString("Sec-WebSocket-Key");
        /**
         * {@code "Sec-WebSocket-Accept"}
         */
        public static final AsciiString SEC_WEBSOCKET_ACCEPT = new AsciiString("Sec-WebSocket-Accept");
        /**
         * {@code "Sec-WebSocket-Protocol"}
         */
        public static final AsciiString SEC_WEBSOCKET_EXTENSIONS = new AsciiString("Sec-WebSocket-Extensions");
        /**
         * {@code "Server"}
         */
        public static final AsciiString SERVER = new AsciiString("Server");
        /**
         * {@code "Set-Cookie"}
         */
        public static final AsciiString SET_COOKIE = new AsciiString("Set-Cookie");
        /**
         * {@code "Set-Cookie2"}
         */
        public static final AsciiString SET_COOKIE2 = new AsciiString("Set-Cookie2");
        /**
         * {@code "TE"}
         */
        public static final AsciiString TE = new AsciiString("TE");
        /**
         * {@code "Trailer"}
         */
        public static final AsciiString TRAILER = new AsciiString("Trailer");
        /**
         * {@code "Transfer-Encoding"}
         */
        public static final AsciiString TRANSFER_ENCODING = new AsciiString("Transfer-Encoding");
        /**
         * {@code "Upgrade"}
         */
        public static final AsciiString UPGRADE = new AsciiString("Upgrade");
        /**
         * {@code "User-Agent"}
         */
        public static final AsciiString USER_AGENT = new AsciiString("User-Agent");
        /**
         * {@code "Vary"}
         */
        public static final AsciiString VARY = new AsciiString("Vary");
        /**
         * {@code "Via"}
         */
        public static final AsciiString VIA = new AsciiString("Via");
        /**
         * {@code "Warning"}
         */
        public static final AsciiString WARNING = new AsciiString("Warning");
        /**
         * {@code "WebSocket-Location"}
         */
        public static final AsciiString WEBSOCKET_LOCATION = new AsciiString("WebSocket-Location");
        /**
         * {@code "WebSocket-Origin"}
         */
        public static final AsciiString WEBSOCKET_ORIGIN = new AsciiString("WebSocket-Origin");
        /**
         * {@code "WebSocket-Protocol"}
         */
        public static final AsciiString WEBSOCKET_PROTOCOL = new AsciiString("WebSocket-Protocol");
        /**
         * {@code "WWW-Authenticate"}
         */
        public static final AsciiString WWW_AUTHENTICATE = new AsciiString("WWW-Authenticate");
        /**
         * {@code "Keep-Alive"}
         * @deprecated use {@link #CONNECTION}
         */
        @Deprecated
        public static final AsciiString KEEP_ALIVE = new AsciiString("Keep-Alive");
        /**
         * {@code "Proxy-Connection"}
         * @deprecated use {@link #CONNECTION}
         */
        @Deprecated
        public static final AsciiString PROXY_CONNECTION = new AsciiString("Proxy-Connection");

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

        private Values() {
        }
    }

    @Override
    HttpHeaders add(CharSequence name, Object value);

    @Override
    HttpHeaders add(CharSequence name, Iterable<?> values);

    @Override
    HttpHeaders add(CharSequence name, Object... values);

    @Override
    HttpHeaders add(TextHeaders headers);

    @Override
    HttpHeaders set(CharSequence name, Object value);

    @Override
    HttpHeaders set(CharSequence name, Iterable<?> values);

    @Override
    HttpHeaders set(CharSequence name, Object... values);

    @Override
    HttpHeaders set(TextHeaders headers);

    @Override
    HttpHeaders clear();

    @Override
    HttpHeaders forEachEntry(TextHeaderProcessor processor);
}
