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

import io.netty.buffer.ByteBuf;
import io.netty.util.CharsetUtil;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The version of HTTP or its derived protocols, such as
 * <a href="http://en.wikipedia.org/wiki/Real_Time_Streaming_Protocol">RTSP</a> and
 * <a href="http://en.wikipedia.org/wiki/Internet_Content_Adaptation_Protocol">ICAP</a>.
 */
public class HttpVersion implements Comparable<HttpVersion> {

    private static final Pattern VERSION_PATTERN =
        Pattern.compile("(\\S+)/(\\d+)\\.(\\d+)");

    private static final String HTTP_1_0_STRING = "HTTP/1.0";
    private static final String HTTP_1_1_STRING = "HTTP/1.1";

    /**
     * HTTP/1.0
     */
    public static final HttpVersion HTTP_1_0 = new HttpVersion("HTTP", 1, 0, false, true);

    /**
     * HTTP/1.1
     */
    public static final HttpVersion HTTP_1_1 = new HttpVersion("HTTP", 1, 1, true, true);

    /**
     * Returns an existing or new {@link HttpVersion} instance which matches to
     * the specified protocol version string.  If the specified {@code text} is
     * equal to {@code "HTTP/1.0"}, {@link #HTTP_1_0} will be returned.  If the
     * specified {@code text} is equal to {@code "HTTP/1.1"}, {@link #HTTP_1_1}
     * will be returned.  Otherwise, a new {@link HttpVersion} instance will be
     * returned.
     */
    public static HttpVersion valueOf(String text) {
        if (text == null) {
            throw new NullPointerException("text");
        }

        text = text.trim();

        if (text.isEmpty()) {
            throw new IllegalArgumentException("text is empty");
        }

        // Try to match without convert to uppercase first as this is what 99% of all clients
        // will send anyway. Also there is a change to the RFC to make it clear that it is
        // expected to be case-sensitive
        //
        // See:
        // * http://trac.tools.ietf.org/wg/httpbis/trac/ticket/1
        // * http://trac.tools.ietf.org/wg/httpbis/trac/wiki
        //
        HttpVersion version = version0(text);
        if (version == null) {
            version = new HttpVersion(text, true);
        }
        return version;
    }

    private static HttpVersion version0(String text) {
        if (HTTP_1_1_STRING.equals(text)) {
            return HTTP_1_1;
        }
        if (HTTP_1_0_STRING.equals(text)) {
            return HTTP_1_0;
        }
        return null;
    }

    private final String protocolName;
    private final int majorVersion;
    private final int minorVersion;
    private final String text;
    private final boolean keepAliveDefault;
    private final byte[] bytes;

    /**
     * Creates a new HTTP version with the specified version string.  You will
     * not need to create a new instance unless you are implementing a protocol
     * derived from HTTP, such as
     * <a href="http://en.wikipedia.org/wiki/Real_Time_Streaming_Protocol">RTSP</a> and
     * <a href="http://en.wikipedia.org/wiki/Internet_Content_Adaptation_Protocol">ICAP</a>.
     *
     * @param keepAliveDefault
     *        {@code true} if and only if the connection is kept alive unless
     *        the {@code "Connection"} header is set to {@code "close"} explicitly.
     */
    public HttpVersion(String text, boolean keepAliveDefault) {
        if (text == null) {
            throw new NullPointerException("text");
        }

        text = text.trim().toUpperCase();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("empty text");
        }

        Matcher m = VERSION_PATTERN.matcher(text);
        if (!m.matches()) {
            throw new IllegalArgumentException("invalid version format: " + text);
        }

        protocolName = m.group(1);
        majorVersion = Integer.parseInt(m.group(2));
        minorVersion = Integer.parseInt(m.group(3));
        this.text = protocolName + '/' + majorVersion + '.' + minorVersion;
        this.keepAliveDefault = keepAliveDefault;
        bytes = null;
    }

    /**
     * Creates a new HTTP version with the specified protocol name and version
     * numbers.  You will not need to create a new instance unless you are
     * implementing a protocol derived from HTTP, such as
     * <a href="http://en.wikipedia.org/wiki/Real_Time_Streaming_Protocol">RTSP</a> and
     * <a href="http://en.wikipedia.org/wiki/Internet_Content_Adaptation_Protocol">ICAP</a>
     *
     * @param keepAliveDefault
     *        {@code true} if and only if the connection is kept alive unless
     *        the {@code "Connection"} header is set to {@code "close"} explicitly.
     */
    public HttpVersion(
            String protocolName, int majorVersion, int minorVersion,
            boolean keepAliveDefault) {
        this(protocolName, majorVersion, minorVersion, keepAliveDefault, false);
    }

    private HttpVersion(
            String protocolName, int majorVersion, int minorVersion,
            boolean keepAliveDefault, boolean bytes) {
        if (protocolName == null) {
            throw new NullPointerException("protocolName");
        }

        protocolName = protocolName.trim().toUpperCase();
        if (protocolName.isEmpty()) {
            throw new IllegalArgumentException("empty protocolName");
        }

        for (int i = 0; i < protocolName.length(); i ++) {
            if (Character.isISOControl(protocolName.charAt(i)) ||
                    Character.isWhitespace(protocolName.charAt(i))) {
                throw new IllegalArgumentException("invalid character in protocolName");
            }
        }

        if (majorVersion < 0) {
            throw new IllegalArgumentException("negative majorVersion");
        }
        if (minorVersion < 0) {
            throw new IllegalArgumentException("negative minorVersion");
        }

        this.protocolName = protocolName;
        this.majorVersion = majorVersion;
        this.minorVersion = minorVersion;
        text = protocolName + '/' + majorVersion + '.' + minorVersion;
        this.keepAliveDefault = keepAliveDefault;

        if (bytes) {
            this.bytes = text.getBytes(CharsetUtil.US_ASCII);
        } else {
            this.bytes = null;
        }
    }

    /**
     * Returns the name of the protocol such as {@code "HTTP"} in {@code "HTTP/1.0"}.
     */
    public String protocolName() {
        return protocolName;
    }

    /**
     * Returns the name of the protocol such as {@code 1} in {@code "HTTP/1.0"}.
     */
    public int majorVersion() {
        return majorVersion;
    }

    /**
     * Returns the name of the protocol such as {@code 0} in {@code "HTTP/1.0"}.
     */
    public int minorVersion() {
        return minorVersion;
    }

    /**
     * Returns the full protocol version text such as {@code "HTTP/1.0"}.
     */
    public String text() {
        return text;
    }

    /**
     * Returns {@code true} if and only if the connection is kept alive unless
     * the {@code "Connection"} header is set to {@code "close"} explicitly.
     */
    public boolean isKeepAliveDefault() {
        return keepAliveDefault;
    }

    /**
     * Returns the full protocol version text such as {@code "HTTP/1.0"}.
     */
    @Override
    public String toString() {
        return text();
    }

    @Override
    public int hashCode() {
        return (protocolName().hashCode() * 31 + majorVersion()) * 31 +
               minorVersion();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof HttpVersion)) {
            return false;
        }

        HttpVersion that = (HttpVersion) o;
        return minorVersion() == that.minorVersion() &&
               majorVersion() == that.majorVersion() &&
               protocolName().equals(that.protocolName());
    }

    @Override
    public int compareTo(HttpVersion o) {
        int v = protocolName().compareTo(o.protocolName());
        if (v != 0) {
            return v;
        }

        v = majorVersion() - o.majorVersion();
        if (v != 0) {
            return v;
        }

        return minorVersion() - o.minorVersion();
    }

    void encode(ByteBuf buf) {
        if (bytes == null) {
            HttpUtil.encodeAscii0(text, buf);
        } else {
            buf.writeBytes(bytes);
        }
    }
}
