/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec.http2;

import static io.netty.handler.codec.http2.Http2Error.PROTOCOL_ERROR;
import static io.netty.handler.codec.http2.Http2Exception.connectionError;
import static io.netty.handler.codec.http2.Http2Exception.streamError;
import static io.netty.util.internal.ObjectUtil.checkNotNull;
import io.netty.handler.codec.BinaryHeaders;
import io.netty.handler.codec.TextHeaders.EntryVisitor;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpMessage;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderUtil;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.AsciiString;
import io.netty.util.ByteString;

import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Provides utility methods and constants for the HTTP/2 to HTTP conversion
 */
public final class HttpUtil {
    /**
     * The set of headers that should not be directly copied when converting headers from HTTP to HTTP/2.
     */
    @SuppressWarnings("deprecation")
    private static final Set<CharSequence> HTTP_TO_HTTP2_HEADER_BLACKLIST = new HashSet<CharSequence>() {
        private static final long serialVersionUID = -5678614530214167043L;
        {
            add(HttpHeaderNames.CONNECTION);
            add(HttpHeaderNames.KEEP_ALIVE);
            add(HttpHeaderNames.PROXY_CONNECTION);
            add(HttpHeaderNames.TRANSFER_ENCODING);
            add(HttpHeaderNames.HOST);
            add(HttpHeaderNames.UPGRADE);
            add(ExtensionHeaderNames.STREAM_ID.text());
            add(ExtensionHeaderNames.AUTHORITY.text());
            add(ExtensionHeaderNames.SCHEME.text());
            add(ExtensionHeaderNames.PATH.text());
        }
    };

    /**
     * This will be the method used for {@link HttpRequest} objects generated out of the HTTP message flow defined in <a
     * href="http://tools.ietf.org/html/draft-ietf-httpbis-http2-16#section-8.1.">HTTP/2 Spec Message Flow</a>
     */
    public static final HttpMethod OUT_OF_MESSAGE_SEQUENCE_METHOD = HttpMethod.OPTIONS;

    /**
     * This will be the path used for {@link HttpRequest} objects generated out of the HTTP message flow defined in <a
     * href="http://tools.ietf.org/html/draft-ietf-httpbis-http2-16#section-8.1.">HTTP/2 Spec Message Flow</a>
     */
    public static final String OUT_OF_MESSAGE_SEQUENCE_PATH = "";

    /**
     * This will be the status code used for {@link HttpResponse} objects generated out of the HTTP message flow defined
     * in <a href="http://tools.ietf.org/html/draft-ietf-httpbis-http2-16#section-8.1.">HTTP/2 Spec Message Flow</a>
     */
    public static final HttpResponseStatus OUT_OF_MESSAGE_SEQUENCE_RETURN_CODE = HttpResponseStatus.OK;

    /**
     * This pattern will use to avoid compile it each time it is used
     * when we need to replace some part of authority.
     */
    private static final Pattern AUTHORITY_REPLACEMENT_PATTERN = Pattern.compile("^.*@");

    private HttpUtil() {
    }

    /**
     * Provides the HTTP header extensions used to carry HTTP/2 information in HTTP objects
     */
    public enum ExtensionHeaderNames {
        /**
         * HTTP extension header which will identify the stream id from the HTTP/2 event(s) responsible for generating a
         * {@code HttpObject}
         * <p>
         * {@code "x-http2-stream-id"}
         */
        STREAM_ID("x-http2-stream-id"),

        /**
         * HTTP extension header which will identify the authority pseudo header from the HTTP/2 event(s) responsible
         * for generating a {@code HttpObject}
         * <p>
         * {@code "x-http2-authority"}
         */
        AUTHORITY("x-http2-authority"),
        /**
         * HTTP extension header which will identify the scheme pseudo header from the HTTP/2 event(s) responsible for
         * generating a {@code HttpObject}
         * <p>
         * {@code "x-http2-scheme"}
         */
        SCHEME("x-http2-scheme"),
        /**
         * HTTP extension header which will identify the path pseudo header from the HTTP/2 event(s) responsible for
         * generating a {@code HttpObject}
         * <p>
         * {@code "x-http2-path"}
         */
        PATH("x-http2-path"),
        /**
         * HTTP extension header which will identify the stream id used to create this stream in a HTTP/2 push promise
         * frame
         * <p>
         * {@code "x-http2-stream-promise-id"}
         */
        STREAM_PROMISE_ID("x-http2-stream-promise-id"),
        /**
         * HTTP extension header which will identify the stream id which this stream is dependent on. This stream will
         * be a child node of the stream id associated with this header value.
         * <p>
         * {@code "x-http2-stream-dependency-id"}
         */
        STREAM_DEPENDENCY_ID("x-http2-stream-dependency-id"),
        /**
         * HTTP extension header which will identify the weight (if non-default and the priority is not on the default
         * stream) of the associated HTTP/2 stream responsible responsible for generating a {@code HttpObject}
         * <p>
         * {@code "x-http2-stream-weight"}
         */
        STREAM_WEIGHT("x-http2-stream-weight");

        private final AsciiString text;

        ExtensionHeaderNames(String text) {
            this.text = new AsciiString(text);
        }

        public AsciiString text() {
            return text;
        }
    }

    /**
     * Apply HTTP/2 rules while translating status code to {@link HttpResponseStatus}
     *
     * @param status The status from an HTTP/2 frame
     * @return The HTTP/1.x status
     * @throws Http2Exception If there is a problem translating from HTTP/2 to HTTP/1.x
     */
    public static HttpResponseStatus parseStatus(ByteString status) throws Http2Exception {
        HttpResponseStatus result;
        try {
            result = HttpResponseStatus.parseLine(status);
            if (result == HttpResponseStatus.SWITCHING_PROTOCOLS) {
                throw connectionError(PROTOCOL_ERROR, "Invalid HTTP/2 status code '%d'", result.code());
            }
        } catch (Http2Exception e) {
            throw e;
        } catch (Throwable t) {
            throw connectionError(PROTOCOL_ERROR, t,
                            "Unrecognized HTTP status code '%s' encountered in translation to HTTP/1.x", status);
        }
        return result;
    }

    /**
     * Create a new object to contain the response data
     *
     * @param streamId The stream associated with the response
     * @param http2Headers The initial set of HTTP/2 headers to create the response with
     * @param validateHttpHeaders <ul>
     *        <li>{@code true} to validate HTTP headers in the http-codec</li>
     *        <li>{@code false} not to validate HTTP headers in the http-codec</li>
     *        </ul>
     * @return A new response object which represents headers/data
     * @throws Http2Exception see {@link #addHttp2ToHttpHeaders(int, Http2Headers, FullHttpMessage, boolean)}
     */
    public static FullHttpResponse toHttpResponse(int streamId, Http2Headers http2Headers, boolean validateHttpHeaders)
                    throws Http2Exception {
        HttpResponseStatus status = parseStatus(http2Headers.status());
        // HTTP/2 does not define a way to carry the version or reason phrase that is included in an
        // HTTP/1.1 status line.
        FullHttpResponse msg = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, validateHttpHeaders);
        addHttp2ToHttpHeaders(streamId, http2Headers, msg, false);
        return msg;
    }

    /**
     * Create a new object to contain the request data
     *
     * @param streamId The stream associated with the request
     * @param http2Headers The initial set of HTTP/2 headers to create the request with
     * @param validateHttpHeaders <ul>
     *        <li>{@code true} to validate HTTP headers in the http-codec</li>
     *        <li>{@code false} not to validate HTTP headers in the http-codec</li>
     *        </ul>
     * @return A new request object which represents headers/data
     * @throws Http2Exception see {@link #addHttp2ToHttpHeaders(int, Http2Headers, FullHttpMessage, boolean)}
     */
    public static FullHttpRequest toHttpRequest(int streamId, Http2Headers http2Headers, boolean validateHttpHeaders)
                    throws Http2Exception {
        // HTTP/2 does not define a way to carry the version identifier that is included in the HTTP/1.1 request line.
        final ByteString method = checkNotNull(http2Headers.method(),
                "method header cannot be null in conversion to HTTP/1.x");
        final ByteString path = checkNotNull(http2Headers.path(),
                "path header cannot be null in conversion to HTTP/1.x");
        FullHttpRequest msg = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.valueOf(method
                        .toString()), path.toString(), validateHttpHeaders);
        addHttp2ToHttpHeaders(streamId, http2Headers, msg, false);
        return msg;
    }

    /**
     * Translate and add HTTP/2 headers to HTTP/1.x headers
     *
     * @param streamId The stream associated with {@code sourceHeaders}
     * @param sourceHeaders The HTTP/2 headers to convert
     * @param destinationMessage The object which will contain the resulting HTTP/1.x headers
     * @param addToTrailer {@code true} to add to trailing headers. {@code false} to add to initial headers.
     * @throws Http2Exception If not all HTTP/2 headers can be translated to HTTP/1.x
     */
    public static void addHttp2ToHttpHeaders(int streamId, Http2Headers sourceHeaders,
                    FullHttpMessage destinationMessage, boolean addToTrailer) throws Http2Exception {
        HttpHeaders headers = addToTrailer ? destinationMessage.trailingHeaders() : destinationMessage.headers();
        boolean request = destinationMessage instanceof HttpRequest;
        Http2ToHttpHeaderTranslator visitor = new Http2ToHttpHeaderTranslator(streamId, headers, request);
        try {
            sourceHeaders.forEachEntry(visitor);
        } catch (Http2Exception ex) {
            throw ex;
        } catch (Throwable t) {
            throw streamError(streamId, PROTOCOL_ERROR, t, "HTTP/2 to HTTP/1.x headers conversion error");
        }

        headers.remove(HttpHeaderNames.TRANSFER_ENCODING);
        headers.remove(HttpHeaderNames.TRAILER);
        if (!addToTrailer) {
            headers.setInt(ExtensionHeaderNames.STREAM_ID.text(), streamId);
            HttpHeaderUtil.setKeepAlive(destinationMessage, true);
        }
    }

    /**
     * Converts the given HTTP/1.x headers into HTTP/2 headers.
     */
    public static Http2Headers toHttp2Headers(FullHttpMessage in) throws Exception {
        final Http2Headers out = new DefaultHttp2Headers();
        HttpHeaders inHeaders = in.headers();
        if (in instanceof HttpRequest) {
            HttpRequest request = (HttpRequest) in;
            out.path(new AsciiString(request.uri()));
            out.method(new AsciiString(request.method().toString()));

            String value = inHeaders.getAndConvert(HttpHeaderNames.HOST);
            if (value != null) {
                URI hostUri = URI.create(value);
                // The authority MUST NOT include the deprecated "userinfo" subcomponent
                value = hostUri.getAuthority();
                if (value != null) {
                    out.authority(new AsciiString(AUTHORITY_REPLACEMENT_PATTERN.matcher(value).replaceFirst("")));
                }
                value = hostUri.getScheme();
                if (value != null) {
                    out.scheme(new AsciiString(value));
                }
            }

            // Consume the Authority extension header if present
            CharSequence cValue = inHeaders.get(ExtensionHeaderNames.AUTHORITY.text());
            if (cValue != null) {
                out.authority(AsciiString.of(cValue));
            }

            // Consume the Scheme extension header if present
            cValue = inHeaders.get(ExtensionHeaderNames.SCHEME.text());
            if (cValue != null) {
                out.scheme(AsciiString.of(cValue));
            }
        } else if (in instanceof HttpResponse) {
            HttpResponse response = (HttpResponse) in;
            out.status(new AsciiString(Integer.toString(response.status().code())));
        }

        // Add the HTTP headers which have not been consumed above
        inHeaders.forEachEntry(new EntryVisitor() {
            @Override
            public boolean visit(Entry<CharSequence, CharSequence> entry) throws Exception {
                final AsciiString aName = AsciiString.of(entry.getKey()).toLowerCase();
                if (!HTTP_TO_HTTP2_HEADER_BLACKLIST.contains(aName)) {
                    AsciiString aValue = AsciiString.of(entry.getValue());
                    // https://tools.ietf.org/html/draft-ietf-httpbis-http2-16#section-8.1.2.2
                    // makes a special exception for TE
                    if (!aName.equalsIgnoreCase(HttpHeaderNames.TE) ||
                        aValue.equalsIgnoreCase(HttpHeaderValues.TRAILERS)) {
                        out.add(aName, aValue);
                    }
                }
                return true;
            }
        });
        return out;
    }

    /**
     * A visitor which translates HTTP/2 headers to HTTP/1 headers
     */
    private static final class Http2ToHttpHeaderTranslator implements BinaryHeaders.EntryVisitor {
        /**
         * Translations from HTTP/2 header name to the HTTP/1.x equivalent.
         */
        private static final Map<ByteString, ByteString>
            REQUEST_HEADER_TRANSLATIONS = new HashMap<ByteString, ByteString>();
        private static final Map<ByteString, ByteString>
            RESPONSE_HEADER_TRANSLATIONS = new HashMap<ByteString, ByteString>();
        static {
            RESPONSE_HEADER_TRANSLATIONS.put(Http2Headers.PseudoHeaderName.AUTHORITY.value(),
                            ExtensionHeaderNames.AUTHORITY.text());
            RESPONSE_HEADER_TRANSLATIONS.put(Http2Headers.PseudoHeaderName.SCHEME.value(),
                            ExtensionHeaderNames.SCHEME.text());
            REQUEST_HEADER_TRANSLATIONS.putAll(RESPONSE_HEADER_TRANSLATIONS);
            RESPONSE_HEADER_TRANSLATIONS.put(Http2Headers.PseudoHeaderName.PATH.value(),
                            ExtensionHeaderNames.PATH.text());
        }

        private final int streamId;
        private final HttpHeaders output;
        private final Map<ByteString, ByteString> translations;

        /**
         * Create a new instance
         *
         * @param output The HTTP/1.x headers object to store the results of the translation
         * @param request if {@code true}, translates headers using the request translation map. Otherwise uses the
         *        response translation map.
         */
        Http2ToHttpHeaderTranslator(int streamId, HttpHeaders output, boolean request) {
            this.streamId = streamId;
            this.output = output;
            translations = request ? REQUEST_HEADER_TRANSLATIONS : RESPONSE_HEADER_TRANSLATIONS;
        }

        @Override
        public boolean visit(Entry<ByteString, ByteString> entry) throws Http2Exception {
            final ByteString name = entry.getKey();
            final ByteString value = entry.getValue();
            ByteString translatedName = translations.get(name);
            if (translatedName != null || !Http2Headers.PseudoHeaderName.isPseudoHeader(name)) {
                if (translatedName == null) {
                    translatedName = name;
                }

                // http://tools.ietf.org/html/draft-ietf-httpbis-http2-16#section-8.1.2.3
                // All headers that start with ':' are only valid in HTTP/2 context
                if (translatedName.isEmpty() || translatedName.byteAt(0) == ':') {
                    throw streamError(streamId, PROTOCOL_ERROR,
                            "Invalid HTTP/2 header '%s' encountered in translation to HTTP/1.x", translatedName);
                } else {
                    output.add(new AsciiString(translatedName.array(), false), new AsciiString(value.array(), false));
                }
            }
            return true;
        }
    }
}
