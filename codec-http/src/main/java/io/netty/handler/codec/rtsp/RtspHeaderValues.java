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

package io.netty.handler.codec.rtsp;

import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.util.AsciiString;

/**
 * Standard RTSP header names.
 */
public final class RtspHeaderValues {
    /**
     * {@code "append"}
     */
    public static final AsciiString APPEND = AsciiString.cached("append");
    /**
     * {@code "AVP"}
     */
    public static final AsciiString AVP = AsciiString.cached("AVP");
    /**
     * {@code "bytes"}
     */
    public static final AsciiString BYTES = HttpHeaderValues.BYTES;
    /**
     * {@code "charset"}
     */
    public static final AsciiString CHARSET = HttpHeaderValues.CHARSET;
    /**
     * {@code "client_port"}
     */
    public static final AsciiString CLIENT_PORT = AsciiString.cached("client_port");
    /**
     * {@code "clock"}
     */
    public static final AsciiString CLOCK = AsciiString.cached("clock");
    /**
     * {@code "close"}
     */
    public static final AsciiString CLOSE = HttpHeaderValues.CLOSE;
    /**
     * {@code "compress"}
     */
    public static final AsciiString COMPRESS = HttpHeaderValues.COMPRESS;
    /**
     * {@code "100-continue"}
     */
    public static final AsciiString CONTINUE =  HttpHeaderValues.CONTINUE;
    /**
     * {@code "deflate"}
     */
    public static final AsciiString DEFLATE = HttpHeaderValues.DEFLATE;
    /**
     * {@code "destination"}
     */
    public static final AsciiString DESTINATION = AsciiString.cached("destination");
    /**
     * {@code "gzip"}
     */
    public static final AsciiString GZIP = HttpHeaderValues.GZIP;
    /**
     * {@code "identity"}
     */
    public static final AsciiString IDENTITY = HttpHeaderValues.IDENTITY;
    /**
     * {@code "interleaved"}
     */
    public static final AsciiString INTERLEAVED = AsciiString.cached("interleaved");
    /**
     * {@code "keep-alive"}
     */
    public static final AsciiString KEEP_ALIVE = HttpHeaderValues.KEEP_ALIVE;
    /**
     * {@code "layers"}
     */
    public static final AsciiString LAYERS = AsciiString.cached("layers");
    /**
     * {@code "max-age"}
     */
    public static final AsciiString MAX_AGE = HttpHeaderValues.MAX_AGE;
    /**
     * {@code "max-stale"}
     */
    public static final AsciiString MAX_STALE = HttpHeaderValues.MAX_STALE;
    /**
     * {@code "min-fresh"}
     */
    public static final AsciiString MIN_FRESH = HttpHeaderValues.MIN_FRESH;
    /**
     * {@code "mode"}
     */
    public static final AsciiString MODE = AsciiString.cached("mode");
    /**
     * {@code "multicast"}
     */
    public static final AsciiString MULTICAST = AsciiString.cached("multicast");
    /**
     * {@code "must-revalidate"}
     */
    public static final AsciiString MUST_REVALIDATE = HttpHeaderValues.MUST_REVALIDATE;
    /**
     * {@code "none"}
     */
    public static final AsciiString NONE = HttpHeaderValues.NONE;
    /**
     * {@code "no-cache"}
     */
    public static final AsciiString NO_CACHE = HttpHeaderValues.NO_CACHE;
    /**
     * {@code "no-transform"}
     */
    public static final AsciiString NO_TRANSFORM = HttpHeaderValues.NO_TRANSFORM;
    /**
     * {@code "only-if-cached"}
     */
    public static final AsciiString ONLY_IF_CACHED = HttpHeaderValues.ONLY_IF_CACHED;
    /**
     * {@code "port"}
     */
    public static final AsciiString PORT = AsciiString.cached("port");
    /**
     * {@code "private"}
     */
    public static final AsciiString PRIVATE = HttpHeaderValues.PRIVATE;
    /**
     * {@code "proxy-revalidate"}
     */
    public static final AsciiString PROXY_REVALIDATE = HttpHeaderValues.PROXY_REVALIDATE;
    /**
     * {@code "public"}
     */
    public static final AsciiString PUBLIC = HttpHeaderValues.PUBLIC;
    /**
     * {@code "RTP"}
     */
    public static final AsciiString RTP = AsciiString.cached("RTP");
    /**
     * {@code "rtptime"}
     */
    public static final AsciiString RTPTIME = AsciiString.cached("rtptime");
    /**
     * {@code "seq"}
     */
    public static final AsciiString SEQ = AsciiString.cached("seq");
    /**
     * {@code "server_port"}
     */
    public static final AsciiString SERVER_PORT = AsciiString.cached("server_port");
    /**
     * {@code "ssrc"}
     */
    public static final AsciiString SSRC = AsciiString.cached("ssrc");
    /**
     * {@code "TCP"}
     */
    public static final AsciiString TCP = AsciiString.cached("TCP");
    /**
     * {@code "time"}
     */
    public static final AsciiString TIME = AsciiString.cached("time");
    /**
     * {@code "timeout"}
     */
    public static final AsciiString TIMEOUT = AsciiString.cached("timeout");
    /**
     * {@code "ttl"}
     */
    public static final AsciiString TTL = AsciiString.cached("ttl");
    /**
     * {@code "UDP"}
     */
    public static final AsciiString UDP = AsciiString.cached("UDP");
    /**
     * {@code "unicast"}
     */
    public static final AsciiString UNICAST = AsciiString.cached("unicast");
    /**
     * {@code "url"}
     */
    public static final AsciiString URL = AsciiString.cached("url");

    private RtspHeaderValues() { }
}
