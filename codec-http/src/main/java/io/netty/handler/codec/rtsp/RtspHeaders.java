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
        public static final CharSequence ALLOW = HttpHeaders.newEntity("Allow");
        /**
         * {@code "Authorization"}
         */
        public static final CharSequence AUTHORIZATION = HttpHeaders.Names.AUTHORIZATION;
        /**
         * {@code "Bandwidth"}
         */
        public static final CharSequence BANDWIDTH = HttpHeaders.newEntity("Bandwidth");
        /**
         * {@code "Blocksize"}
         */
        public static final CharSequence BLOCKSIZE = HttpHeaders.newEntity("Blocksize");
        /**
         * {@code "Cache-Control"}
         */
        public static final CharSequence CACHE_CONTROL = HttpHeaders.Names.CACHE_CONTROL;
        /**
         * {@code "Conference"}
         */
        public static final CharSequence CONFERENCE = HttpHeaders.newEntity("Conference");
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
        public static final CharSequence CSEQ = HttpHeaders.newEntity("CSeq");
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
        public static final CharSequence KEYMGMT = HttpHeaders.newEntity("KeyMgmt");
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
        public static final CharSequence PROXY_REQUIRE = HttpHeaders.newEntity("Proxy-Require");
        /**
         * {@code "Public"}
         */
        public static final CharSequence PUBLIC = HttpHeaders.newEntity("Public");
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
        public static final CharSequence REQUIRE = HttpHeaders.newEntity("Require");
        /**
         * {@code "Retry-After"}
         */
        public static final CharSequence RETRT_AFTER = HttpHeaders.Names.RETRY_AFTER;
        /**
         * {@code "RTP-Info"}
         */
        public static final CharSequence RTP_INFO = HttpHeaders.newEntity("RTP-Info");
        /**
         * {@code "Scale"}
         */
        public static final CharSequence SCALE = HttpHeaders.newEntity("Scale");
        /**
         * {@code "Session"}
         */
        public static final CharSequence SESSION = HttpHeaders.newEntity("Session");
        /**
         * {@code "Server"}
         */
        public static final CharSequence SERVER = HttpHeaders.Names.SERVER;
        /**
         * {@code "Speed"}
         */
        public static final CharSequence SPEED = HttpHeaders.newEntity("Speed");
        /**
         * {@code "Timestamp"}
         */
        public static final CharSequence TIMESTAMP = HttpHeaders.newEntity("Timestamp");
        /**
         * {@code "Transport"}
         */
        public static final CharSequence TRANSPORT = HttpHeaders.newEntity("Transport");
        /**
         * {@code "Unsupported"}
         */
        public static final CharSequence UNSUPPORTED = HttpHeaders.newEntity("Unsupported");
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
        public static final CharSequence APPEND = HttpHeaders.newEntity("append");
        /**
         * {@code "AVP"}
         */
        public static final CharSequence AVP = HttpHeaders.newEntity("AVP");
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
        public static final CharSequence CLIENT_PORT = HttpHeaders.newEntity("client_port");
        /**
         * {@code "clock"}
         */
        public static final CharSequence CLOCK = HttpHeaders.newEntity("clock");
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
        public static final CharSequence DESTINATION = HttpHeaders.newEntity("destination");
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
        public static final CharSequence INTERLEAVED = HttpHeaders.newEntity("interleaved");
        /**
         * {@code "keep-alive"}
         */
        public static final CharSequence KEEP_ALIVE = HttpHeaders.Values.KEEP_ALIVE;
        /**
         * {@code "layers"}
         */
        public static final CharSequence LAYERS = HttpHeaders.newEntity("layers");
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
        public static final CharSequence MODE = HttpHeaders.newEntity("mode");
        /**
         * {@code "multicast"}
         */
        public static final CharSequence MULTICAST = HttpHeaders.newEntity("multicast");
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
        public static final CharSequence PORT = HttpHeaders.newEntity("port");
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
        public static final CharSequence RTP = HttpHeaders.newEntity("RTP");
        /**
         * {@code "rtptime"}
         */
        public static final CharSequence RTPTIME = HttpHeaders.newEntity("rtptime");
        /**
         * {@code "seq"}
         */
        public static final CharSequence SEQ = HttpHeaders.newEntity("seq");
        /**
         * {@code "server_port"}
         */
        public static final CharSequence SERVER_PORT = HttpHeaders.newEntity("server_port");
        /**
         * {@code "ssrc"}
         */
        public static final CharSequence SSRC = HttpHeaders.newEntity("ssrc");
        /**
         * {@code "TCP"}
         */
        public static final CharSequence TCP = HttpHeaders.newEntity("TCP");
        /**
         * {@code "time"}
         */
        public static final CharSequence TIME = HttpHeaders.newEntity("time");
        /**
         * {@code "timeout"}
         */
        public static final CharSequence TIMEOUT = HttpHeaders.newEntity("timeout");
        /**
         * {@code "ttl"}
         */
        public static final CharSequence TTL = HttpHeaders.newEntity("ttl");
        /**
         * {@code "UDP"}
         */
        public static final CharSequence UDP = HttpHeaders.newEntity("UDP");
        /**
         * {@code "unicast"}
         */
        public static final CharSequence UNICAST = HttpHeaders.newEntity("unicast");
        /**
         * {@code "url"}
         */
        public static final CharSequence URL = HttpHeaders.newEntity("url");

        private Values() { }
    }

    private RtspHeaders() { }
}
