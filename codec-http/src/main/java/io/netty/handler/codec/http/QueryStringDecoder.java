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

import io.netty.util.CharsetUtil;
import io.netty.util.internal.PlatformDependent;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.netty.util.internal.ObjectUtil.checkNotNull;
import static io.netty.util.internal.ObjectUtil.checkPositive;
import static io.netty.util.internal.StringUtil.EMPTY_STRING;
import static io.netty.util.internal.StringUtil.SPACE;
import static io.netty.util.internal.StringUtil.decodeHexByte;

/**
 * Splits an HTTP query string into a path string and key-value parameter pairs.
 * This decoder is for one time use only.  Create a new instance for each URI:
 * <pre>
 * {@link QueryStringDecoder} decoder = new {@link QueryStringDecoder}("/hello?recipient=world&x=1;y=2");
 * assert decoder.path().equals("/hello");
 * assert decoder.parameters().get("recipient").get(0).equals("world");
 * assert decoder.parameters().get("x").get(0).equals("1");
 * assert decoder.parameters().get("y").get(0).equals("2");
 * </pre>
 *
 * This decoder can also decode the content of an HTTP POST request whose
 * content type is <tt>application/x-www-form-urlencoded</tt>:
 * <pre>
 * {@link QueryStringDecoder} decoder = new {@link QueryStringDecoder}("recipient=world&x=1;y=2", false);
 * ...
 * </pre>
 *
 * <h3>HashDOS vulnerability fix</h3>
 *
 * As a workaround to the <a href="https://netty.io/s/hashdos">HashDOS</a> vulnerability, the decoder
 * limits the maximum number of decoded key-value parameter pairs, up to {@literal 1024} by
 * default, and you can configure it when you construct the decoder by passing an additional
 * integer parameter.
 *
 * @see QueryStringEncoder
 */
public class QueryStringDecoder {

    private static final int DEFAULT_MAX_PARAMS = 1024;

    private final Charset charset;
    private final String uri;
    private final int maxParams;
    private final boolean semicolonIsNormalChar;
    private final boolean htmlQueryDecoding;
    private int pathEndIdx;
    private String path;
    private Map<String, List<String>> params;

    /**
     * Creates a new decoder that decodes the specified URI. The decoder will
     * assume that the query string is encoded in UTF-8.
     */
    public QueryStringDecoder(String uri) {
        this(builder(), uri);
    }

    /**
     * Creates a new decoder that decodes the specified URI encoded in the
     * specified charset.
     */
    public QueryStringDecoder(String uri, boolean hasPath) {
        this(builder().hasPath(hasPath), uri);
    }

    /**
     * Creates a new decoder that decodes the specified URI encoded in the
     * specified charset.
     */
    public QueryStringDecoder(String uri, Charset charset) {
        this(builder().charset(charset), uri);
    }

    /**
     * Creates a new decoder that decodes the specified URI encoded in the
     * specified charset.
     */
    public QueryStringDecoder(String uri, Charset charset, boolean hasPath) {
        this(builder().hasPath(hasPath).charset(charset), uri);
    }

    /**
     * Creates a new decoder that decodes the specified URI encoded in the
     * specified charset.
     */
    public QueryStringDecoder(String uri, Charset charset, boolean hasPath, int maxParams) {
        this(builder().hasPath(hasPath).charset(charset).maxParams(maxParams), uri);
    }

    /**
     * Creates a new decoder that decodes the specified URI encoded in the
     * specified charset.
     */
    public QueryStringDecoder(String uri, Charset charset, boolean hasPath,
                              int maxParams, boolean semicolonIsNormalChar) {
        this(
                builder()
                        .hasPath(hasPath)
                        .charset(charset)
                        .maxParams(maxParams)
                        .semicolonIsNormalChar(semicolonIsNormalChar),
                uri);
    }

    /**
     * Creates a new decoder that decodes the specified URI. The decoder will
     * assume that the query string is encoded in UTF-8.
     */
    public QueryStringDecoder(URI uri) {
        this(builder(), uri);
    }

    /**
     * Creates a new decoder that decodes the specified URI encoded in the
     * specified charset.
     */
    public QueryStringDecoder(URI uri, Charset charset) {
        this(builder().charset(charset), uri);
    }

    /**
     * Creates a new decoder that decodes the specified URI encoded in the
     * specified charset.
     */
    public QueryStringDecoder(URI uri, Charset charset, int maxParams) {
        this(builder().charset(charset).maxParams(maxParams), uri);
    }

    /**
     * Creates a new decoder that decodes the specified URI encoded in the
     * specified charset.
     */
    public QueryStringDecoder(URI uri, Charset charset, int maxParams, boolean semicolonIsNormalChar) {
        this(builder().charset(charset).maxParams(maxParams).semicolonIsNormalChar(semicolonIsNormalChar), uri);
    }

    private QueryStringDecoder(Builder builder, String uri) {
        this.uri = checkNotNull(uri, "uri");
        this.charset = checkNotNull(builder.charset, "charset");
        this.maxParams = checkPositive(builder.maxParams, "maxParams");
        this.semicolonIsNormalChar = builder.semicolonIsNormalChar;
        this.htmlQueryDecoding = builder.htmlQueryDecoding;

        // `-1` means that path end index will be initialized lazily
        pathEndIdx = builder.hasPath ? -1 : 0;
    }

    private QueryStringDecoder(Builder builder, URI uri) {
        String rawPath = uri.getRawPath();
        if (rawPath == null) {
            rawPath = EMPTY_STRING;
        }
        String rawQuery = uri.getRawQuery();
        // Also take care of cut of things like "http://localhost"
        this.uri = rawQuery == null? rawPath : rawPath + '?' + rawQuery;
        this.charset = checkNotNull(builder.charset, "charset");
        this.maxParams = checkPositive(builder.maxParams, "maxParams");
        this.semicolonIsNormalChar = builder.semicolonIsNormalChar;
        this.htmlQueryDecoding = builder.htmlQueryDecoding;
        pathEndIdx = rawPath.length();
    }

    @Override
    public String toString() {
        return uri();
    }

    /**
     * Returns the uri used to initialize this {@link QueryStringDecoder}.
     */
    public String uri() {
        return uri;
    }

    /**
     * Returns the decoded path string of the URI.
     */
    public String path() {
        if (path == null) {
            path = decodeComponent(uri, 0, pathEndIdx(), charset, false);
        }
        return path;
    }

    /**
     * Returns the decoded key-value parameter pairs of the URI.
     */
    public Map<String, List<String>> parameters() {
        if (params == null) {
            params = decodeParams(uri, pathEndIdx(), charset, maxParams);
        }
        return params;
    }

    /**
     * Returns the raw path string of the URI.
     */
    public String rawPath() {
        return uri.substring(0, pathEndIdx());
    }

    /**
     * Returns raw query string of the URI.
     */
    public String rawQuery() {
        int start = pathEndIdx() + 1;
        return start < uri.length() ? uri.substring(start) : EMPTY_STRING;
    }

    private int pathEndIdx() {
        if (pathEndIdx == -1) {
            pathEndIdx = findPathEndIndex(uri);
        }
        return pathEndIdx;
    }

    private Map<String, List<String>> decodeParams(String s, int from, Charset charset, int paramsLimit) {
        int len = s.length();
        if (from >= len) {
            return Collections.emptyMap();
        }
        if (s.charAt(from) == '?') {
            from++;
        }
        Map<String, List<String>> params = new LinkedHashMap<String, List<String>>();
        int nameStart = from;
        int valueStart = -1;
        int i;
        loop:
        for (i = from; i < len; i++) {
            switch (s.charAt(i)) {
            case '=':
                if (nameStart == i) {
                    nameStart = i + 1;
                } else if (valueStart < nameStart) {
                    valueStart = i + 1;
                }
                break;
            case ';':
                if (semicolonIsNormalChar) {
                    continue;
                }
                // fall-through
            case '&':
                if (addParam(s, nameStart, valueStart, i, params, charset)) {
                    paramsLimit--;
                    if (paramsLimit == 0) {
                        return params;
                    }
                }
                nameStart = i + 1;
                break;
            case '#':
                break loop;
            default:
                // continue
            }
        }
        addParam(s, nameStart, valueStart, i, params, charset);
        return params;
    }

    private boolean addParam(String s, int nameStart, int valueStart, int valueEnd,
                                    Map<String, List<String>> params, Charset charset) {
        if (nameStart >= valueEnd) {
            return false;
        }
        if (valueStart <= nameStart) {
            valueStart = valueEnd + 1;
        }
        String name = decodeComponent(s, nameStart, valueStart - 1, charset, htmlQueryDecoding);
        String value = decodeComponent(s, valueStart, valueEnd, charset, htmlQueryDecoding);
        List<String> values = params.get(name);
        if (values == null) {
            values = new ArrayList<String>(1);  // Often there's only 1 value.
            params.put(name, values);
        }
        values.add(value);
        return true;
    }

    /**
     * Decodes a bit of a URL encoded by a browser.
     * <p>
     * This is equivalent to calling {@link #decodeComponent(String, Charset)}
     * with the UTF-8 charset (recommended to comply with RFC 3986, Section 2).
     * @param s The string to decode (can be empty).
     * @return The decoded string, or {@code s} if there's nothing to decode.
     * If the string to decode is {@code null}, returns an empty string.
     * @throws IllegalArgumentException if the string contains a malformed
     * escape sequence.
     */
    public static String decodeComponent(final String s) {
        return decodeComponent(s, HttpConstants.DEFAULT_CHARSET);
    }

    /**
     * Decodes a bit of a URL encoded by a browser.
     * <p>
     * The string is expected to be encoded as per RFC 3986, Section 2.
     * This is the encoding used by JavaScript functions {@code encodeURI}
     * and {@code encodeURIComponent}, but not {@code escape}.  For example
     * in this encoding, &eacute; (in Unicode {@code U+00E9} or in UTF-8
     * {@code 0xC3 0xA9}) is encoded as {@code %C3%A9} or {@code %c3%a9}.
     * <p>
     * This is essentially equivalent to calling
     *   {@link URLDecoder#decode(String, String)}
     * except that it's over 2x faster and generates less garbage for the GC.
     * Actually this function doesn't allocate any memory if there's nothing
     * to decode, the argument itself is returned.
     * @param s The string to decode (can be empty).
     * @param charset The charset to use to decode the string (should really
     * be {@link CharsetUtil#UTF_8}.
     * @return The decoded string, or {@code s} if there's nothing to decode.
     * If the string to decode is {@code null}, returns an empty string.
     * @throws IllegalArgumentException if the string contains a malformed
     * escape sequence.
     */
    public static String decodeComponent(final String s, final Charset charset) {
        if (s == null) {
            return EMPTY_STRING;
        }
        return decodeComponent(s, 0, s.length(), charset, true);
    }

    private static String decodeComponent(String s, int from, int toExcluded, Charset charset, boolean plusToSpace) {
        int len = toExcluded - from;
        if (len <= 0) {
            return EMPTY_STRING;
        }
        int firstEscaped = -1;
        for (int i = from; i < toExcluded; i++) {
            char c = s.charAt(i);
            if (c == '%' || (c == '+' && plusToSpace)) {
                firstEscaped = i;
                break;
            }
        }
        if (firstEscaped == -1) {
            return s.substring(from, toExcluded);
        }

        // Each encoded byte takes 3 characters (e.g. "%20")
        int decodedCapacity = (toExcluded - firstEscaped) / 3;
        byte[] buf = PlatformDependent.allocateUninitializedArray(decodedCapacity);
        int bufIdx;

        StringBuilder strBuf = new StringBuilder(len);
        strBuf.append(s, from, firstEscaped);

        for (int i = firstEscaped; i < toExcluded; i++) {
            char c = s.charAt(i);
            if (c != '%') {
                strBuf.append(c != '+' || !plusToSpace ? c : SPACE);
                continue;
            }

            bufIdx = 0;
            do {
                if (i + 3 > toExcluded) {
                    throw new IllegalArgumentException("unterminated escape sequence at index " + i + " of: " + s);
                }
                buf[bufIdx++] = decodeHexByte(s, i + 1);
                i += 3;
            } while (i < toExcluded && s.charAt(i) == '%');
            i--;

            strBuf.append(new String(buf, 0, bufIdx, charset));
        }
        return strBuf.toString();
    }

    private static int findPathEndIndex(String uri) {
        int len = uri.length();
        for (int i = 0; i < len; i++) {
            char c = uri.charAt(i);
            if (c == '?' || c == '#') {
                return i;
            }
        }
        return len;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private boolean hasPath = true;
        private int maxParams = DEFAULT_MAX_PARAMS;
        private boolean semicolonIsNormalChar;
        private Charset charset = HttpConstants.DEFAULT_CHARSET;
        private boolean htmlQueryDecoding = true;

        private Builder() {
        }

        /**
         * {@code true} by default. When set to {@code false}, the input string only contains the query component of
         * the URI.
         *
         * @param hasPath Whether the URI contains a path
         * @return This builder
         */
        public Builder hasPath(boolean hasPath) {
            this.hasPath = hasPath;
            return this;
        }

        /**
         * Maximum number of query parameters allowed, to mitigate HashDOS. {@value DEFAULT_MAX_PARAMS} by default.
         *
         * @param maxParams The maximum number of query parameters
         * @return This builder
         */
        public Builder maxParams(int maxParams) {
            this.maxParams = maxParams;
            return this;
        }

        /**
         * {@code false} by default. If set to {@code true}, instead of allowing query parameters to be separated by
         * semicolons, treat the semicolon as a normal character in a query value.
         *
         * @param semicolonIsNormalChar Whether to treat semicolons as a normal character
         * @return This builder
         */
        public Builder semicolonIsNormalChar(boolean semicolonIsNormalChar) {
            this.semicolonIsNormalChar = semicolonIsNormalChar;
            return this;
        }

        /**
         * The charset to use for decoding percent escape sequences. {@link HttpConstants#DEFAULT_CHARSET} by default.
         *
         * @param charset The charset
         * @return This builder
         */
        public Builder charset(Charset charset) {
            this.charset = charset;
            return this;
        }

        /**
         * RFC 3986 (the URI standard) makes no mention of using '+' to encode a space in a URI query component. The
         * whatwg HTML standard, however, defines the query to be encoded with the
         * {@code application/x-www-form-urlencoded} serializer defined in the whatwg URL standard, which does use '+'
         * to encode a space instead of {@code %20}.
         * <p>This flag controls whether the decoding should happen according to HTML rules, which decodes the '+' to a
         * space. The default is {@code true}.
         *
         * @param htmlQueryDecoding Whether to decode '+' to space
         * @return This builder
         */
        public Builder htmlQueryDecoding(boolean htmlQueryDecoding) {
            this.htmlQueryDecoding = htmlQueryDecoding;
            return this;
        }

        /**
         * Create a decoder that will lazily decode the given URI with the settings configured in this builder.
         *
         * @param uri The URI in String form
         * @return The decoder
         */
        public QueryStringDecoder build(String uri) {
            return new QueryStringDecoder(this, uri);
        }

        /**
         * Create a decoder that will lazily decode the given URI with the settings configured in this builder. Note
         * that {@link #hasPath(boolean)} has no effect when using this method.
         *
         * @param uri The already parsed URI
         * @return The decoder
         */
        public QueryStringDecoder build(URI uri) {
            return new QueryStringDecoder(this, uri);
        }
    }
}
