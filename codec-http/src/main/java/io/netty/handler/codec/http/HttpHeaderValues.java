/*
 * Copyright 2014 The Netty Project
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

/**
 * Standard HTTP header values.
 */
public final class HttpHeaderValues {
    /**
     * {@code "application/json"}
     */
    public static final AsciiString APPLICATION_JSON = AsciiString.cached("application/json");
    /**
     * {@code "application/manifest+json"}
     */
    public static final AsciiString APPLICATION_MANIFEST_JSON = AsciiString.cached("application/manifest+json");
    /**
     * {@code "application/octet-stream"}
     */
    public static final AsciiString APPLICATION_OCTET_STREAM = AsciiString.cached("application/octet-stream");
    /**
     * {@code "application/ogg"}
     */
    public static final AsciiString APPLICATION_OGG = AsciiString.cached("application/ogg");
    /**
     * {@code "application/pdf"}
     */
    public static final AsciiString APPLICATION_PDF = AsciiString.cached("application/pdf");
    /**
     * {@code "application/rtf"}
     */
    public static final AsciiString APPLICATION_RTF = AsciiString.cached("application/rtf");
    /**
     * {@code "application/wasm"}
     */
    public static final AsciiString APPLICATION_WASM = AsciiString.cached("application/wasm");
    /**
     * {@code "application/x-www-form-urlencoded"}
     */
    public static final AsciiString APPLICATION_X_WWW_FORM_URLENCODED =
            AsciiString.cached("application/x-www-form-urlencoded");
    /**
     * {@code "application/xhtml+xml"}
     */
    public static final AsciiString APPLICATION_XHTML = AsciiString.cached("application/xhtml+xml");
    /**
     * {@code "application/xml"}
     */
    public static final AsciiString APPLICATION_XML = AsciiString.cached("application/xml");
    /**
     * {@code "application/zstd"}
     */
    public static final AsciiString APPLICATION_ZSTD = AsciiString.cached("application/zstd");
    /**
     * {@code "attachment"}
     * See {@link HttpHeaderNames#CONTENT_DISPOSITION}
     */
    public static final AsciiString ATTACHMENT = AsciiString.cached("attachment");
    /**
     * {@code "audio/aac"}
     */
    public static final AsciiString AUDIO_AAC = AsciiString.cached("audio/aac");
    /**
     * {@code "audio/midi"}
     */
    public static final AsciiString AUDIO_MIDI = AsciiString.cached("audio/midi");
    /**
     * {@code "audio/x-midi"}
     */
    public static final AsciiString AUDIO_X_MIDI = AsciiString.cached("audio/x-midi");
    /**
     * {@code "audio/mpeg"}
     */
    public static final AsciiString AUDIO_MPEG = AsciiString.cached("audio/mpeg");
    /**
     * {@code "audio/ogg"}
     */
    public static final AsciiString AUDIO_OGG = AsciiString.cached("audio/ogg");
    /**
     * {@code "audio/wav"}
     */
    public static final AsciiString AUDIO_WAV = AsciiString.cached("audio/wav");
    /**
     * {@code "audio/webm"}
     */
    public static final AsciiString AUDIO_WEBM = AsciiString.cached("audio/webm");
    /**
     * {@code "base64"}
     */
    public static final AsciiString BASE64 = AsciiString.cached("base64");
    /**
     * {@code "binary"}
     */
    public static final AsciiString BINARY = AsciiString.cached("binary");
    /**
     * {@code "boundary"}
     */
    public static final AsciiString BOUNDARY = AsciiString.cached("boundary");
    /**
     * {@code "bytes"}
     */
    public static final AsciiString BYTES = AsciiString.cached("bytes");
    /**
     * {@code "charset"}
     */
    public static final AsciiString CHARSET = AsciiString.cached("charset");
    /**
     * {@code "chunked"}
     */
    public static final AsciiString CHUNKED = AsciiString.cached("chunked");
    /**
     * {@code "close"}
     */
    public static final AsciiString CLOSE = AsciiString.cached("close");
    /**
     * {@code "compress"}
     */
    public static final AsciiString COMPRESS = AsciiString.cached("compress");
    /**
     * {@code "100-continue"}
     */
    public static final AsciiString CONTINUE = AsciiString.cached("100-continue");
    /**
     * {@code "deflate"}
     */
    public static final AsciiString DEFLATE = AsciiString.cached("deflate");
    /**
     * {@code "x-deflate"}
     */
    public static final AsciiString X_DEFLATE = AsciiString.cached("x-deflate");
    /**
     * {@code "file"}
     * See {@link HttpHeaderNames#CONTENT_DISPOSITION}
     */
    public static final AsciiString FILE = AsciiString.cached("file");
    /**
     * {@code "filename"}
     * See {@link HttpHeaderNames#CONTENT_DISPOSITION}
     */
    public static final AsciiString FILENAME = AsciiString.cached("filename");
    /**
     * {@code "font/otf"}
     */
    public static final AsciiString FONT_OTF = AsciiString.cached("font/otf");
    /**
     * {@code "font/ttf"}
     */
    public static final AsciiString FONT_TTF = AsciiString.cached("font/ttf");
    /**
     * {@code "font/woff"}
     */
    public static final AsciiString FONT_WOFF = AsciiString.cached("font/woff");
    /**
     * {@code "font/woff2"}
     */
    public static final AsciiString FONT_WOFF2 = AsciiString.cached("font/woff2");
    /**
     * {@code "form-data"}
     * See {@link HttpHeaderNames#CONTENT_DISPOSITION}
     */
    public static final AsciiString FORM_DATA = AsciiString.cached("form-data");
    /**
     * {@code "gzip"}
     */
    public static final AsciiString GZIP = AsciiString.cached("gzip");
    /**
     * {@code "br"}
     */
    public static final AsciiString BR = AsciiString.cached("br");

    /**
     * {@code "snappy"}
     */
    public static final AsciiString SNAPPY = AsciiString.cached("snappy");

    /**
     * {@code "zstd"}
     */
    public static final AsciiString ZSTD = AsciiString.cached("zstd");
    /**
     * {@code "gzip,deflate"}
     */
    public static final AsciiString GZIP_DEFLATE = AsciiString.cached("gzip,deflate");
    /**
     * {@code "x-gzip"}
     */
    public static final AsciiString X_GZIP = AsciiString.cached("x-gzip");
    /**
     * {@code "identity"}
     */
    public static final AsciiString IDENTITY = AsciiString.cached("identity");
    /**
     * {@code "image/avif"}
     */
    public static final AsciiString IMAGE_AVIF = AsciiString.cached("image/avif");
    /**
     * {@code "image/bmp"}
     */
    public static final AsciiString IMAGE_BMP = AsciiString.cached("image/bmp");
    /**
     * {@code "image/jpeg"}
     */
    public static final AsciiString IMAGE_JPEG = AsciiString.cached("image/jpeg");
    /**
     * {@code "image/png"}
     */
    public static final AsciiString IMAGE_PNG = AsciiString.cached("image/png");
    /**
     * {@code "image/svg+xml"}
     */
    public static final AsciiString IMAGE_SVG_XML = AsciiString.cached("image/svg+xml");
    /**
     * {@code "image/tiff"}
     */
    public static final AsciiString IMAGE_TIFF = AsciiString.cached("image/tiff");
    /**
     * {@code "image/webp"}
     */
    public static final AsciiString IMAGE_WEBP = AsciiString.cached("image/webp");
    /**
     * {@code "keep-alive"}
     */
    public static final AsciiString KEEP_ALIVE = AsciiString.cached("keep-alive");
    /**
     * {@code "max-age"}
     */
    public static final AsciiString MAX_AGE = AsciiString.cached("max-age");
    /**
     * {@code "max-stale"}
     */
    public static final AsciiString MAX_STALE = AsciiString.cached("max-stale");
    /**
     * {@code "min-fresh"}
     */
    public static final AsciiString MIN_FRESH = AsciiString.cached("min-fresh");
    /**
     * {@code "multipart/form-data"}
     */
    public static final AsciiString MULTIPART_FORM_DATA = AsciiString.cached("multipart/form-data");
    /**
     * {@code "multipart/mixed"}
     */
    public static final AsciiString MULTIPART_MIXED = AsciiString.cached("multipart/mixed");
    /**
     * {@code "must-revalidate"}
     */
    public static final AsciiString MUST_REVALIDATE = AsciiString.cached("must-revalidate");
    /**
     * {@code "name"}
     * See {@link HttpHeaderNames#CONTENT_DISPOSITION}
     */
    public static final AsciiString NAME = AsciiString.cached("name");
    /**
     * {@code "no-cache"}
     */
    public static final AsciiString NO_CACHE = AsciiString.cached("no-cache");
    /**
     * {@code "no-store"}
     */
    public static final AsciiString NO_STORE = AsciiString.cached("no-store");
    /**
     * {@code "no-transform"}
     */
    public static final AsciiString NO_TRANSFORM = AsciiString.cached("no-transform");
    /**
     * {@code "none"}
     */
    public static final AsciiString NONE = AsciiString.cached("none");
    /**
     * {@code "0"}
     */
    public static final AsciiString ZERO = AsciiString.cached("0");
    /**
     * {@code "only-if-cached"}
     */
    public static final AsciiString ONLY_IF_CACHED = AsciiString.cached("only-if-cached");
    /**
     * {@code "private"}
     */
    public static final AsciiString PRIVATE = AsciiString.cached("private");
    /**
     * {@code "proxy-revalidate"}
     */
    public static final AsciiString PROXY_REVALIDATE = AsciiString.cached("proxy-revalidate");
    /**
     * {@code "public"}
     */
    public static final AsciiString PUBLIC = AsciiString.cached("public");
    /**
     * {@code "quoted-printable"}
     */
    public static final AsciiString QUOTED_PRINTABLE = AsciiString.cached("quoted-printable");
    /**
     * {@code "s-maxage"}
     */
    public static final AsciiString S_MAXAGE = AsciiString.cached("s-maxage");
    /**
     * {@code "text/css"}
     */
    public static final AsciiString TEXT_CSS = AsciiString.cached("text/css");
    /**
     * {@code "text/csv"}
     */
    public static final AsciiString TEXT_CSV = AsciiString.cached("text/csv");
    /**
     * {@code "text/html"}
     */
    public static final AsciiString TEXT_HTML = AsciiString.cached("text/html");
    /**
     * {@code "text/javascript"}
     */
    public static final AsciiString TEXT_JAVASCRIPT = AsciiString.cached("text/javascript");
    /**
     * {@code "text/markdown"}
     */
    public static final AsciiString TEXT_MARKDOWN = AsciiString.cached("text/markdown");
    /**
     * {@code "text/event-stream"}
     */
    public static final AsciiString TEXT_EVENT_STREAM = AsciiString.cached("text/event-stream");
    /**
     * {@code "text/plain"}
     */
    public static final AsciiString TEXT_PLAIN = AsciiString.cached("text/plain");
    /**
     * {@code "trailers"}
     */
    public static final AsciiString TRAILERS = AsciiString.cached("trailers");
    /**
     * {@code "upgrade"}
     */
    public static final AsciiString UPGRADE = AsciiString.cached("upgrade");
    /**
     * {@code "video/mp4"}
     */
    public static final AsciiString VIDEO_MP4 = AsciiString.cached("video/mp4");
    /**
     * {@code "video/mpeg"}
     */
    public static final AsciiString VIDEO_MPEG = AsciiString.cached("video/mpeg");
    /**
     * {@code "video/ogg"}
     */
    public static final AsciiString VIDEO_OGG = AsciiString.cached("video/ogg");
    /**
     * {@code "video/webm"}
     */
    public static final AsciiString VIDEO_WEBM = AsciiString.cached("video/webm");
    /**
     * {@code "websocket"}
     */
    public static final AsciiString WEBSOCKET = AsciiString.cached("websocket");
    /**
     * {@code "XmlHttpRequest"}
     */
    public static final AsciiString XML_HTTP_REQUEST = AsciiString.cached("XMLHttpRequest");

    private HttpHeaderValues() { }
}
