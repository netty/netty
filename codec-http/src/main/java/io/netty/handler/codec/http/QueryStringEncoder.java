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

import io.netty.util.internal.ObjectUtil;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.BitSet;

/**
 * Creates an URL-encoded URI from a path string and key-value parameter pairs.
 * This encoder is for one time use only.  Create a new instance for each URI.
 *
 * <pre>
 * {@link QueryStringEncoder} encoder = new {@link QueryStringEncoder}("/hello");
 * encoder.addParam("recipient", "world");
 * assert encoder.toString().equals("/hello?recipient=world");
 * </pre>
 * @see QueryStringDecoder
 */
public class QueryStringEncoder {

    private final Charset charset;
    private final StringBuilder uriBuilder;
    private boolean hasParams;
    private static BitSet dontNeedEncoding;
    private static final int CASE_DIFF = 'a' - 'A';

    static {
        dontNeedEncoding = new BitSet(256);
        int i;
        for (i = 'a'; i <= 'z'; i++) {
            dontNeedEncoding.set(i);
        }
        for (i = 'A'; i <= 'Z'; i++) {
            dontNeedEncoding.set(i);
        }
        for (i = '0'; i <= '9'; i++) {
            dontNeedEncoding.set(i);
        }

        dontNeedEncoding.set('-');
        dontNeedEncoding.set('_');
        dontNeedEncoding.set('.');
        dontNeedEncoding.set('*');
    }

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
        uriBuilder = new StringBuilder(uri);
        this.charset = charset;
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
     * encode the String as per RFC 3986, Section 2.
     * <p>
     * There is a little different between the JDK's encode method : {@link URLEncoder#encode(String, String)}.
     * The JDK's encoder encode the space to {@code +} and this method directly encode the blank to {@code %20}
     * beyond that , this method reuse the {@link #uriBuilder} in this class rather then create a new one,
     * thus generates less garbage for the GC.
     *
     * @param s The String to encode
     */
    private void encodeComponent(String s) {
        //allocate memory until needed
        char[] buf = null;

        for (int i = 0; i < s.length();) {
            int c = s.charAt(i);
            if (dontNeedEncoding.get(c)) {
                uriBuilder.append((char) c);
                i++;
            } else {
                int index = 0;
                if (buf == null) {
                    buf = new char[s.length() - i];
                }

                do {
                    buf[index] = (char) c;
                    index++;
                    i++;
                } while (i < s.length() && !dontNeedEncoding.get(c = s.charAt(i)));

                byte[] bytes = new String(buf, 0, index).getBytes(charset);

                for (byte b : bytes) {
                    uriBuilder.append('%');

                    char ch = Character.forDigit((b >> 4) & 0xF, 16);
                    if (ch >= 'a' && ch <= 'f') {
                        ch -= CASE_DIFF;
                    }
                    uriBuilder.append(ch);

                    ch = Character.forDigit(b & 0xF, 16);
                    if (ch >= 'a' && ch <= 'f') {
                        ch -= CASE_DIFF;
                    }
                    uriBuilder.append(ch);
                }
            }
        }
    }
}
