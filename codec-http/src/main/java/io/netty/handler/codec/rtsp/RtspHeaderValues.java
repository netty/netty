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
    public static final AsciiString APPEND = new AsciiString("append");
    /**
     * {@code "AVP"}
     */
    public static final AsciiString AVP = new AsciiString("AVP");
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
    public static final AsciiString CLIENT_PORT = new AsciiString("client_port");
    /**
     * {@code "clock"}
     */
    public static final AsciiString CLOCK = new AsciiString("clock");
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
    public static final AsciiString DESTINATION = new AsciiString("destination");
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
    public static final AsciiString INTERLEAVED = new AsciiString("interleaved");
    /**
     * {@code "keep-alive"}
     */
    public static final AsciiString KEEP_ALIVE = HttpHeaderValues.KEEP_ALIVE;
    /**
     * {@code "layers"}
     */
    public static final AsciiString LAYERS = new AsciiString("layers");
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
    public static final AsciiString MODE = new AsciiString("mode");
    /**
     * {@code "multicast"}
     */
    public static final AsciiString MULTICAST = new AsciiString("multicast");
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
    public static final AsciiString PORT = new AsciiString("port");
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
    public static final AsciiString RTP = new AsciiString("RTP");
    /**
     * {@code "rtptime"}
     */
    public static final AsciiString RTPTIME = new AsciiString("rtptime");
    /**
     * {@code "seq"}
     */
    public static final AsciiString SEQ = new AsciiString("seq");
    /**
     * {@code "server_port"}
     */
    public static final AsciiString SERVER_PORT = new AsciiString("server_port");
    /**
     * {@code "ssrc"}
     */
    public static final AsciiString SSRC = new AsciiString("ssrc");
    /**
     * {@code "TCP"}
     */
    public static final AsciiString TCP = new AsciiString("TCP");
    /**
     * {@code "time"}
     */
    public static final AsciiString TIME = new AsciiString("time");
    /**
     * {@code "timeout"}
     */
    public static final AsciiString TIMEOUT = new AsciiString("timeout");
    /**
     * {@code "ttl"}
     */
    public static final AsciiString TTL = new AsciiString("ttl");
    /**
     * {@code "UDP"}
     */
    public static final AsciiString UDP = new AsciiString("UDP");
    /**
     * {@code "unicast"}
     */
    public static final AsciiString UNICAST = new AsciiString("unicast");
    /**
     * {@code "url"}
     */
    public static final AsciiString URL = new AsciiString("url");

    private RtspHeaderValues() { }
}
