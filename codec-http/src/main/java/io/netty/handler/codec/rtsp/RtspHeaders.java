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
package io.netty.handler.codec.rtsp;

import io.netty.handler.codec.AsciiString;
import io.netty.handler.codec.http.HttpHeaders;


/**
 * Standard RTSP header names and values.
 */
public final class RtspHeaders {

    /**
     * Standard RTSP header names.
     */
    public static final class Names {
        /**
         * {@code "Accept"}
         */
        public static final CharSequence ACCEPT = HttpHeaders.Names.ACCEPT;
        /**
         * {@code "Accept-Encoding"}
         */
        public static final CharSequence ACCEPT_ENCODING = HttpHeaders.Names.ACCEPT_ENCODING;
        /**
         * {@code "Accept-Lanugage"}
         */
        public static final CharSequence ACCEPT_LANGUAGE = HttpHeaders.Names.ACCEPT_LANGUAGE;
        /**
         * {@code "Allow"}
         */
        public static final CharSequence ALLOW = new AsciiString("Allow");
        /**
         * {@code "Authorization"}
         */
        public static final CharSequence AUTHORIZATION = HttpHeaders.Names.AUTHORIZATION;
        /**
         * {@code "Bandwidth"}
         */
        public static final CharSequence BANDWIDTH = new AsciiString("Bandwidth");
        /**
         * {@code "Blocksize"}
         */
        public static final CharSequence BLOCKSIZE = new AsciiString("Blocksize");
        /**
         * {@code "Cache-Control"}
         */
        public static final CharSequence CACHE_CONTROL = HttpHeaders.Names.CACHE_CONTROL;
        /**
         * {@code "Conference"}
         */
        public static final CharSequence CONFERENCE = new AsciiString("Conference");
        /**
         * {@code "Connection"}
         */
        public static final CharSequence CONNECTION = HttpHeaders.Names.CONNECTION;
        /**
         * {@code "Content-Base"}
         */
        public static final CharSequence CONTENT_BASE = HttpHeaders.Names.CONTENT_BASE;
        /**
         * {@code "Content-Encoding"}
         */
        public static final CharSequence CONTENT_ENCODING = HttpHeaders.Names.CONTENT_ENCODING;
        /**
         * {@code "Content-Language"}
         */
        public static final CharSequence CONTENT_LANGUAGE = HttpHeaders.Names.CONTENT_LANGUAGE;
        /**
         * {@code "Content-Length"}
         */
        public static final CharSequence CONTENT_LENGTH = HttpHeaders.Names.CONTENT_LENGTH;
        /**
         * {@code "Content-Location"}
         */
        public static final CharSequence CONTENT_LOCATION = HttpHeaders.Names.CONTENT_LOCATION;
        /**
         * {@code "Content-Type"}
         */
        public static final CharSequence CONTENT_TYPE = HttpHeaders.Names.CONTENT_TYPE;
        /**
         * {@code "CSeq"}
         */
        public static final CharSequence CSEQ = new AsciiString("CSeq");
        /**
         * {@code "Date"}
         */
        public static final CharSequence DATE = HttpHeaders.Names.DATE;
        /**
         * {@code "Expires"}
         */
        public static final CharSequence EXPIRES = HttpHeaders.Names.EXPIRES;
        /**
         * {@code "From"}
         */
        public static final CharSequence FROM = HttpHeaders.Names.FROM;
        /**
         * {@code "Host"}
         */
        public static final CharSequence HOST = HttpHeaders.Names.HOST;
        /**
         * {@code "If-Match"}
         */
        public static final CharSequence IF_MATCH = HttpHeaders.Names.IF_MATCH;
        /**
         * {@code "If-Modified-Since"}
         */
        public static final CharSequence IF_MODIFIED_SINCE = HttpHeaders.Names.IF_MODIFIED_SINCE;
        /**
         * {@code "KeyMgmt"}
         */
        public static final CharSequence KEYMGMT = new AsciiString("KeyMgmt");
        /**
         * {@code "Last-Modified"}
         */
        public static final CharSequence LAST_MODIFIED = HttpHeaders.Names.LAST_MODIFIED;
        /**
         * {@code "Proxy-Authenticate"}
         */
        public static final CharSequence PROXY_AUTHENTICATE = HttpHeaders.Names.PROXY_AUTHENTICATE;
        /**
         * {@code "Proxy-Require"}
         */
        public static final CharSequence PROXY_REQUIRE = new AsciiString("Proxy-Require");
        /**
         * {@code "Public"}
         */
        public static final CharSequence PUBLIC = new AsciiString("Public");
        /**
         * {@code "Range"}
         */
        public static final CharSequence RANGE = HttpHeaders.Names.RANGE;
        /**
         * {@code "Referer"}
         */
        public static final CharSequence REFERER = HttpHeaders.Names.REFERER;
        /**
         * {@code "Require"}
         */
        public static final CharSequence REQUIRE = new AsciiString("Require");
        /**
         * {@code "Retry-After"}
         */
        public static final CharSequence RETRT_AFTER = HttpHeaders.Names.RETRY_AFTER;
        /**
         * {@code "RTP-Info"}
         */
        public static final CharSequence RTP_INFO = new AsciiString("RTP-Info");
        /**
         * {@code "Scale"}
         */
        public static final CharSequence SCALE = new AsciiString("Scale");
        /**
         * {@code "Session"}
         */
        public static final CharSequence SESSION = new AsciiString("Session");
        /**
         * {@code "Server"}
         */
        public static final CharSequence SERVER = HttpHeaders.Names.SERVER;
        /**
         * {@code "Speed"}
         */
        public static final CharSequence SPEED = new AsciiString("Speed");
        /**
         * {@code "Timestamp"}
         */
        public static final CharSequence TIMESTAMP = new AsciiString("Timestamp");
        /**
         * {@code "Transport"}
         */
        public static final CharSequence TRANSPORT = new AsciiString("Transport");
        /**
         * {@code "Unsupported"}
         */
        public static final CharSequence UNSUPPORTED = new AsciiString("Unsupported");
        /**
         * {@code "User-Agent"}
         */
        public static final CharSequence USER_AGENT = HttpHeaders.Names.USER_AGENT;
        /**
         * {@code "Vary"}
         */
        public static final CharSequence VARY = HttpHeaders.Names.VARY;
        /**
         * {@code "Via"}
         */
        public static final CharSequence VIA = HttpHeaders.Names.VIA;
        /**
         * {@code "WWW-Authenticate"}
         */
        public static final CharSequence WWW_AUTHENTICATE = HttpHeaders.Names.WWW_AUTHENTICATE;

        private Names() {
        }
    }

    /**
     * Standard RTSP header values.
     */
    public static final class Values {
        /**
         * {@code "append"}
         */
        public static final CharSequence APPEND = new AsciiString("append");
        /**
         * {@code "AVP"}
         */
        public static final CharSequence AVP = new AsciiString("AVP");
        /**
         * {@code "bytes"}
         */
        public static final CharSequence BYTES = HttpHeaders.Values.BYTES;
        /**
         * {@code "charset"}
         */
        public static final CharSequence CHARSET = HttpHeaders.Values.CHARSET;
        /**
         * {@code "client_port"}
         */
        public static final CharSequence CLIENT_PORT = new AsciiString("client_port");
        /**
         * {@code "clock"}
         */
        public static final CharSequence CLOCK = new AsciiString("clock");
        /**
         * {@code "close"}
         */
        public static final CharSequence CLOSE = HttpHeaders.Values.CLOSE;
        /**
         * {@code "compress"}
         */
        public static final CharSequence COMPRESS = HttpHeaders.Values.COMPRESS;
        /**
         * {@code "100-continue"}
         */
        public static final CharSequence CONTINUE =  HttpHeaders.Values.CONTINUE;
        /**
         * {@code "deflate"}
         */
        public static final CharSequence DEFLATE = HttpHeaders.Values.DEFLATE;
        /**
         * {@code "destination"}
         */
        public static final CharSequence DESTINATION = new AsciiString("destination");
        /**
         * {@code "gzip"}
         */
        public static final CharSequence GZIP = HttpHeaders.Values.GZIP;
        /**
         * {@code "identity"}
         */
        public static final CharSequence IDENTITY = HttpHeaders.Values.IDENTITY;
        /**
         * {@code "interleaved"}
         */
        public static final CharSequence INTERLEAVED = new AsciiString("interleaved");
        /**
         * {@code "keep-alive"}
         */
        public static final CharSequence KEEP_ALIVE = HttpHeaders.Values.KEEP_ALIVE;
        /**
         * {@code "layers"}
         */
        public static final CharSequence LAYERS = new AsciiString("layers");
        /**
         * {@code "max-age"}
         */
        public static final CharSequence MAX_AGE = HttpHeaders.Values.MAX_AGE;
        /**
         * {@code "max-stale"}
         */
        public static final CharSequence MAX_STALE = HttpHeaders.Values.MAX_STALE;
        /**
         * {@code "min-fresh"}
         */
        public static final CharSequence MIN_FRESH = HttpHeaders.Values.MIN_FRESH;
        /**
         * {@code "mode"}
         */
        public static final CharSequence MODE = new AsciiString("mode");
        /**
         * {@code "multicast"}
         */
        public static final CharSequence MULTICAST = new AsciiString("multicast");
        /**
         * {@code "must-revalidate"}
         */
        public static final CharSequence MUST_REVALIDATE = HttpHeaders.Values.MUST_REVALIDATE;
        /**
         * {@code "none"}
         */
        public static final CharSequence NONE = HttpHeaders.Values.NONE;
        /**
         * {@code "no-cache"}
         */
        public static final CharSequence NO_CACHE = HttpHeaders.Values.NO_CACHE;
        /**
         * {@code "no-transform"}
         */
        public static final CharSequence NO_TRANSFORM = HttpHeaders.Values.NO_TRANSFORM;
        /**
         * {@code "only-if-cached"}
         */
        public static final CharSequence ONLY_IF_CACHED = HttpHeaders.Values.ONLY_IF_CACHED;
        /**
         * {@code "port"}
         */
        public static final CharSequence PORT = new AsciiString("port");
        /**
         * {@code "private"}
         */
        public static final CharSequence PRIVATE = HttpHeaders.Values.PRIVATE;
        /**
         * {@code "proxy-revalidate"}
         */
        public static final CharSequence PROXY_REVALIDATE = HttpHeaders.Values.PROXY_REVALIDATE;
        /**
         * {@code "public"}
         */
        public static final CharSequence PUBLIC = HttpHeaders.Values.PUBLIC;
        /**
         * {@code "RTP"}
         */
        public static final CharSequence RTP = new AsciiString("RTP");
        /**
         * {@code "rtptime"}
         */
        public static final CharSequence RTPTIME = new AsciiString("rtptime");
        /**
         * {@code "seq"}
         */
        public static final CharSequence SEQ = new AsciiString("seq");
        /**
         * {@code "server_port"}
         */
        public static final CharSequence SERVER_PORT = new AsciiString("server_port");
        /**
         * {@code "ssrc"}
         */
        public static final CharSequence SSRC = new AsciiString("ssrc");
        /**
         * {@code "TCP"}
         */
        public static final CharSequence TCP = new AsciiString("TCP");
        /**
         * {@code "time"}
         */
        public static final CharSequence TIME = new AsciiString("time");
        /**
         * {@code "timeout"}
         */
        public static final CharSequence TIMEOUT = new AsciiString("timeout");
        /**
         * {@code "ttl"}
         */
        public static final CharSequence TTL = new AsciiString("ttl");
        /**
         * {@code "UDP"}
         */
        public static final CharSequence UDP = new AsciiString("UDP");
        /**
         * {@code "unicast"}
         */
        public static final CharSequence UNICAST = new AsciiString("unicast");
        /**
         * {@code "url"}
         */
        public static final CharSequence URL = new AsciiString("url");

        private Values() { }
    }

    private RtspHeaders() { }
}
