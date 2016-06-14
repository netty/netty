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

import io.netty.util.AsciiString;

/**
 * Standard HTTP header values.
 */
public final class HttpHeaderValues {
    /**
     * {@code "application/json"}
     */
    public static final AsciiString APPLICATION_JSON = new AsciiString("application/json");
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
     * {@code "attachment"}
     * See {@link HttpHeaderNames#CONTENT_DISPOSITION}
     */
    public static final AsciiString ATTACHMENT = new AsciiString("attachment");
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
    public static final AsciiString X_DEFLATE = new AsciiString("x-deflate");
    /**
     * {@code "file"}
     * See {@link HttpHeaderNames#CONTENT_DISPOSITION}
     */
    public static final AsciiString FILE = new AsciiString("file");
    /**
     * {@code "filename"}
     * See {@link HttpHeaderNames#CONTENT_DISPOSITION}
     */
    public static final AsciiString FILENAME = new AsciiString("filename");
    /**
     * {@code "form-data"}
     * See {@link HttpHeaderNames#CONTENT_DISPOSITION}
     */
    public static final AsciiString FORM_DATA = new AsciiString("form-data");
    /**
     * {@code "gzip"}
     */
    public static final AsciiString GZIP = new AsciiString("gzip");
    /**
     * {@code "gzip,deflate"}
     */
    public static final AsciiString GZIP_DEFLATE = new AsciiString("gzip,deflate");
    /**
     * {@code "x-gzip"}
     */
    public static final AsciiString X_GZIP = new AsciiString("x-gzip");
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
     * {@code "name"}
     * See {@link HttpHeaderNames#CONTENT_DISPOSITION}
     */
    public static final AsciiString NAME = new AsciiString("name");
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
     * {@code "0"}
     */
    public static final AsciiString ZERO = new AsciiString("0");
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
     * {@code "text/plain"}
     */
    public static final AsciiString TEXT_PLAIN = new AsciiString("text/plain");
    /**
     * {@code "trailers"}
     */
    public static final AsciiString TRAILERS = new AsciiString("trailers");
    /**
     * {@code "upgrade"}
     */
    public static final AsciiString UPGRADE = new AsciiString("upgrade");
    /**
     * {@code "websocket"}
     */
    public static final AsciiString WEBSOCKET = new AsciiString("websocket");

    private HttpHeaderValues() { }
}
