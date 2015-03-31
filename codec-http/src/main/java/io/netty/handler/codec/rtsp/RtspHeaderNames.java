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

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.util.AsciiString;

/**
 * Standard RTSP header names.
 * <p>
 * These are all defined as lowercase to support HTTP/2 requirements while also not
 * violating RTSP/1.x requirements.  New header names should always be lowercase.
 */
public final class RtspHeaderNames {
    /**
     * {@code "accept"}
     */
    public static final AsciiString ACCEPT = HttpHeaderNames.ACCEPT;
    /**
     * {@code "accept-encoding"}
     */
    public static final AsciiString ACCEPT_ENCODING = HttpHeaderNames.ACCEPT_ENCODING;
    /**
     * {@code "accept-lanugage"}
     */
    public static final AsciiString ACCEPT_LANGUAGE = HttpHeaderNames.ACCEPT_LANGUAGE;
    /**
     * {@code "allow"}
     */
    public static final AsciiString ALLOW = new AsciiString("allow");
    /**
     * {@code "authorization"}
     */
    public static final AsciiString AUTHORIZATION = HttpHeaderNames.AUTHORIZATION;
    /**
     * {@code "bandwidth"}
     */
    public static final AsciiString BANDWIDTH = new AsciiString("bandwidth");
    /**
     * {@code "blocksize"}
     */
    public static final AsciiString BLOCKSIZE = new AsciiString("blocksize");
    /**
     * {@code "cache-control"}
     */
    public static final AsciiString CACHE_CONTROL = HttpHeaderNames.CACHE_CONTROL;
    /**
     * {@code "conference"}
     */
    public static final AsciiString CONFERENCE = new AsciiString("conference");
    /**
     * {@code "connection"}
     */
    public static final AsciiString CONNECTION = HttpHeaderNames.CONNECTION;
    /**
     * {@code "content-base"}
     */
    public static final AsciiString CONTENT_BASE = HttpHeaderNames.CONTENT_BASE;
    /**
     * {@code "content-encoding"}
     */
    public static final AsciiString CONTENT_ENCODING = HttpHeaderNames.CONTENT_ENCODING;
    /**
     * {@code "content-language"}
     */
    public static final AsciiString CONTENT_LANGUAGE = HttpHeaderNames.CONTENT_LANGUAGE;
    /**
     * {@code "content-length"}
     */
    public static final AsciiString CONTENT_LENGTH = HttpHeaderNames.CONTENT_LENGTH;
    /**
     * {@code "content-location"}
     */
    public static final AsciiString CONTENT_LOCATION = HttpHeaderNames.CONTENT_LOCATION;
    /**
     * {@code "content-type"}
     */
    public static final AsciiString CONTENT_TYPE = HttpHeaderNames.CONTENT_TYPE;
    /**
     * {@code "cseq"}
     */
    public static final AsciiString CSEQ = new AsciiString("cseq");
    /**
     * {@code "cate"}
     */
    public static final AsciiString DATE = HttpHeaderNames.DATE;
    /**
     * {@code "expires"}
     */
    public static final AsciiString EXPIRES = HttpHeaderNames.EXPIRES;
    /**
     * {@code "from"}
     */
    public static final AsciiString FROM = HttpHeaderNames.FROM;
    /**
     * {@code "host"}
     */
    public static final AsciiString HOST = HttpHeaderNames.HOST;
    /**
     * {@code "if-match"}
     */
    public static final AsciiString IF_MATCH = HttpHeaderNames.IF_MATCH;
    /**
     * {@code "if-modified-since"}
     */
    public static final AsciiString IF_MODIFIED_SINCE = HttpHeaderNames.IF_MODIFIED_SINCE;
    /**
     * {@code "keymgmt"}
     */
    public static final AsciiString KEYMGMT = new AsciiString("keymgmt");
    /**
     * {@code "last-modified"}
     */
    public static final AsciiString LAST_MODIFIED = HttpHeaderNames.LAST_MODIFIED;
    /**
     * {@code "proxy-authenticate"}
     */
    public static final AsciiString PROXY_AUTHENTICATE = HttpHeaderNames.PROXY_AUTHENTICATE;
    /**
     * {@code "proxy-require"}
     */
    public static final AsciiString PROXY_REQUIRE = new AsciiString("proxy-require");
    /**
     * {@code "public"}
     */
    public static final AsciiString PUBLIC = new AsciiString("public");
    /**
     * {@code "range"}
     */
    public static final AsciiString RANGE = HttpHeaderNames.RANGE;
    /**
     * {@code "referer"}
     */
    public static final AsciiString REFERER = HttpHeaderNames.REFERER;
    /**
     * {@code "require"}
     */
    public static final AsciiString REQUIRE = new AsciiString("require");
    /**
     * {@code "retry-after"}
     */
    public static final AsciiString RETRT_AFTER = HttpHeaderNames.RETRY_AFTER;
    /**
     * {@code "rtp-info"}
     */
    public static final AsciiString RTP_INFO = new AsciiString("rtp-info");
    /**
     * {@code "scale"}
     */
    public static final AsciiString SCALE = new AsciiString("scale");
    /**
     * {@code "session"}
     */
    public static final AsciiString SESSION = new AsciiString("session");
    /**
     * {@code "server"}
     */
    public static final AsciiString SERVER = HttpHeaderNames.SERVER;
    /**
     * {@code "speed"}
     */
    public static final AsciiString SPEED = new AsciiString("speed");
    /**
     * {@code "timestamp"}
     */
    public static final AsciiString TIMESTAMP = new AsciiString("timestamp");
    /**
     * {@code "transport"}
     */
    public static final AsciiString TRANSPORT = new AsciiString("transport");
    /**
     * {@code "unsupported"}
     */
    public static final AsciiString UNSUPPORTED = new AsciiString("unsupported");
    /**
     * {@code "user-agent"}
     */
    public static final AsciiString USER_AGENT = HttpHeaderNames.USER_AGENT;
    /**
     * {@code "vary"}
     */
    public static final AsciiString VARY = HttpHeaderNames.VARY;
    /**
     * {@code "via"}
     */
    public static final AsciiString VIA = HttpHeaderNames.VIA;
    /**
     * {@code "www-authenticate"}
     */
    public static final AsciiString WWW_AUTHENTICATE = HttpHeaderNames.WWW_AUTHENTICATE;

    private RtspHeaderNames() { }
}
