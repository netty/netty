/*
 * Copyright 2018 The Netty Project
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

import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http2.Http2Headers.PseudoHeaderName;
import io.netty.util.AsciiString;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;

import static io.netty.handler.codec.http.HttpHeaderNames.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaderNames.KEEP_ALIVE;
import static io.netty.handler.codec.http.HttpHeaderNames.TE;
import static io.netty.handler.codec.http.HttpHeaderNames.TRANSFER_ENCODING;
import static io.netty.handler.codec.http.HttpHeaderNames.UPGRADE;
import static io.netty.handler.codec.http.HttpHeaderValues.TRAILERS;
import static io.netty.handler.codec.http2.Http2Error.PROTOCOL_ERROR;
import static io.netty.handler.codec.http2.Http2Exception.streamError;
import static io.netty.handler.codec.http2.Http2Headers.PseudoHeaderName.METHOD;
import static io.netty.handler.codec.http2.Http2Headers.PseudoHeaderName.PATH;
import static io.netty.handler.codec.http2.Http2Headers.PseudoHeaderName.SCHEME;
import static io.netty.handler.codec.http2.Http2Headers.PseudoHeaderName.hasPseudoHeaderFormat;

final class Http2HeadersValidator {

    private static final List<AsciiString> connectionSpecificHeaders = Collections.unmodifiableList(
            Arrays.asList(CONNECTION, TRANSFER_ENCODING, KEEP_ALIVE, UPGRADE));

    private Http2HeadersValidator() {
    }

    /**
     * Validates connection-specific headers according to
     * <a href="https://tools.ietf.org/html/rfc7540#section-8.1.2.2">RFC7540, section-8.1.2.2</a>
     */
    static void validateConnectionSpecificHeaders(Http2Headers headers, int streamId) throws Http2Exception {
        for (int i = 0; i < connectionSpecificHeaders.size(); i++) {
            final AsciiString header = connectionSpecificHeaders.get(i);
            if (headers.contains(header)) {
                throw streamError(streamId, PROTOCOL_ERROR,
                                  "Connection-specific headers like [%s] must not be used with HTTP/2.", header);
            }
        }

        final CharSequence teHeader = headers.get(TE);
        if (teHeader != null && !AsciiString.contentEqualsIgnoreCase(teHeader, TRAILERS)) {
            throw streamError(streamId, PROTOCOL_ERROR,
                              "TE header must not contain any value other than \"%s\"", TRAILERS);
        }
    }

    /**
     * Validates response pseudo-header fields
     */
    static void validateResponsePseudoHeaders(Http2Headers headers, int streamId) throws Http2Exception {
        for (Entry<CharSequence, CharSequence> entry : headers) {
            final CharSequence key = entry.getKey();
            if (!hasPseudoHeaderFormat(key)) {
                // We know that pseudo header appears first so we can stop
                // looking once we get to the first non pseudo headers.
                break;
            }

            final PseudoHeaderName pseudoHeader = PseudoHeaderName.getPseudoHeader(key);
            if (pseudoHeader.isRequestOnly()) {
                throw streamError(streamId, PROTOCOL_ERROR,
                                  "Request pseudo-header [%s] is not allowed in a response.", key);
            }
        }
    }

    /**
     * Validates request pseudo-header fields according to
     * <a href="https://tools.ietf.org/html/rfc7540#section-8.1.2.3">RFC7540, section-8.1.2.3</a>
     */
    static void validateRequestPseudoHeaders(Http2Headers headers, int streamId) throws Http2Exception {
        final CharSequence method = headers.get(METHOD.value());
        if (method == null) {
            throw streamError(streamId, PROTOCOL_ERROR,
                              "Mandatory header [:method] is missing.");
        }

        if (HttpMethod.CONNECT.asciiName().contentEqualsIgnoreCase(method)) {
            if (headers.contains(SCHEME.value())) {
                throw streamError(streamId, PROTOCOL_ERROR,
                                  "Header [:scheme] must be omitted when using CONNECT method.");
            }

            if (headers.contains(PATH.value())) {
                throw streamError(streamId, PROTOCOL_ERROR,
                                  "Header [:path] must be omitted when using CONNECT method.");
            }

            if (headers.getAll(METHOD.value()).size() > 1) {
                throw streamError(streamId, PROTOCOL_ERROR,
                                  "Header [:method] should have a unique value.");
            }
        } else {
            final CharSequence path = headers.get(PATH.value());
            if (path != null && path.length() == 0) {
                throw streamError(streamId, PROTOCOL_ERROR, "[:path] header cannot be empty.");
            }

            int methodHeadersCount = 0;
            int pathHeadersCount = 0;
            int schemeHeadersCount = 0;
            for (Entry<CharSequence, CharSequence> entry : headers) {
                final CharSequence key = entry.getKey();
                if (!hasPseudoHeaderFormat(key)) {
                    // We know that pseudo header appears first so we can stop
                    // looking once we get to the first non pseudo headers.
                    break;
                }

                final PseudoHeaderName pseudoHeader = PseudoHeaderName.getPseudoHeader(key);
                if (METHOD.value().contentEquals(key)) {
                    methodHeadersCount++;
                } else if (PATH.value().contentEquals(key)) {
                    pathHeadersCount++;
                } else if (SCHEME.value().contentEquals(key)) {
                    schemeHeadersCount++;
                } else if (!pseudoHeader.isRequestOnly()) {
                    throw streamError(streamId, PROTOCOL_ERROR,
                                      "Response pseudo-header [%s] is not allowed in a request.", key);
                }
            }

            validatePseudoHeaderCount(streamId, methodHeadersCount, METHOD);
            validatePseudoHeaderCount(streamId, pathHeadersCount, PATH);
            validatePseudoHeaderCount(streamId, schemeHeadersCount, SCHEME);
        }
    }

    private static void validatePseudoHeaderCount(int streamId, int valueCount, PseudoHeaderName headerName)
            throws Http2Exception {
        if (valueCount == 0) {
            throw streamError(streamId, PROTOCOL_ERROR,
                              "Mandatory header [%s] is missing.", headerName.value());
        } else if (valueCount > 1) {
            throw streamError(streamId, PROTOCOL_ERROR,
                              "Header [%s] should have a unique value.", headerName.value());
        }
    }
}
