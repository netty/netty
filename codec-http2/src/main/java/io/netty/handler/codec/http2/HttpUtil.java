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

import io.netty.handler.codec.AsciiString;
import io.netty.handler.codec.BinaryHeaders;
import io.netty.handler.codec.TextHeaderProcessor;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpMessage;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderUtil;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Provides utility methods and constants for the HTTP/2 to HTTP conversion
 */
@SuppressWarnings("deprecation")
public final class HttpUtil {
    /**
     * The set of headers that should not be directly copied when converting headers from HTTP to HTTP/2.
     */
    private static final Set<CharSequence> HTTP_TO_HTTP2_HEADER_BLACKLIST = new HashSet<CharSequence>();
    static {
        HTTP_TO_HTTP2_HEADER_BLACKLIST.add(HttpHeaders.Names.CONNECTION.toLowerCase());
        HTTP_TO_HTTP2_HEADER_BLACKLIST.add(HttpHeaders.Names.KEEP_ALIVE.toLowerCase());
        HTTP_TO_HTTP2_HEADER_BLACKLIST.add(HttpHeaders.Names.PROXY_CONNECTION.toLowerCase());
        HTTP_TO_HTTP2_HEADER_BLACKLIST.add(HttpHeaders.Names.TRANSFER_ENCODING.toLowerCase());
        HTTP_TO_HTTP2_HEADER_BLACKLIST.add(HttpHeaders.Names.HOST.toLowerCase());
        // These are already defined as lower-case.
        HTTP_TO_HTTP2_HEADER_BLACKLIST.add(ExtensionHeaderNames.STREAM_ID.text());
        HTTP_TO_HTTP2_HEADER_BLACKLIST.add(ExtensionHeaderNames.AUTHORITY.text());
        HTTP_TO_HTTP2_HEADER_BLACKLIST.add(ExtensionHeaderNames.SCHEME.text());
        HTTP_TO_HTTP2_HEADER_BLACKLIST.add(ExtensionHeaderNames.PATH.text());
    }

    /**
     * This will be the method used for {@link HttpRequest} objects generated
     * out of the HTTP message flow defined in
     * <a href="http://tools.ietf.org/html/draft-ietf-httpbis-http2-14#section-8.1.">HTTP/2 Spec Message Flow</a>
     */
    public static final HttpMethod OUT_OF_MESSAGE_SEQUENCE_METHOD = HttpMethod.OPTIONS;

    /**
     * This will be the path used for {@link HttpRequest} objects generated
     * out of the HTTP message flow defined in
     * <a href="http://tools.ietf.org/html/draft-ietf-httpbis-http2-14#section-8.1.">HTTP/2 Spec Message Flow</a>
     */
    public static final String OUT_OF_MESSAGE_SEQUENCE_PATH = "";

    /**
     * This will be the status code used for {@link HttpResponse} objects generated
     * out of the HTTP message flow defined in
     * <a href="http://tools.ietf.org/html/draft-ietf-httpbis-http2-14#section-8.1.">HTTP/2 Spec Message Flow</a>
     */
    public static final HttpResponseStatus OUT_OF_MESSAGE_SEQUENCE_RETURN_CODE = HttpResponseStatus.OK;

    private HttpUtil() { }

    /**
     * Provides the HTTP header extensions used to carry HTTP/2 information in HTTP objects
     */
    public enum ExtensionHeaderNames {
        /**
         * HTTP extension header which will identify the stream id from the HTTP/2 event(s)
         * responsible for generating a {@code HttpObject}
         * <p>
         * {@code "x-http2-stream-id"}
         */
        STREAM_ID("x-http2-stream-id"),

        /**
         * HTTP extension header which will identify the authority pseudo header from the HTTP/2
         * event(s) responsible for generating a {@code HttpObject}
         * <p>
         * {@code "x-http2-authority"}
         */
        AUTHORITY("x-http2-authority"),
        /**
         * HTTP extension header which will identify the scheme pseudo header from the HTTP/2
         * event(s) responsible for generating a {@code HttpObject}
         * <p>
         * {@code "x-http2-scheme"}
         */
        SCHEME("x-http2-scheme"),
        /**
         * HTTP extension header which will identify the path pseudo header from the HTTP/2 event(s)
         * responsible for generating a {@code HttpObject}
         * <p>
         * {@code "x-http2-path"}
         */
        PATH("x-http2-path"),
        /**
         * HTTP extension header which will identify the stream id used to create this stream in a
         * HTTP/2 push promise frame
         * <p>
         * {@code "x-http2-stream-promise-id"}
         */
        STREAM_PROMISE_ID("x-http2-stream-promise-id"),
        /**
         * HTTP extension header which will identify the stream id which this stream is dependent
         * on. This stream will be a child node of the stream id associated with this header value.
         * <p>
         * {@code "x-http2-stream-dependency-id"}
         */
        STREAM_DEPENDENCY_ID("x-http2-stream-dependency-id"),
        /**
         * HTTP extension header which will identify the weight (if non-default and the priority is
         * not on the default stream) of the associated HTTP/2 stream responsible responsible for
         * generating a {@code HttpObject}
         * <p>
         * {@code "x-http2-stream-weight"}
         */
        STREAM_WEIGHT("x-http2-stream-weight");

        private final AsciiString text;

        private ExtensionHeaderNames(String text) {
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
    public static HttpResponseStatus parseStatus(AsciiString status) throws Http2Exception {
        HttpResponseStatus result = null;
        try {
            result = HttpResponseStatus.parseLine(status);
            if (result == HttpResponseStatus.SWITCHING_PROTOCOLS) {
                throw Http2Exception.protocolError("Invalid HTTP/2 status code '%d'", result.code());
            }
        } catch (Http2Exception e) {
            throw e;
        } catch (Exception e) {
            throw Http2Exception.protocolError(
                            "Unrecognized HTTP status code '%s' encountered in translation to HTTP/1.x", status);
        }
        return result;
    }

    /**
     * Create a new object to contain the response data
     *
     * @param streamId The stream associated with the response
     * @param http2Headers The initial set of HTTP/2 headers to create the response with
     * @param validateHttpHeaders
     * <ul>
     * <li>{@code true} to validate HTTP headers in the http-codec</li>
     * <li>{@code false} not to validate HTTP headers in the http-codec</li>
     * </ul>
     * @return A new response object which represents headers/data
     * @throws Http2Exception see {@link #addHttp2ToHttpHeaders(int, Http2Headers, FullHttpMessage, Map)}
     */
    public static FullHttpResponse toHttpResponse(int streamId, Http2Headers http2Headers,
            boolean validateHttpHeaders) throws Http2Exception {
        HttpResponseStatus status = parseStatus(http2Headers.status());
        // HTTP/2 does not define a way to carry the version or reason phrase that is included in an
        // HTTP/1.1 status line.
        FullHttpResponse msg =
                new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, validateHttpHeaders);
        addHttp2ToHttpHeaders(streamId, http2Headers, msg, false);
        return msg;
    }

    /**
     * Create a new object to contain the request data
     *
     * @param streamId The stream associated with the request
     * @param http2Headers The initial set of HTTP/2 headers to create the request with
     * @param validateHttpHeaders
     * <ul>
     * <li>{@code true} to validate HTTP headers in the http-codec</li>
     * <li>{@code false} not to validate HTTP headers in the http-codec</li>
     * </ul>
     * @return A new request object which represents headers/data
     * @throws Http2Exception see {@link #addHttp2ToHttpHeaders(int, Http2Headers, FullHttpMessage, Map)}
     */
    public static FullHttpRequest toHttpRequest(int streamId, Http2Headers http2Headers, boolean validateHttpHeaders)
                    throws Http2Exception {
        // HTTP/2 does not define a way to carry the version identifier that is
        // included in the HTTP/1.1 request line.
        FullHttpRequest msg =
                new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.valueOf(http2Headers
                        .method().toString()), http2Headers.path().toString(), validateHttpHeaders);
        addHttp2ToHttpHeaders(streamId, http2Headers, msg, false);
        return msg;
    }

    /**
     * Translate and add HTTP/2 headers to HTTP/1.x headers
     *
     * @param streamId The stream associated with {@code sourceHeaders}
     * @param sourceHeaders The HTTP/2 headers to convert
     * @param destinationMessage The object which will contain the resulting HTTP/1.x headers
     * @param addToTrailer {@code true} to add to trailing headers. {@code false} to add to initial
     *            headers.
     * @throws Http2Exception If not all HTTP/2 headers can be translated to HTTP/1.x
     */
    public static void addHttp2ToHttpHeaders(int streamId, Http2Headers sourceHeaders,
                    FullHttpMessage destinationMessage, boolean addToTrailer)
                            throws Http2Exception {
        HttpHeaders headers = addToTrailer ? destinationMessage.trailingHeaders() : destinationMessage.headers();
        boolean request = destinationMessage instanceof HttpRequest;
        Http2ToHttpHeaderTranslator visitor = new Http2ToHttpHeaderTranslator(headers, request);
        sourceHeaders.forEachEntry(visitor);
        if (visitor.cause() != null) {
            throw visitor.cause();
        }

        headers.remove(HttpHeaders.Names.TRANSFER_ENCODING);
        headers.remove(HttpHeaders.Names.TRAILER);
        if (!addToTrailer) {
            headers.set(ExtensionHeaderNames.STREAM_ID.text(), streamId);
            HttpHeaderUtil.setKeepAlive(destinationMessage, true);
        }
    }

    /**
     * Converts the given HTTP/1.x headers into HTTP/2 headers.
     */
    public static Http2Headers toHttp2Headers(FullHttpMessage in) {
        final Http2Headers out = new DefaultHttp2Headers();
        HttpHeaders inHeaders = in.headers();
        if (in instanceof HttpRequest) {
            HttpRequest request = (HttpRequest) in;
            out.path(new AsciiString(request.uri()));
            out.method(new AsciiString(request.method().toString()));

            String value = inHeaders.get(HttpHeaders.Names.HOST);
            if (value != null) {
                URI hostUri = URI.create(value);
                // The authority MUST NOT include the deprecated "userinfo" subcomponent
                value = hostUri.getAuthority();
                if (value != null) {
                    out.authority(new AsciiString(value.replaceFirst("^.*@", "")));
                }
                value = hostUri.getScheme();
                if (value != null) {
                    out.scheme(new AsciiString(value));
                }
            }

            // Consume the Authority extension header if present
            value = inHeaders.get(ExtensionHeaderNames.AUTHORITY.text());
            if (value != null) {
                out.authority(new AsciiString(value));
            }

            // Consume the Scheme extension header if present
            value = inHeaders.get(ExtensionHeaderNames.SCHEME.text());
            if (value != null) {
                out.scheme(new AsciiString(value));
            }
        } else if (in instanceof HttpResponse) {
            HttpResponse response = (HttpResponse) in;
            out.status(new AsciiString(Integer.toString(response.status().code())));
        }

        // Add the HTTP headers which have not been consumed above
        inHeaders.forEachEntry(new TextHeaderProcessor() {
            @Override
            public boolean process(CharSequence name, CharSequence value) throws Exception {
                AsciiString aName =  AsciiString.of(name);
                if (!HTTP_TO_HTTP2_HEADER_BLACKLIST.contains(aName.toLowerCase())) {
                    AsciiString aValue = AsciiString.of(value);
                    out.add(aName, aValue);
                }
                return true;
            }
        });
        return out;
    }

    /**
     * A visitor which translates HTTP/2 headers to HTTP/1 headers
     */
    private static final class Http2ToHttpHeaderTranslator implements BinaryHeaders.BinaryHeaderVisitor {
        /**
         * Translations from HTTP/2 header name to the HTTP/1.x equivalent.
         */
        private static final Map<AsciiString, String> REQUEST_HEADER_TRANSLATIONS =
                new HashMap<AsciiString, String>();
        private static final Map<AsciiString, String> RESPONSE_HEADER_TRANSLATIONS =
                new HashMap<AsciiString, String>();
        static {
            RESPONSE_HEADER_TRANSLATIONS.put(Http2Headers.PseudoHeaderName.AUTHORITY.value(),
                    ExtensionHeaderNames.AUTHORITY.text().toString());
            RESPONSE_HEADER_TRANSLATIONS.put(Http2Headers.PseudoHeaderName.SCHEME.value(),
                    ExtensionHeaderNames.SCHEME.text().toString());
            REQUEST_HEADER_TRANSLATIONS.putAll(RESPONSE_HEADER_TRANSLATIONS);
            RESPONSE_HEADER_TRANSLATIONS.put(Http2Headers.PseudoHeaderName.PATH.value(),
                    ExtensionHeaderNames.PATH.text().toString());
        }

        private final HttpHeaders output;
        private final Map<AsciiString, String> translations;
        private Http2Exception e;

        /**
         * Create a new instance
         *
         * @param output The HTTP/1.x headers object to store the results of the translation
         * @param request if {@code true}, translates headers using the request translation map.
         *            Otherwise uses the response translation map.
         */
        public Http2ToHttpHeaderTranslator(HttpHeaders output, boolean request) {
            this.output = output;
            translations = request? REQUEST_HEADER_TRANSLATIONS : RESPONSE_HEADER_TRANSLATIONS;
        }

        @Override
        public boolean visit(AsciiString name, AsciiString value) {
            String translatedName = translations.get(name);
            if (translatedName != null || !Http2Headers.PseudoHeaderName.isPseudoHeader(name)) {
                if (translatedName == null) {
                    translatedName = name.toString();
                }

                // http://tools.ietf.org/html/draft-ietf-httpbis-http2-14#section-8.1.2.3
                // All headers that start with ':' are only valid in HTTP/2 context
                if (translatedName.isEmpty() || translatedName.charAt(0) == ':') {
                    e = Http2Exception
                            .protocolError("Unknown HTTP/2 header '%s' encountered in translation to HTTP/1.x",
                                            translatedName);
                    return false;
                } else {
                    output.add(translatedName, value.toString());
                }
            }
            return true;
        }

        /**
         * Get any exceptions encountered while translating HTTP/2 headers to HTTP/1.x headers
         *
         * @return
         * <ul>
         * <li>{@code null} if no exceptions where encountered</li>
         * <li>Otherwise an exception describing what went wrong</li>
         * </ul>
         */
        public Http2Exception cause() {
            return e;
        }
    }
}
