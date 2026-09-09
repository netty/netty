/*
 * Copyright 2021 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec.http3;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.codec.UnsupportedValueConverter;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpMessage;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.AsciiString;
import io.netty.util.internal.InternalThreadLocalMap;
import io.netty.util.internal.StringUtil;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import static io.netty.handler.codec.http.HttpHeaderNames.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaderNames.COOKIE;
import static io.netty.handler.codec.http.HttpHeaderNames.TE;
import static io.netty.handler.codec.http.HttpHeaderValues.TRAILERS;
import static io.netty.handler.codec.http.HttpResponseStatus.parseLine;
import static io.netty.handler.codec.http.HttpScheme.HTTP;
import static io.netty.handler.codec.http.HttpScheme.HTTPS;
import static io.netty.handler.codec.http.HttpUtil.isAsteriskForm;
import static io.netty.handler.codec.http.HttpUtil.isOriginForm;
import static io.netty.util.AsciiString.EMPTY_STRING;
import static io.netty.util.AsciiString.contentEqualsIgnoreCase;
import static io.netty.util.AsciiString.indexOf;
import static io.netty.util.AsciiString.trim;
import static io.netty.util.ByteProcessor.FIND_COMMA;
import static io.netty.util.ByteProcessor.FIND_SEMI_COLON;
import static io.netty.util.internal.ObjectUtil.checkNotNull;
import static io.netty.util.internal.StringUtil.isNullOrEmpty;
import static io.netty.util.internal.StringUtil.unescapeCsvFields;

/**
 * Provides utility methods and constants for the HTTP/3 to HTTP conversion
 */
public final class HttpConversionUtil {
    /**
     * The set of headers that should not be directly copied when converting headers from HTTP to HTTP/3.
     */
    private static final CharSequenceMap<AsciiString> HTTP_TO_HTTP3_HEADER_BLACKLIST =
            new CharSequenceMap<>();
    static {
        HTTP_TO_HTTP3_HEADER_BLACKLIST.add(CONNECTION, EMPTY_STRING);
        @SuppressWarnings("deprecation")
        AsciiString keepAlive = HttpHeaderNames.KEEP_ALIVE;
        HTTP_TO_HTTP3_HEADER_BLACKLIST.add(keepAlive, EMPTY_STRING);
        @SuppressWarnings("deprecation")
        AsciiString proxyConnection = HttpHeaderNames.PROXY_CONNECTION;
        HTTP_TO_HTTP3_HEADER_BLACKLIST.add(proxyConnection, EMPTY_STRING);
        HTTP_TO_HTTP3_HEADER_BLACKLIST.add(HttpHeaderNames.TRANSFER_ENCODING, EMPTY_STRING);
        HTTP_TO_HTTP3_HEADER_BLACKLIST.add(HttpHeaderNames.HOST, EMPTY_STRING);
        HTTP_TO_HTTP3_HEADER_BLACKLIST.add(HttpHeaderNames.UPGRADE, EMPTY_STRING);
        HTTP_TO_HTTP3_HEADER_BLACKLIST.add(ExtensionHeaderNames.STREAM_ID.text(), EMPTY_STRING);
        HTTP_TO_HTTP3_HEADER_BLACKLIST.add(ExtensionHeaderNames.SCHEME.text(), EMPTY_STRING);
        HTTP_TO_HTTP3_HEADER_BLACKLIST.add(ExtensionHeaderNames.PATH.text(), EMPTY_STRING);
        HTTP_TO_HTTP3_HEADER_BLACKLIST.add(ExtensionHeaderNames.PROTOCOL.text(), EMPTY_STRING);
    }

    private static final CharSequenceMap<AsciiString> HTTP3_TO_HTTP_HEADER_BLACKLIST =
            new CharSequenceMap<>(false);
    static {
        for (ExtensionHeaderNames name : ExtensionHeaderNames.values()) {
            HTTP3_TO_HTTP_HEADER_BLACKLIST.add(name.text(), EMPTY_STRING);
        }
    }

    /**
     * <a href="https://tools.ietf.org/html/rfc7540#section-8.1.2.3">[RFC 7540], 8.1.2.3</a> states the path must not
     * be empty, and instead should be {@code /}.
     */
    private static final AsciiString EMPTY_REQUEST_PATH = AsciiString.cached("/");

    private HttpConversionUtil() {
    }

    /**
     * Provides the HTTP header extensions used to carry HTTP/3 information in HTTP objects
     */
    public enum ExtensionHeaderNames {
        /**
         * HTTP extension header which will identify the stream id from the HTTP/3 event(s) responsible for
         * generating an {@code HttpObject}
         * <p>
         * {@code "x-http3-stream-id"}
         */
        STREAM_ID("x-http3-stream-id"),
        /**
         * HTTP extension header which will identify the scheme pseudo header from the HTTP/3 event(s) responsible for
         * generating an {@code HttpObject}
         * <p>
         * {@code "x-http3-scheme"}
         */
        SCHEME("x-http3-scheme"),
        /**
         * HTTP extension header which will identify the path pseudo header from the HTTP/3 event(s) responsible for
         * generating an {@code HttpObject}
         * <p>
         * {@code "x-http3-path"}
         */
        PATH("x-http3-path"),
        /**
         * HTTP extension header which will identify the stream id used to create this stream in an HTTP/3 push promise
         * frame
         * <p>
         * {@code "x-http3-stream-promise-id"}
         */
        STREAM_PROMISE_ID("x-http3-stream-promise-id"),
        /**
         * HTTP extension header which will identify the protocol pseudo header from an Extended CONNECT
         * (<a href="https://tools.ietf.org/html/rfc9220">RFC 9220</a>) HTTP/3 event responsible for generating an
         * {@code HttpObject}
         * <p>
         * {@code "x-http3-protocol"}
         */
        PROTOCOL("x-http3-protocol");

        private final AsciiString text;

        ExtensionHeaderNames(String text) {
            this.text = AsciiString.cached(text);
        }

        public AsciiString text() {
            return text;
        }
    }

    /**
     * Apply HTTP/3 rules while translating status code to {@link HttpResponseStatus}
     *
     * @param status The status from an HTTP/3 frame
     * @return The HTTP/1.x status
     * @throws Http3Exception If there is a problem translating from HTTP/3 to HTTP/1.x
     */
    private static HttpResponseStatus parseStatus(long streamId, @Nullable CharSequence status) throws Http3Exception {
        HttpResponseStatus result;
        try {
            result = parseLine(status);
            if (result == HttpResponseStatus.SWITCHING_PROTOCOLS) {
                throw streamError(streamId, Http3ErrorCode.H3_MESSAGE_ERROR,
                        "Invalid HTTP/3 status code '" + status + "'", null);
            }
        } catch (Http3Exception e) {
            throw e;
        } catch (Throwable t) {
            throw streamError(streamId, Http3ErrorCode.H3_MESSAGE_ERROR, "Unrecognized HTTP status code '"
                    + status + "' encountered in translation to HTTP/1.x" + status, null);
        }
        return result;
    }

    /**
     * Create a new object to contain the response data
     *
     * @param streamId The stream associated with the response
     * @param http3Headers The initial set of HTTP/3 headers to create the response with
     * @param alloc The {@link ByteBufAllocator} to use to generate the content of the message
     * @param validateHttpHeaders <ul>
     *        <li>{@code true} to validate HTTP headers in the http-codec</li>
     *        <li>{@code false} not to validate HTTP headers in the http-codec</li>
     *        </ul>
     * @return A new response object which represents headers/data
     * @throws Http3Exception
     */
    static FullHttpResponse toFullHttpResponse(long streamId, Http3Headers http3Headers, ByteBufAllocator alloc,
                                                      boolean validateHttpHeaders) throws Http3Exception {
        ByteBuf content = alloc.buffer();
        HttpResponseStatus status = parseStatus(streamId, http3Headers.status());
        // HTTP/3 does not define a way to carry the version or reason phrase that is included in an
        // HTTP/1.1 status line.
        FullHttpResponse msg = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, content,
                                                           validateHttpHeaders);
        try {
            addHttp3ToHttpHeaders(streamId, http3Headers, msg, false);
        } catch (Http3Exception e) {
            msg.release();
            throw e;
        } catch (Throwable t) {
            msg.release();
            throw streamError(streamId, Http3ErrorCode.H3_MESSAGE_ERROR,
                    "HTTP/3 to HTTP/1.x headers conversion error", t);
        }
        return msg;
    }

    private static CharSequence extractPath(CharSequence method, Http3Headers headers) {
        if (HttpMethod.CONNECT.asciiName().contentEqualsIgnoreCase(method)) {
            // See https://tools.ietf.org/html/rfc7231#section-4.3.6
            return checkNotNull(headers.authority(),
                    "authority header cannot be null in the conversion to HTTP/1.x");
        } else {
            return checkNotNull(headers.path(),
                    "path header cannot be null in conversion to HTTP/1.x");
        }
    }

    /**
     * Create a new object to contain the request data
     *
     * @param streamId The stream associated with the request
     * @param http3Headers The initial set of HTTP/3 headers to create the request with
     * @param alloc The {@link ByteBufAllocator} to use to generate the content of the message
     * @param validateHttpHeaders <ul>
     *        <li>{@code true} to validate HTTP headers in the http-codec</li>
     *        <li>{@code false} not to validate HTTP headers in the http-codec</li>
     *        </ul>
     * @return A new request object which represents headers/data
     * @throws Http3Exception
     */
    static FullHttpRequest toFullHttpRequest(long streamId, Http3Headers http3Headers, ByteBufAllocator alloc,
                                                    boolean validateHttpHeaders) throws Http3Exception {
        ByteBuf content = alloc.buffer();
        // HTTP/3 does not define a way to carry the version identifier that is included in the HTTP/1.1 request line.
        final CharSequence method = checkNotNull(http3Headers.method(),
                "method header cannot be null in conversion to HTTP/1.x");
        final CharSequence path = extractPath(method, http3Headers);
        FullHttpRequest msg = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.valueOf(method
                        .toString()), path.toString(), content, validateHttpHeaders);
        try {
            addHttp3ToHttpHeaders(streamId, http3Headers, msg, false);
        } catch (Http3Exception e) {
            msg.release();
            throw e;
        } catch (Throwable t) {
            msg.release();
            throw streamError(streamId, Http3ErrorCode.H3_MESSAGE_ERROR,
                    "HTTP/3 to HTTP/1.x headers conversion error", t);
        }
        return msg;
    }

    /**
     * Create a new object to contain the request data.
     *
     * @param streamId The stream associated with the request
     * @param http3Headers The initial set of HTTP/3 headers to create the request with
     * @param validateHttpHeaders <ul>
     *        <li>{@code true} to validate HTTP headers in the http-codec</li>
     *        <li>{@code false} not to validate HTTP headers in the http-codec</li>
     *        </ul>
     * @return A new request object which represents headers for a chunked request
     * @throws Http3Exception
     */
    static HttpRequest toHttpRequest(long streamId, Http3Headers http3Headers, boolean validateHttpHeaders)
                    throws Http3Exception {
        // HTTP/3 does not define a way to carry the version identifier that is included in the HTTP/1.1 request line.
        final CharSequence method = checkNotNull(http3Headers.method(),
                "method header cannot be null in conversion to HTTP/1.x");
        final CharSequence path = extractPath(method, http3Headers);
        HttpRequest msg = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.valueOf(method.toString()),
                path.toString(), validateHttpHeaders);
        try {
            addHttp3ToHttpHeaders(streamId, http3Headers, msg.headers(), msg.protocolVersion(), false, true);
        } catch (Http3Exception e) {
            throw e;
        } catch (Throwable t) {
            throw streamError(streamId, Http3ErrorCode.H3_MESSAGE_ERROR,
                    "HTTP/3 to HTTP/1.x headers conversion error", t);
        }
        return msg;
    }

    /**
     * Create a new object to contain the response data.
     *
     * @param streamId The stream associated with the response
     * @param http3Headers The initial set of HTTP/3 headers to create the response with
     * @param validateHttpHeaders <ul>
     *        <li>{@code true} to validate HTTP headers in the http-codec</li>
     *        <li>{@code false} not to validate HTTP headers in the http-codec</li>
     *        </ul>
     * @return A new response object which represents headers for a chunked response
     * @throws Http3Exception
     */
    static HttpResponse toHttpResponse(final long streamId,
                                              final Http3Headers http3Headers,
                                              final boolean validateHttpHeaders) throws Http3Exception {
        final HttpResponseStatus status = parseStatus(streamId, http3Headers.status());
        // HTTP/3 does not define a way to carry the version or reason phrase that is included in an
        // HTTP/1.1 status line.
        final HttpResponse msg = new DefaultHttpResponse(HttpVersion.HTTP_1_1, status, validateHttpHeaders);
        try {
            addHttp3ToHttpHeaders(streamId, http3Headers, msg.headers(), msg.protocolVersion(), false, false);
        } catch (final Http3Exception e) {
            throw e;
        } catch (final Throwable t) {
            throw streamError(streamId, Http3ErrorCode.H3_MESSAGE_ERROR,
                    "HTTP/3 to HTTP/1.x headers conversion error", t);
        }
        return msg;
    }

    /**
     * Translate and add HTTP/3 headers to HTTP/1.x headers.
     *
     * @param streamId The stream associated with {@code sourceHeaders}.
     * @param inputHeaders The HTTP/3 headers to convert.
     * @param destinationMessage The object which will contain the resulting HTTP/1.x headers.
     * @param addToTrailer {@code true} to add to trailing headers. {@code false} to add to initial headers.
     * @throws Http3Exception If not all HTTP/3 headers can be translated to HTTP/1.x.
     */
    private static void addHttp3ToHttpHeaders(long streamId, Http3Headers inputHeaders,
                    FullHttpMessage destinationMessage, boolean addToTrailer) throws Http3Exception {
        addHttp3ToHttpHeaders(streamId, inputHeaders,
                addToTrailer ? destinationMessage.trailingHeaders() : destinationMessage.headers(),
                destinationMessage.protocolVersion(), addToTrailer, destinationMessage instanceof HttpRequest);
    }

    /**
     * Translate and add HTTP/3 headers to HTTP/1.x headers.
     *
     * @param streamId The stream associated with {@code sourceHeaders}.
     * @param inputHeaders The HTTP/3 headers to convert.
     * @param outputHeaders The object which will contain the resulting HTTP/1.x headers..
     * @param httpVersion What HTTP/1.x version {@code outputHeaders} should be treated as when doing the conversion.
     * @param isTrailer {@code true} if {@code outputHeaders} should be treated as trailing headers.
     * {@code false} otherwise.
     * @param isRequest {@code true} if the {@code outputHeaders} will be used in a request message.
     * {@code false} for response message.
     * @throws Http3Exception If not all HTTP/3 headers can be translated to HTTP/1.x.
     */
     static void addHttp3ToHttpHeaders(long streamId, Http3Headers inputHeaders, HttpHeaders outputHeaders,
            HttpVersion httpVersion, boolean isTrailer, boolean isRequest) throws Http3Exception {
         // Extended CONNECT (RFC 9220) changes the semantics of a CONNECT request: the server must not treat
         // ':authority' as an ordinary tunnel target the way it would for a regular CONNECT request. Preserve the
         // ':protocol' and ':path' pseudo-headers as extension headers so that code operating on the converted
         // HTTP/1.x object can still distinguish an Extended CONNECT request from a regular CONNECT request.
         boolean isConnect = isRequest && HttpMethod.CONNECT.asciiName().contentEqualsIgnoreCase(inputHeaders.method());
         Http3ToHttpHeaderTranslator translator =
                 new Http3ToHttpHeaderTranslator(streamId, outputHeaders, isRequest, isConnect);
        try {
            translator.translateHeaders(inputHeaders);
        } catch (Http3Exception ex) {
            throw ex;
        } catch (Throwable t) {
            throw streamError(streamId, Http3ErrorCode.H3_MESSAGE_ERROR,
                    "HTTP/3 to HTTP/1.x headers conversion error", t);
        }

        outputHeaders.remove(HttpHeaderNames.TRANSFER_ENCODING);
        outputHeaders.remove(HttpHeaderNames.TRAILER);
        if (!isTrailer) {
            outputHeaders.set(ExtensionHeaderNames.STREAM_ID.text(), streamId);
            HttpUtil.setKeepAlive(outputHeaders, httpVersion, true);
        }
    }

    /**
     * Converts the given HTTP/1.x headers into HTTP/3 headers.
     * The following headers are only used if they can not be found in from the {@code HOST} header or the
     * {@code Request-Line} as defined by <a href="https://tools.ietf.org/html/rfc7230">rfc7230</a>
     * <ul>
     * <li>{@link ExtensionHeaderNames#SCHEME}</li>
     * </ul>
     * {@link ExtensionHeaderNames#PATH} is ignored and instead extracted from the {@code Request-Line}.
     */
    static Http3Headers toHttp3Headers(HttpMessage in, boolean validateHeaders) {
        HttpHeaders inHeaders = in.headers();
        final Http3Headers out = new DefaultHttp3Headers(validateHeaders, inHeaders.size());
        if (in instanceof HttpRequest) {
            HttpRequest request = (HttpRequest) in;
            if (HttpMethod.CONNECT.equals(request.method())) {
                // https://www.rfc-editor.org/rfc/rfc9114#section-4.4 requires that :scheme and :path are
                // omitted, and that :authority is the authority-form request-target from the HTTP/1.x
                // request-line (https://www.rfc-editor.org/rfc/rfc9112#section-3.2.3). The Host header must
                // not be allowed to override the CONNECT target, so it is intentionally ignored here.

                String authorityForm = request.uri();
                if (authorityForm != null) {
                    // CONNECT uses a special form of request target, unique to this method, consisting of only the
                    // host and port number of the tunnel destination, separated by a colon per
                    // https://www.rfc-editor.org/info/rfc9110/#name-connect
                    if (authorityForm.isEmpty() || authorityForm.indexOf('@') >= 0 || authorityForm.indexOf('/') >= 0) {
                        throw new IllegalArgumentException("Invalid CONNECT request target: " + authorityForm);
                    }
                    out.authority(new AsciiString(authorityForm));
                }
            } else {
                String host = inHeaders.getAsString(HttpHeaderNames.HOST);
                if (isOriginForm(request.uri()) || isAsteriskForm(request.uri())) {
                    out.path(new AsciiString(request.uri()));
                    setHttp3Scheme(inHeaders, out);
                } else {
                    String requestTarget = request.uri();
                    out.path(toHttp3Path(requestTarget));
                    if (hasSchemeAndAuthority(requestTarget)) {
                        URI requestTargetUri = URI.create(http3PathlessRequestTarget(requestTarget));
                        // The absolute-form request-target authority is authoritative and takes precedence over
                        // a (potentially conflicting) HOST header, per RFC 9112 section 3.2 and RFC 9113 section 8.3.1.
                        String requestTargetAuthority = requestTargetUri.getAuthority();
                        host = isNullOrEmpty(requestTargetAuthority) ? host : requestTargetAuthority;
                        setHttp3Scheme(inHeaders, requestTargetUri, out);
                    } else {
                        int schemeEnd = schemeEnd(requestTarget);
                        if (schemeEnd != -1) {
                            setHttp3Scheme(inHeaders, requestTarget.substring(0, schemeEnd), -1, out);
                        } else {
                            setHttp3Scheme(inHeaders, out);
                        }
                    }
                }
                setHttp3Authority(host, out);
            }
            out.method(request.method().asciiName());
        } else if (in instanceof HttpResponse) {
            HttpResponse response = (HttpResponse) in;
            out.status(response.status().codeAsText());
        }

        // Add the HTTP headers which have not been consumed above
        toHttp3Headers(inHeaders, out);
        return out;
    }

    static Http3Headers toHttp3Headers(HttpHeaders inHeaders, boolean validateHeaders) {
        if (inHeaders.isEmpty()) {
            return new DefaultHttp3Headers();
        }

        final Http3Headers out = new DefaultHttp3Headers(validateHeaders, inHeaders.size());
        toHttp3Headers(inHeaders, out);
        return out;
    }

    private static CharSequenceMap<AsciiString> toLowercaseMap(Iterator<? extends CharSequence> valuesIter,
                                                               int arraySizeHint) {
        UnsupportedValueConverter<AsciiString> valueConverter = UnsupportedValueConverter.<AsciiString>instance();
        CharSequenceMap<AsciiString> result = new CharSequenceMap<AsciiString>(true, valueConverter, arraySizeHint);

        while (valuesIter.hasNext()) {
            AsciiString lowerCased = AsciiString.of(valuesIter.next()).toLowerCase();
            try {
                int index = lowerCased.forEachByte(FIND_COMMA);
                if (index != -1) {
                    int start = 0;
                    do {
                        result.add(lowerCased.subSequence(start, index, false).trim(), EMPTY_STRING);
                        start = index + 1;
                    } while (start < lowerCased.length() &&
                             (index = lowerCased.forEachByte(start, lowerCased.length() - start, FIND_COMMA)) != -1);
                    result.add(lowerCased.subSequence(start, lowerCased.length(), false).trim(), EMPTY_STRING);
                } else {
                    result.add(lowerCased.trim(), EMPTY_STRING);
                }
            } catch (Exception e) {
                // This is not expect to happen because FIND_COMMA never throws but must be caught
                // because of the ByteProcessor interface.
                throw new IllegalStateException(e);
            }
        }
        return result;
    }

    /**
     * Filter the {@link HttpHeaderNames#TE} header according to the
     * <a href="https://quicwg.org/base-drafts/draft-ietf-quic-http.html#section-4.1.1">
     *     special rules in the HTTP/3 RFC</a>.
     * @param entry An entry whose name is {@link HttpHeaderNames#TE}.
     * @param out the resulting HTTP/3 headers.
     */
    private static void toHttp3HeadersFilterTE(Entry<CharSequence, CharSequence> entry,
                                               Http3Headers out) {
        if (indexOf(entry.getValue(), ',', 0) == -1) {
            if (contentEqualsIgnoreCase(trim(entry.getValue()), TRAILERS)) {
                out.add(TE, TRAILERS);
            }
        } else {
            List<CharSequence> teValues = unescapeCsvFields(entry.getValue());
            for (CharSequence teValue : teValues) {
                if (contentEqualsIgnoreCase(trim(teValue), TRAILERS)) {
                    out.add(TE, TRAILERS);
                    break;
                }
            }
        }
    }

    static void toHttp3Headers(HttpHeaders inHeaders, Http3Headers out) {
        Iterator<Entry<CharSequence, CharSequence>> iter = inHeaders.iteratorCharSequence();
        // Choose 8 as a default size because it is unlikely we will see more than 4 Connection headers values, but
        // still allowing for "enough" space in the map to reduce the chance of hash code collision.
        CharSequenceMap<AsciiString> connectionBlacklist =
            toLowercaseMap(inHeaders.valueCharSequenceIterator(CONNECTION), 8);
        while (iter.hasNext()) {
            Entry<CharSequence, CharSequence> entry = iter.next();
            final AsciiString aName = AsciiString.of(entry.getKey()).toLowerCase();
            if (!HTTP_TO_HTTP3_HEADER_BLACKLIST.contains(aName) && !connectionBlacklist.contains(aName)) {
                // https://quicwg.org/base-drafts/draft-ietf-quic-http.html#section-4.1.1 makes a special exception
                // for TE
                if (aName.contentEqualsIgnoreCase(TE)) {
                    toHttp3HeadersFilterTE(entry, out);
                } else if (aName.contentEqualsIgnoreCase(COOKIE)) {
                    AsciiString value = AsciiString.of(entry.getValue());
                    // split up cookies to allow for better compression
                    try {
                        int index = value.forEachByte(FIND_SEMI_COLON);
                        if (index != -1) {
                            int start = 0;
                            do {
                                out.add(COOKIE, value.subSequence(start, index, false));
                                // skip 2 characters "; " (see https://tools.ietf.org/html/rfc6265#section-4.2.1)
                                start = index + 2;
                            } while (start < value.length() &&
                                    (index = value.forEachByte(start, value.length() - start, FIND_SEMI_COLON)) != -1);
                            if (start >= value.length()) {
                                throw new IllegalArgumentException("cookie value is of unexpected format: " + value);
                            }
                            out.add(COOKIE, value.subSequence(start, value.length(), false));
                        } else {
                            out.add(COOKIE, value);
                        }
                    } catch (Exception e) {
                        // This is not expect to happen because FIND_SEMI_COLON never throws but must be caught
                        // because of the ByteProcessor interface.
                        throw new IllegalStateException(e);
                    }
                } else {
                    out.add(aName, entry.getValue());
                }
            }
        }
    }

    /**
     * Generate an HTTP/3 {code :path} from a URI in accordance with
     * <a href="https://quicwg.org/base-drafts/draft-ietf-quic-http.html#section-4.1.1.1">HTTP3 spec</a>.
     */
    private static AsciiString toHttp3Path(String uri) {
        String path = dropEmptyFragment(parsePath(uri));
        String query = parseQuery(uri);
        if (isNullOrEmpty(query)) {
            return path.isEmpty() ? EMPTY_REQUEST_PATH : new AsciiString(path);
        }
        StringBuilder pathBuilder = new StringBuilder(path.length() + query.length() + 1);
        pathBuilder.append(path);
        appendQuery(pathBuilder, query);
        return new AsciiString(pathBuilder.toString());
    }

    /**
     * Extract the path out of the request-target. Based on Vert.x' HttpUtils.parsePath logic.
     */
    private static String parsePath(String uri) {
        if (uri.isEmpty()) {
            return StringUtil.EMPTY_STRING;
        }
        int i;
        if (uri.charAt(0) == '/') {
            i = 0;
        } else {
            i = uri.indexOf("://");
            // Netty change: validate the scheme before treating :// as authority syntax.
            if (!isValidScheme(uri, i)) {
                i = 0;
            } else {
                int authorityStart = i + 3;
                // Netty change: only accept '/' before query/fragment as path start.
                int queryOrFragmentStart = queryOrFragmentStart(uri, authorityStart);
                i = uri.indexOf('/', authorityStart);
                if (i == -1 || (queryOrFragmentStart != -1 && queryOrFragmentStart < i)) {
                    // contains no /
                    return "/";
                }
            }
        }

        int queryStart = uri.indexOf('?', i);
        if (queryStart == -1) {
            queryStart = uri.length();
            if (i == 0) {
                return uri;
            }
        }
        return uri.substring(i, queryStart);
    }

    /**
     * Extract the query out of a request-target or returns {@code null} if no query was found.
     */
    private static String parseQuery(String uri) {
        int i = uri.indexOf('?');
        if (i == -1) {
            return null;
        } else {
            return uri.substring(i + 1);
        }
    }

    private static String dropEmptyFragment(String path) {
        // Netty change: old URI-based conversion dropped an empty fragment delimiter.
        return path.endsWith("#") ? path.substring(0, path.length() - 1) : path;
    }

    private static void appendQuery(StringBuilder pathBuilder, String query) {
        int fragmentStart = query.indexOf('#');
        if (fragmentStart == 0) {
            // Netty change: old URI-based conversion skipped an empty query before a fragment.
            pathBuilder.append(query);
        } else if (fragmentStart == query.length() - 1) {
            // Netty change: old URI-based conversion dropped an empty fragment delimiter after a query.
            pathBuilder.append('?').append(query, 0, fragmentStart);
        } else {
            pathBuilder.append('?').append(query);
        }
    }

    static int queryOrFragmentStart(String uri, int searchStart) {
        int queryStart = uri.indexOf('?', searchStart);
        int fragmentStart = uri.indexOf('#', searchStart);
        return queryStart == -1 ? fragmentStart :
            fragmentStart == -1 ? queryStart : Math.min(queryStart, fragmentStart);
    }

    // Netty addition: detect authority for HTTP/3 :scheme/:authority extraction.
    static boolean hasSchemeAndAuthority(String requestTarget) {
        int schemeEnd = requestTarget.indexOf("://");
        return isValidScheme(requestTarget, schemeEnd);
    }

    private static int schemeEnd(String requestTarget) {
        int schemeEnd = requestTarget.indexOf(':');
        return isValidScheme(requestTarget, schemeEnd) ? schemeEnd : -1;
    }

    // Netty addition: prepare only scheme://authority for URI validation.
    private static String http3PathlessRequestTarget(String requestTarget) {
        int schemeEnd = requestTarget.indexOf("://");
        int authorityStart = schemeEnd + 3;
        // Netty addition: strip before path/query/fragment; Vert.x parsePath does not validate authority.
        int pathStart = requestTarget.indexOf('/', authorityStart);
        int delimiter = queryOrFragmentStart(requestTarget, authorityStart);
        if (pathStart != -1 && (delimiter == -1 || pathStart < delimiter)) {
            delimiter = pathStart;
        }
        if (delimiter == -1) {
            return requestTarget;
        }
        return delimiter == authorityStart ? requestTarget.substring(0, delimiter + 1) :
            requestTarget.substring(0, delimiter);
    }

    // Netty addition: validate the text before :// as a scheme.
    static boolean isValidScheme(String uri, int schemeEnd) {
        if (schemeEnd <= 0) {
            return false;
        }
        char first = uri.charAt(0);
        if (!isAlpha(first)) {
            return false;
        }
        for (int i = 1; i < schemeEnd; ++i) {
            char c = uri.charAt(i);
            if (!isAlpha(c) && (c < '0' || c > '9') && c != '+' && c != '-' && c != '.') {
                return false;
            }
        }
        return true;
    }

    private static boolean isAlpha(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    // package-private for testing only
    static void setHttp3Authority(String authority, Http3Headers out) {
        // The authority MUST NOT include the deprecated "userinfo" subcomponent
        if (authority != null) {
            if (authority.isEmpty()) {
                out.authority(EMPTY_STRING);
            } else {
                int start = authority.indexOf('@') + 1;
                int length = authority.length() - start;
                if (length == 0) {
                    throw new IllegalArgumentException("authority: " + authority);
                }
                out.authority(new AsciiString(authority, start, length));
            }
        }
    }

    private static void setHttp3Scheme(HttpHeaders in, Http3Headers out) {
        setHttp3Scheme(in, URI.create(""), out);
    }

    private static void setHttp3Scheme(HttpHeaders in, URI uri, Http3Headers out) {
        setHttp3Scheme(in, uri.getScheme(), uri.getPort(), out);
    }

    private static void setHttp3Scheme(HttpHeaders in, String scheme, int port, Http3Headers out) {
        if (!isNullOrEmpty(scheme)) {
            out.scheme(new AsciiString(scheme));
            return;
        }

        // Consume the Scheme extension header if present
        CharSequence cValue = in.get(ExtensionHeaderNames.SCHEME.text());
        if (cValue != null) {
            out.scheme(AsciiString.of(cValue));
            return;
        }

        if (port == HTTPS.port()) {
            out.scheme(HTTPS.name());
        } else if (port == HTTP.port()) {
            out.scheme(HTTP.name());
        } else {
            throw new IllegalArgumentException(":scheme must be specified. " +
                "see https://quicwg.org/base-drafts/draft-ietf-quic-http.html#section-4.1.1.1");
        }
    }

    /**
     * Utility which translates HTTP/3 headers to HTTP/1 headers.
     */
    private static final class Http3ToHttpHeaderTranslator {
        /**
         * Translations from HTTP/3 header name to the HTTP/1.x equivalent.
         */
        private static final CharSequenceMap<AsciiString>
            REQUEST_HEADER_TRANSLATIONS = new CharSequenceMap<AsciiString>();
        private static final CharSequenceMap<AsciiString>
            RESPONSE_HEADER_TRANSLATIONS = new CharSequenceMap<AsciiString>();
        /**
         * Translations used for Extended CONNECT (RFC 9220) requests. In addition to the regular request
         * translations, the ':path' and ':protocol' pseudo-headers are preserved as extension headers so that
         * an Extended CONNECT request cannot be mistaken for a regular CONNECT request once converted to an
         * HTTP/1.x object.
         */
        private static final CharSequenceMap<AsciiString>
            CONNECT_REQUEST_HEADER_TRANSLATIONS = new CharSequenceMap<AsciiString>();
        static {
            RESPONSE_HEADER_TRANSLATIONS.add(Http3Headers.PseudoHeaderName.AUTHORITY.value(),
                            HttpHeaderNames.HOST);
            RESPONSE_HEADER_TRANSLATIONS.add(Http3Headers.PseudoHeaderName.SCHEME.value(),
                            ExtensionHeaderNames.SCHEME.text());
            REQUEST_HEADER_TRANSLATIONS.add(RESPONSE_HEADER_TRANSLATIONS);
            RESPONSE_HEADER_TRANSLATIONS.add(Http3Headers.PseudoHeaderName.PATH.value(),
                            ExtensionHeaderNames.PATH.text());
            CONNECT_REQUEST_HEADER_TRANSLATIONS.add(REQUEST_HEADER_TRANSLATIONS);
            CONNECT_REQUEST_HEADER_TRANSLATIONS.add(Http3Headers.PseudoHeaderName.PATH.value(),
                            ExtensionHeaderNames.PATH.text());
            CONNECT_REQUEST_HEADER_TRANSLATIONS.add(Http3Headers.PseudoHeaderName.PROTOCOL.value(),
                            ExtensionHeaderNames.PROTOCOL.text());
        }

        private final long streamId;
        private final HttpHeaders output;
        private final CharSequenceMap<AsciiString> translations;

        /**
         * Create a new instance
         *
         * @param output The HTTP/1.x headers object to store the results of the translation
         * @param request if {@code true}, translates headers using the request translation map. Otherwise uses the
         *        response translation map.
         * @param connect if {@code true}, translates headers using the CONNECT request translation map, which
         *        additionally preserves the ':path' and ':protocol' pseudo-headers of an Extended CONNECT
         *        (RFC 9220) request as extension headers. Ignored unless {@code request} is {@code true}.
         */
        Http3ToHttpHeaderTranslator(long streamId, HttpHeaders output, boolean request, boolean connect) {
            this.streamId = streamId;
            this.output = output;
            if (request) {
                translations = connect ? CONNECT_REQUEST_HEADER_TRANSLATIONS : REQUEST_HEADER_TRANSLATIONS;
            } else {
                translations = RESPONSE_HEADER_TRANSLATIONS;
            }
        }

        void translateHeaders(Iterable<Entry<CharSequence, CharSequence>> inputHeaders) throws Http3Exception {
            // lazily created as needed
            StringBuilder cookies = null;
            boolean hostHeaderFound = false;
            boolean authorityFound = false;

            for (Entry<CharSequence, CharSequence> entry : inputHeaders) {
                final CharSequence name = entry.getKey();
                final CharSequence value = entry.getValue();
                AsciiString translatedName = translations.get(name);
                if (translatedName != null) {
                    if (translatedName.contentEqualsIgnoreCase(HttpHeaderNames.HOST)) {
                        hostHeaderFound = true;
                        authorityFound = true;
                    }
                    output.add(translatedName, AsciiString.of(value));
                } else if (!Http3Headers.PseudoHeaderName.isPseudoHeader(name)) {
                    // https://tools.ietf.org/html/rfc7540#section-8.1.2.3
                    // All headers that start with ':' are only valid in HTTP/3 context
                    if (name.length() == 0 || name.charAt(0) == ':') {
                        throw streamError(streamId, Http3ErrorCode.H3_MESSAGE_ERROR,
                                "Invalid HTTP/3 header '" + name + "' encountered in translation to HTTP/1.x",
                                null);
                    }
                    if (HTTP3_TO_HTTP_HEADER_BLACKLIST.contains(name)) {
                        continue;
                    }
                    if (COOKIE.equals(name)) {
                        // combine the cookie values into 1 header entry.
                        // https://tools.ietf.org/html/rfc7540#section-8.1.2.5
                        if (cookies == null) {
                            cookies = InternalThreadLocalMap.get().stringBuilder();
                        } else if (cookies.length() > 0) {
                            cookies.append("; ");
                        }
                        cookies.append(value);
                    } else if (contentEqualsIgnoreCase(HttpHeaderNames.HOST, name)) {
                        // https://www.rfc-editor.org/rfc/rfc9114#section-4.3.1
                        // the request MUST contain either an :authority pseudo-header field or a Host header field.
                        // If both fields are present, they MUST contain the same value.
                        // https://datatracker.ietf.org/doc/html/rfc9112#section-3.2
                        // A server MUST respond with a 400 (Bad Request) status code to any HTTP/1.1 request message
                        // that ... contains more than one Host header field line
                        if (hostHeaderFound) {
                            if (!contentEqualsIgnoreCase(output.get(HttpHeaderNames.HOST), value)) {
                                throw streamError(streamId, Http3ErrorCode.H3_MESSAGE_ERROR,
                                    authorityFound ? "Conflicting ':authority' and 'host' headers found" :
                                        "Conflicting 'host' headers found", null);
                            }
                        } else {
                            hostHeaderFound = true;
                            output.add(name, value);
                        }
                    } else {
                        output.add(name, value);
                    }
                }
            }
            if (cookies != null) {
                output.add(COOKIE, cookies.toString());
            }
        }
    }

    private static Http3Exception streamError(long streamId, Http3ErrorCode error, String msg,
                                              @Nullable Throwable cause) {
        return new Http3Exception(error, streamId + ": " + msg, cause);
    }
}
