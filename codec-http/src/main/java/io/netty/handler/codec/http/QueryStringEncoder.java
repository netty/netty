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

import io.netty.buffer.ByteBufUtil;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.StringUtil;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.Charset;

/**
 * Creates a URL-encoded URI from a path string and key-value parameter pairs.
 * This encoder is for one time use only.  Create a new instance for each URI.
 *
 * <pre>
 * {@link QueryStringEncoder} encoder = new {@link QueryStringEncoder}("/hello");
 * encoder.addParam("recipient", "world");
 * assert encoder.toString().equals("/hello?recipient=world");
 * </pre>
 *
 * @see QueryStringDecoder
 */
public class QueryStringEncoder {

    private final Charset charset;
    private final StringBuilder uriBuilder;
    private boolean hasParams;
    private static final byte WRITE_UTF_UNKNOWN = (byte) '?';
    private static final char[] CHAR_MAP = "0123456789ABCDEF".toCharArray();

    /**
     * Creates a new encoder that encodes a URI that starts with the specified
     * path string.  The encoder will encode the URI in UTF-8.
     */
    public QueryStringEncoder(String uri) {
        this(uri, HttpConstants.DEFAULT_CHARSET);
    }

    /**
     * Creates a new encoder that encodes a URI that starts with the specified
     * path string in the specified charset.
     */
    public QueryStringEncoder(String uri, Charset charset) {
        ObjectUtil.checkNotNull(charset, "charset");
        uriBuilder = new StringBuilder(uri);
        this.charset = CharsetUtil.UTF_8.equals(charset) ? null : charset;
    }

    /**
     * Adds a parameter with the specified name and value to this encoder.
     */
    public void addParam(String name, String value) {
        ObjectUtil.checkNotNull(name, "name");
        if (hasParams) {
            uriBuilder.append('&');
        } else {
            uriBuilder.append('?');
            hasParams = true;
        }

        encodeComponent(name);
        if (value != null) {
            uriBuilder.append('=');
            encodeComponent(value);
        }
    }

    private void encodeComponent(CharSequence s) {
        if (charset == null) {
            encodeUtf8Component(s);
        } else {
            encodeNonUtf8Component(s);
        }
    }

    /**
     * Returns the URL-encoded URI object which was created from the path string
     * specified in the constructor and the parameters added by
     * {@link #addParam(String, String)} method.
     */
    public URI toUri() throws URISyntaxException {
        return new URI(toString());
    }

    /**
     * Returns the URL-encoded URI which was created from the path string
     * specified in the constructor and the parameters added by
     * {@link #addParam(String, String)} method.
     */
    @Override
    public String toString() {
        return uriBuilder.toString();
    }

    /**
     * Encode the String as per RFC 3986, Section 2.
     * <p>
     * There is a little different between the JDK's encode method : {@link URLEncoder#encode(String, String)}.
     * The JDK's encoder encode the space to {@code +} and this method directly encode the blank to {@code %20}
     * beyond that , this method reuse the {@link #uriBuilder} in this class rather then create a new one,
     * thus generates less garbage for the GC.
     *
     * @param s The String to encode
     */
    private void encodeNonUtf8Component(CharSequence s) {
        //Don't allocate memory until needed
        char[] buf = null;

        for (int i = 0, len = s.length(); i < len;) {
            char c = s.charAt(i);
            if (dontNeedEncoding(c)) {
                uriBuilder.append(c);
                i++;
            } else {
                int index = 0;
                if (buf == null) {
                    buf = new char[s.length() - i];
                }

                do {
                    buf[index] = c;
                    index++;
                    i++;
                } while (i < s.length() && !dontNeedEncoding(c = s.charAt(i)));

                byte[] bytes = new String(buf, 0, index).getBytes(charset);

                for (byte b : bytes) {
                    appendEncoded(b);
                }
            }
        }
    }

    /**
     * @see ByteBufUtil#writeUtf8(io.netty.buffer.ByteBuf, CharSequence, int, int)
     */
    private void encodeUtf8Component(CharSequence s) {
        for (int i = 0, len = s.length(); i < len; i++) {
            char c = s.charAt(i);
            if (!dontNeedEncoding(c)) {
                encodeUtf8Component(s, i, len);
                return;
            }
        }
        uriBuilder.append(s);
    }

    private void encodeUtf8Component(CharSequence s, int encodingStart, int len) {
        if (encodingStart > 0) {
            // Append non-encoded characters directly first.
            uriBuilder.append(s, 0, encodingStart);
        }
        encodeUtf8ComponentSlow(s, encodingStart, len);
    }

    private void encodeUtf8ComponentSlow(CharSequence s, int start, int len) {
        for (int i = start; i < len; i++) {
            char c = s.charAt(i);
            if (c < 0x80) {
                if (dontNeedEncoding(c)) {
                    uriBuilder.append(c);
                } else {
                    appendEncoded(c);
                }
            } else if (c < 0x800) {
                appendEncoded(0xc0 | (c >> 6));
                appendEncoded(0x80 | (c & 0x3f));
            } else if (StringUtil.isSurrogate(c)) {
                if (!Character.isHighSurrogate(c)) {
                    appendEncoded(WRITE_UTF_UNKNOWN);
                    continue;
                }
                // Surrogate Pair consumes 2 characters.
                if (++i == s.length()) {
                    appendEncoded(WRITE_UTF_UNKNOWN);
                    break;
                }
                // Extra method to allow inlining the rest of writeUtf8 which is the most likely code path.
                writeUtf8Surrogate(c, s.charAt(i));
            } else {
                appendEncoded(0xe0 | (c >> 12));
                appendEncoded(0x80 | ((c >> 6) & 0x3f));
                appendEncoded(0x80 | (c & 0x3f));
            }
        }
    }

    private void writeUtf8Surrogate(char c, char c2) {
        if (!Character.isLowSurrogate(c2)) {
            appendEncoded(WRITE_UTF_UNKNOWN);
            appendEncoded(Character.isHighSurrogate(c2) ? WRITE_UTF_UNKNOWN : c2);
            return;
        }
        int codePoint = Character.toCodePoint(c, c2);
        // See https://www.unicode.org/versions/Unicode7.0.0/ch03.pdf#G2630.
        appendEncoded(0xf0 | (codePoint >> 18));
        appendEncoded(0x80 | ((codePoint >> 12) & 0x3f));
        appendEncoded(0x80 | ((codePoint >> 6) & 0x3f));
        appendEncoded(0x80 | (codePoint & 0x3f));
    }

    private void appendEncoded(int b) {
        uriBuilder.append('%').append(forDigit(b >> 4)).append(forDigit(b));
    }

    /**
     * Convert the given digit to a upper hexadecimal char.
     *
     * @param digit the number to convert to a character.
     * @return the {@code char} representation of the specified digit
     * in hexadecimal.
     */
    private static char forDigit(int digit) {
        return CHAR_MAP[digit & 0xF];
    }

    /**
     * Determines whether the given character is a unreserved character.
     * <p>
     * unreserved characters do not need to be encoded, and include uppercase and lowercase
     * letters, decimal digits, hyphen, period, underscore, and tilde.
     * <p>
     * unreserved  = ALPHA / DIGIT / "-" / "." / "_" / "~" / "*"
     *
     * @param ch the char to be judged whether it need to be encode
     * @return true or false
     */
    private static boolean dontNeedEncoding(char ch) {
        return ch >= 'a' && ch <= 'z' || ch >= 'A' && ch <= 'Z' || ch >= '0' && ch <= '9'
                || ch == '-' || ch == '_' || ch == '.' || ch == '*' || ch == '~';
    }
}
