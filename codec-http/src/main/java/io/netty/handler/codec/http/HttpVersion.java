/*
 * Copyright 2012 The Netty Project
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

import static io.netty.util.internal.ObjectUtil.checkPositiveOrZero;
import static io.netty.util.internal.ObjectUtil.checkNonEmptyAfterTrim;

import io.netty.buffer.ByteBuf;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.ObjectUtil;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The version of HTTP or its derived protocols, such as
 * <a href="https://en.wikipedia.org/wiki/Real_Time_Streaming_Protocol">RTSP</a> and
 * <a href="https://en.wikipedia.org/wiki/Internet_Content_Adaptation_Protocol">ICAP</a>.
 */
public class HttpVersion implements Comparable<HttpVersion> {

    private static final Pattern VERSION_PATTERN =
        Pattern.compile("(\\S+)/(\\d+)\\.(\\d+)");
    static final String HTTP_1_0_STRING = "HTTP/1.0";
    static final String HTTP_1_1_STRING = "HTTP/1.1";

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
        return valueOf(text, false);
    }

    static HttpVersion valueOf(String text, boolean strict) {
        ObjectUtil.checkNotNull(text, "text");

        // super fast-path
        if (text == HTTP_1_1_STRING) {
            return HTTP_1_1;
        }
        if (text == HTTP_1_0_STRING) {
            return HTTP_1_0;
        }

        text = text.trim();

        if (text.isEmpty()) {
            throw new IllegalArgumentException("text is empty (possibly HTTP/0.9)");
        }

        // Try to match without convert to uppercase first as this is what 99% of all clients
        // will send anyway. Also there is a change to the RFC to make it clear that it is
        // expected to be case-sensitive
        //
        // See:
        // * https://trac.tools.ietf.org/wg/httpbis/trac/ticket/1
        // * https://trac.tools.ietf.org/wg/httpbis/trac/wiki
        //
        HttpVersion version = version0(text);
        if (version == null) {
            version = new HttpVersion(text, strict, true);
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
     * <a href="https://en.wikipedia.org/wiki/Real_Time_Streaming_Protocol">RTSP</a> and
     * <a href="https://en.wikipedia.org/wiki/Internet_Content_Adaptation_Protocol">ICAP</a>.
     *
     * @param keepAliveDefault
     *        {@code true} if and only if the connection is kept alive unless
     *        the {@code "Connection"} header is set to {@code "close"} explicitly.
     */
    public HttpVersion(String text, boolean keepAliveDefault) {
        this(text, false, keepAliveDefault);
    }

    HttpVersion(String text, boolean strict, boolean keepAliveDefault) {
        text = checkNonEmptyAfterTrim(text, "text").toUpperCase();

        if (strict) {
            // Only single digit major / minor version is allowed.
            // See
            //  - https://datatracker.ietf.org/doc/html/rfc7230#section-2.6
            //  - https://datatracker.ietf.org/doc/html/rfc9110#name-protocol-version
            if (text.length() != 8 || !text.startsWith("HTTP/") || text.charAt(6) != '.') {
                throw new IllegalArgumentException("invalid version format: " + text);
            }
            protocolName = "HTTP";
            majorVersion = toDecimal(text.charAt(5));
            minorVersion = toDecimal(text.charAt(7));
        } else {
            Matcher m = VERSION_PATTERN.matcher(text);
            if (!m.matches()) {
                throw new IllegalArgumentException("invalid version format: " + text);
            }

            protocolName = m.group(1);
            majorVersion = Integer.parseInt(m.group(2));
            minorVersion = Integer.parseInt(m.group(3));
        }

        this.text = protocolName + '/' + majorVersion + '.' + minorVersion;
        this.keepAliveDefault = keepAliveDefault;
        bytes = null;
    }

    private static int toDecimal(final int value) {
        if (value < '0' || value > '9') {
            throw new IllegalArgumentException("Invalid version number, only 0-9 (0x30-0x39) allowed," +
                    " but received a '" + (char) value + "' (0x" + Integer.toHexString(value) + ")");
        }
        return value - '0';
    }

    /**
     * Creates a new HTTP version with the specified protocol name and version
     * numbers.  You will not need to create a new instance unless you are
     * implementing a protocol derived from HTTP, such as
     * <a href="https://en.wikipedia.org/wiki/Real_Time_Streaming_Protocol">RTSP</a> and
     * <a href="https://en.wikipedia.org/wiki/Internet_Content_Adaptation_Protocol">ICAP</a>
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
        protocolName = checkNonEmptyAfterTrim(protocolName, "protocolName").toUpperCase();

        for (int i = 0; i < protocolName.length(); i ++) {
            if (Character.isISOControl(protocolName.charAt(i)) ||
                    Character.isWhitespace(protocolName.charAt(i))) {
                throw new IllegalArgumentException("invalid character in protocolName");
            }
        }

        checkPositiveOrZero(majorVersion, "majorVersion");
        checkPositiveOrZero(minorVersion, "minorVersion");

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
            buf.writeCharSequence(text, CharsetUtil.US_ASCII);
        } else {
            buf.writeBytes(bytes);
        }
    }
}
