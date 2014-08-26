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
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;

/**
 * Provides utility methods and constants for the HTTP/2 to HTTP conversion
 */
public final class HttpUtil {
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
    public static final class ExtensionHeaders {
        public static final class Names {
            private Names() { }

            /**
             * HTTP extension header which will identify the stream id
             * from the HTTP/2 event(s) responsible for generating a {@code HttpObject}
             * <p>
             * {@code "X-HTTP2-Stream-ID"}
             */
            public static final AsciiString STREAM_ID = new AsciiString("X-HTTP2-Stream-ID");
            /**
             * HTTP extension header which will identify the authority pseudo header
             * from the HTTP/2 event(s) responsible for generating a {@code HttpObject}
             * <p>
             * {@code "X-HTTP2-Authority"}
             */
            public static final AsciiString AUTHORITY = new AsciiString("X-HTTP2-Authority");
            /**
             * HTTP extension header which will identify the scheme pseudo header
             * from the HTTP/2 event(s) responsible for generating a {@code HttpObject}
             * <p>
             * {@code "X-HTTP2-Scheme"}
             */
            public static final AsciiString SCHEME = new AsciiString("X-HTTP2-Scheme");
            /**
             * HTTP extension header which will identify the path pseudo header
             * from the HTTP/2 event(s) responsible for generating a {@code HttpObject}
             * <p>
             * {@code "X-HTTP2-Path"}
             */
            public static final AsciiString PATH = new AsciiString("X-HTTP2-Path");
            /**
             * HTTP extension header which will identify the stream id used to create this stream
             * in a HTTP/2 push promise frame
             * <p>
             * {@code "X-HTTP2-Stream-Promise-ID"}
             */
            public static final AsciiString STREAM_PROMISE_ID = new AsciiString("X-HTTP2-Stream-Promise-ID");
            /**
             * HTTP extension header which will identify the stream id which this stream is dependent on.
             * This stream will be a child node of the stream id associated with this header value.
             * <p>
             * {@code "X-HTTP2-Stream-Dependency-ID"}
             */
            public static final AsciiString STREAM_DEPENDENCY_ID = new AsciiString("X-HTTP2-Stream-Dependency-ID");
            /**
             * HTTP extension header which will identify the weight
             * (if non-default and the priority is not on the default stream) of the associated HTTP/2 stream
             * responsible responsible for generating a {@code HttpObject}
             * <p>
             * {@code "X-HTTP2-Stream-Weight"}
             */
            public static final AsciiString STREAM_WEIGHT = new AsciiString("X-HTTP2-Stream-Weight");
        }
    }

    /**
     * Apply HTTP/2 rules while translating status code to {@link HttpResponseStatus}
     *
     * @param status The status from an HTTP/2 frame
     * @return The HTTP/1.x status
     * @throws Http2Exception If there is a problem translating from HTTP/2 to HTTP/1.x
     */
    public static HttpResponseStatus parseStatus(String status) throws Http2Exception {
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
}
