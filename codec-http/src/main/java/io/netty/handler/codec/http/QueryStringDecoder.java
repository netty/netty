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
import io.netty.util.internal.UnstableApi;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

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
    private int pathEndIdx;
    private String path;
    private Map<String, List<String>> params;

    /**
     * Creates a new decoder that decodes the specified URI. The decoder will
     * assume that the query string is encoded in UTF-8.
     */
    public QueryStringDecoder(String uri) {
        this(uri, HttpConstants.DEFAULT_CHARSET);
    }

    /**
     * Creates a new decoder that decodes the specified URI encoded in the
     * specified charset.
     */
    public QueryStringDecoder(String uri, boolean hasPath) {
        this(uri, HttpConstants.DEFAULT_CHARSET, hasPath);
    }

    /**
     * Creates a new decoder that decodes the specified URI encoded in the
     * specified charset.
     */
    public QueryStringDecoder(String uri, Charset charset) {
        this(uri, charset, true);
    }

    /**
     * Creates a new decoder that decodes the specified URI encoded in the
     * specified charset.
     */
    public QueryStringDecoder(String uri, Charset charset, boolean hasPath) {
        this(uri, charset, hasPath, DEFAULT_MAX_PARAMS);
    }

    /**
     * Creates a new decoder that decodes the specified URI encoded in the
     * specified charset.
     */
    public QueryStringDecoder(String uri, Charset charset, boolean hasPath, int maxParams) {
        this(uri, charset, hasPath, maxParams, false);
    }

    /**
     * Creates a new decoder that decodes the specified URI encoded in the
     * specified charset.
     */
    public QueryStringDecoder(String uri, Charset charset, boolean hasPath,
                              int maxParams, boolean semicolonIsNormalChar) {
        this.uri = checkNotNull(uri, "uri");
        this.charset = checkNotNull(charset, "charset");
        this.maxParams = checkPositive(maxParams, "maxParams");
        this.semicolonIsNormalChar = semicolonIsNormalChar;

        // `-1` means that path end index will be initialized lazily
        pathEndIdx = hasPath ? -1 : 0;
    }

    /**
     * Creates a new decoder that decodes the specified URI. The decoder will
     * assume that the query string is encoded in UTF-8.
     */
    public QueryStringDecoder(URI uri) {
        this(uri, HttpConstants.DEFAULT_CHARSET);
    }

    /**
     * Creates a new decoder that decodes the specified URI encoded in the
     * specified charset.
     */
    public QueryStringDecoder(URI uri, Charset charset) {
        this(uri, charset, DEFAULT_MAX_PARAMS);
    }

    /**
     * Creates a new decoder that decodes the specified URI encoded in the
     * specified charset.
     */
    public QueryStringDecoder(URI uri, Charset charset, int maxParams) {
        this(uri, charset, maxParams, false);
    }

    /**
     * Creates a new decoder that decodes the specified URI encoded in the
     * specified charset.
     */
    public QueryStringDecoder(URI uri, Charset charset, int maxParams, boolean semicolonIsNormalChar) {
        // Also take care of cut of things like "http://localhost"
        this.uri = parsableUri(uri);
        this.charset = checkNotNull(charset, "charset");
        this.maxParams = checkPositive(maxParams, "maxParams");
        this.semicolonIsNormalChar = semicolonIsNormalChar;
        pathEndIdx = pathEndIdx(uri);
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
            path = decodeComponent(uri, 0, pathEndIdx(), charset, true);
        }
        return path;
    }

    @UnstableApi
    final boolean hasDecodedParameters() {
        return params != null;
    }

    /**
     * Returns the decoded key-value parameter pairs of the URI.
     */
    public Map<String, List<String>> parameters() {
        if (params == null) {
            params = new LinkedHashMap<String, List<String>>();
            decodeParams(uri, pathEndIdx(), charset, MAP_PARAMETER_COLLECTOR, params, maxParams, semicolonIsNormalChar);
        }
        return params;
    }

    /**
     * Decodes {@link #uri()} parameters, reporting them in the provided {@code collector}'s map.
     */
    public void decodeParameters(Map<String, List<String>> parameters) {
        checkNotNull(parameters, "parameters");
        final Map<String, List<String>> params = this.params;
        if (params != null) {
            if (params.isEmpty()) {
                return;
            }
            parameters.putAll(params);
        } else {
            decodeParams(uri, pathEndIdx(), charset, MAP_PARAMETER_COLLECTOR, parameters, maxParams,
                         semicolonIsNormalChar);
        }
    }

    /**
     * Decodes {@link #uri()} parameters, reporting them in the provided {@link ParameterCollector}.
     */
    public <C> void decodeParameters(ParameterCollector<? super C> collector, C accumulator) {
        checkNotNull(collector, "collector");
        checkNotNull(accumulator, "accumulator");
        final Map<String, List<String>> params = this.params;
        if (params != null) {
            if (params.isEmpty()) {
                return;
            }
            for (Entry<String, List<String>> param : params.entrySet()) {
                final List<String> values = param.getValue();
                for (int i = 0; i < values.size(); i++) {
                    collector.collect(param.getKey(), values.get(i), accumulator);
                }
            }
        } else {
            decodeParams(uri, pathEndIdx(), charset, collector, accumulator, maxParams, semicolonIsNormalChar);
        }
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

    private static String parsableUri(URI uri) {
        String rawPath = uri.getRawPath();
        if (rawPath == null) {
            rawPath = EMPTY_STRING;
        }
        String rawQuery = uri.getRawQuery();
        // Also take care of cut of things like "http://localhost"
        return rawQuery == null? rawPath : rawPath + '?' + rawQuery;
    }

    private static int pathEndIdx(URI uri) {
        return uri.getRawPath().length();
    }

    public static int getDefaultMaxParams() {
        return DEFAULT_MAX_PARAMS;
    }

    /**
     * Decodes the specified URI encoded in the specified charset.
     */
    public static <C> void decodeParams(URI uri, Charset charset, int maxParams, boolean semicolonIsNormalChar,
                                        ParameterCollector<? super C> collector, C accumulator) {
        checkNotNull(uri, "uri");
        checkNotNull(charset, "charset");
        checkPositive(maxParams, "maxParams");
        checkNotNull(collector, "collector");
        checkNotNull(accumulator, "accumulator");
        String parsableUri = parsableUri(uri);
        int pathEndIdx = pathEndIdx(uri);
        decodeParams(parsableUri, pathEndIdx, charset, collector, accumulator, maxParams, semicolonIsNormalChar);
    }

    /**
     * Decodes the specified URI encoded in the specified charset.
     */
    public static <C> void decodeParams(String uri, Charset charset, boolean hasPath, int maxParams,
                                        boolean semicolonIsNormalChar,
                                        ParameterCollector<? super C> collector, C accumulator) {
        checkNotNull(uri, "uri");
        checkNotNull(charset, "charset");
        checkPositive(maxParams, "maxParams");
        checkNotNull(collector, "collector");
        checkNotNull(accumulator, "accumulator");
        int pathEndIdx = hasPath? findPathEndIndex(uri) : 0;
        decodeParams(uri, pathEndIdx, charset, collector, accumulator, maxParams, semicolonIsNormalChar);
    }

    /**
     * Decodes the specified URI encoded in the specified charset.
     */
    public static Map<String, List<String>> decodeParams(URI uri, Charset charset, int maxParams,
                                                         boolean semicolonIsNormalChar) {
        checkNotNull(uri, "uri");
        checkNotNull(charset, "charset");
        checkPositive(maxParams, "maxParams");
        String parsableUri = parsableUri(uri);
        int pathEndIdx = pathEndIdx(uri);
        final Map<String, List<String>> params = new LinkedHashMap<String, List<String>>();
        decodeParams(parsableUri, pathEndIdx, charset, MAP_PARAMETER_COLLECTOR, params, maxParams,
                     semicolonIsNormalChar);
        return params;
    }

    /**
     * Decodes the specified URI encoded in the specified charset.
     */
    public static Map<String, List<String>> decodeParams(String uri, Charset charset, boolean hasPath, int maxParams,
                                                         boolean semicolonIsNormalChar) {
        checkNotNull(uri, "uri");
        checkNotNull(charset, "charset");
        checkPositive(maxParams, "maxParams");
        int pathEndIdx = hasPath? findPathEndIndex(uri) : 0;
        final Map<String, List<String>> params = new LinkedHashMap<String, List<String>>();
        decodeParams(uri, pathEndIdx, charset, MAP_PARAMETER_COLLECTOR, params, maxParams, semicolonIsNormalChar);
        return params;
    }

    private static <C> void decodeParams(String s, int from, Charset charset,
                                         ParameterCollector<? super C> collector, C accumulator,
                                         int paramsLimit, boolean semicolonIsNormalChar) {
        int len = s.length();
        if (from >= len) {
            return;
        }
        if (s.charAt(from) == '?') {
            from++;
        }
        int nameStart = from;
        for (int p = 0; p < paramsLimit; p++) {
            int valueEndExclusive = -1;
            int indexOfEquals = -1;
            // let's use a switch to let JIT arrange it the best it can
            loop:
            for (int i = nameStart; i < len; i++) {
                switch (s.charAt(i)) {
                case '=':
                    indexOfEquals = i;
                    break loop;
                case ';':
                    if (semicolonIsNormalChar) {
                        continue;
                    }
                    // fall-through
                case '&':
                    valueEndExclusive = i;
                    break loop;
                case '#':
                    len = i;
                    break loop;
                }
            }
            int nextValueStart = -1;
            if (indexOfEquals != -1) {
                // we have found `=` first (which is quite common); we can drop on check
                nextValueStart = indexOfEquals + 1;
                // no need for a switch; we bias our decisions based on the current state
                for (int i = nextValueStart; i < len; i++) {
                    char ch = s.charAt(i);
                    if (ch == '&' || (!semicolonIsNormalChar && ch == ';')) {
                        valueEndExclusive = i;
                        break;
                    }
                    if (ch == '#') {
                        len = i;
                        break;
                    }
                }
            }
            if (valueEndExclusive == -1) {
                valueEndExclusive = len;
            }
            int valueStart;
            if (nextValueStart != -1) {
                valueStart = nextValueStart;
                if (valueStart == nameStart + 1) {
                    // uncommon slow path: it seems there is no name!
                    // search nameStart while skipping useless subsequent =, if any
                    nameStart = skipIf(s, valueStart, valueEndExclusive, '=');
                    valueStart = indexOf(s, nameStart + 1, valueEndExclusive, '=');
                }
            } else {
                valueStart = -1;
            }
            addParam(s, nameStart, valueStart, valueEndExclusive, collector, accumulator, charset);
            if (valueEndExclusive == len) {
                break;
            }
            nameStart = valueEndExclusive + 1;
        }
    }

    private static int indexOf(CharSequence s, int from, int to, int ch) {
        for (int i = from; i < to; i++) {
            if (s.charAt(i) == ch) {
                return i;
            }
        }
        return -1;
    }

    private static int skipIf(CharSequence s, int from, int to, int ch) {
        for (int i = from; i < to; i++) {
            if (s.charAt(i) != ch) {
                return i;
            }
        }
        return to;
    }

    private static final ParameterCollector<Map<String, List<String>>> MAP_PARAMETER_COLLECTOR =
            new ParameterCollector<Map<String, List<String>>>() {
                @Override
                public void collect(String name, String value, Map<String, List<String>> accumulator) {
                    List<String> values = accumulator.get(name);
                    if (values == null) {
                        values = new ArrayList<String>(1);
                        accumulator.put(name, values);
                    }
                    values.add(value);
                }
            };

    /**
     * @return {@link ParameterCollector} which behave as its {@link ParameterCollector#collect} is defined as:
     *
     * <pre>
     * {@code
     * @Override
     * public void accept(String name, String value, Map<String, List<String>> collector) {
     *     List<String> values = collector.get(name);
     *     if (values == null) {
     *         values = new ArrayList<>(1);
     *         collector.put(name, values);
     *     }
     *     values.add(value);
     * }
     * }
     * </pre>
     */
    public static ParameterCollector<Map<String, List<String>>> mapCollector() {
        return MAP_PARAMETER_COLLECTOR;
    }

    /**
     * This interface is used to accumulate Query decoded parameters.<br>
     * The {@link #collect} method receive the query parameters in the same order are
     * decoded from the provided {@code uri}
     * <p>
     * eg "a=1&b=2&c=3"
     * <p>
     * would cause
     * accept("a", "1", map), accept("b", "2", map), accept("c", "3", map)
     * <p>
     * to be called.
     * <p>
     * Order of calling {@link #collect} is an implementation details users shouldn't rely on, anyway,
     * and just store/report/filter them assuming random ordering, instead.
     */
    public interface ParameterCollector<C> {
        void collect(String name, String value, C accumulator);
    }

    private static <C> void addParam(String s, int nameStart, int valueStart, int valueEnd,
                                     ParameterCollector<? super C> collector, C accumulator, Charset charset) {
        if (nameStart >= valueEnd) {
            return;
        }
        if (valueStart <= nameStart) {
            valueStart = valueEnd + 1;
        }
        String name = decodeComponent(s, nameStart, valueStart - 1, charset, false);
        String value = decodeComponent(s, valueStart, valueEnd, charset, false);
        collector.collect(name, value, accumulator);
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
        return decodeComponent(s, 0, s.length(), charset, false);
    }

    private static String decodeComponent(String s, int from, int toExcluded, Charset charset, boolean isPath) {
        int len = toExcluded - from;
        if (len <= 0) {
            return EMPTY_STRING;
        }
        int firstEscaped = getFirstEscaped(s, from, toExcluded, isPath);
        if (firstEscaped == -1) {
            return s.substring(from, toExcluded);
        }

        return decodeEscapedComponent(s, from, toExcluded, charset, isPath, firstEscaped, len);
    }

    private static int getFirstEscaped(CharSequence s, int from, int toExcluded, boolean isPath) {
        int cutOff = isPath? '%' : '+';
        for (int i = from; i < toExcluded; i++) {
            int c = s.charAt(i);
            if (c <= cutOff) {
                if (c == '%' || !isPath && c == '+') {
                    return i;
                }
            }
        }
        return -1;
    }

    private static String decodeEscapedComponent(String s, int from, int toExcluded, Charset charset, boolean isPath,
                                                 int firstEscaped, int len) {
        // Each encoded byte takes 3 characters (e.g. "%20")
        int decodedCapacity = (toExcluded - firstEscaped) / 3;
        byte[] buf = PlatformDependent.allocateUninitializedArray(decodedCapacity);
        int bufIdx;

        StringBuilder strBuf = new StringBuilder(len);
        strBuf.append(s, from, firstEscaped);

        for (int i = firstEscaped; i < toExcluded; i++) {
            char c = s.charAt(i);
            if (c != '%') {
                strBuf.append(c != '+' || isPath? c : SPACE);
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

    private static int findPathEndIndex(CharSequence uri) {
        int len = uri.length();
        for (int i = 0; i < len; i++) {
            char c = uri.charAt(i);
            if (c == '?' || c == '#') {
                return i;
            }
        }
        return len;
    }
}
