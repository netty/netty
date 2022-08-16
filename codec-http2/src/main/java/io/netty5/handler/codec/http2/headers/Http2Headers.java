/*
 * Copyright 2022 The Netty Project
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
package io.netty5.handler.codec.http2.headers;

import io.netty5.handler.codec.http.headers.HttpHeaders;
import io.netty5.util.AsciiString;

/**
 * HTTP/2 headers. This works similar to {@link HttpHeaders} with the following HTTP/2 specific modifications:
 * <ul>
 *     <li>Pseudo-headers always come before all other headers when iterated.</li>
 *     <li>Header field names are always lower-case.</li>
 * </ul>
 */
public interface Http2Headers extends HttpHeaders {
    /**
     * HTTP/2 pseudo-headers names.
     */
    enum PseudoHeaderName {
        /**
         * {@code :method}.
         */
        METHOD(":method", true),

        /**
         * {@code :scheme}.
         */
        SCHEME(":scheme", true),

        /**
         * {@code :authority}.
         */
        AUTHORITY(":authority", true),

        /**
         * {@code :path}.
         */
        PATH(":path", true),

        /**
         * {@code :status}.
         */
        STATUS(":status", false),

        /**
         * {@code :protocol}, as defined in <a href="https://datatracker.ietf.org/doc/rfc8441/">RFC 8441,
         * Bootstrapping WebSockets with HTTP/2</a>.
         */
        PROTOCOL(":protocol", true);

        private static final char PSEUDO_HEADER_PREFIX = ':';
        private static final byte PSEUDO_HEADER_PREFIX_BYTE = (byte) PSEUDO_HEADER_PREFIX;

        private final AsciiString value;
        private final boolean requestOnly;

        PseudoHeaderName(String value, boolean requestOnly) {
            this.value = AsciiString.cached(value);
            this.requestOnly = requestOnly;
        }

        public AsciiString value() {
            // Return a slice so that the buffer gets its own reader index.
            return value;
        }

        /**
         * Indicates whether the specified header follows the pseudo-header format (begins with ':' character)
         *
         * @return {@code true} if the header follow the pseudo-header format
         */
        public static boolean hasPseudoHeaderFormat(CharSequence headerName) {
            if (headerName instanceof AsciiString) {
                final AsciiString asciiHeaderName = (AsciiString) headerName;
                return !asciiHeaderName.isEmpty() && asciiHeaderName.byteAt(0) == PSEUDO_HEADER_PREFIX_BYTE;
            } else {
                return headerName.length() > 0 && headerName.charAt(0) == PSEUDO_HEADER_PREFIX;
            }
        }

        /**
         * Indicates whether the given header name is a valid HTTP/2 pseudo header.
         */
        public static boolean isPseudoHeader(CharSequence header) {
            return getPseudoHeader(header) != null;
        }

        /**
         * Returns the {@link PseudoHeaderName} corresponding to the specified header name.
         *
         * @return corresponding {@link PseudoHeaderName} if any, {@code null} otherwise.
         */
        public static PseudoHeaderName getPseudoHeader(CharSequence header) {
            if (header.length() < 5 || header.charAt(0) != ':') {
                return null;
            }
            // The second character is a perfect discriminant. Here in alphabetical order:
            //   :path => a
            //   :scheme => c
            //   :method => e
            //   :protocol => r
            //   :status => t
            //   :authority => u
            //     ^
            switch (header.charAt(2)) {
            case 'a':
                if (AsciiString.contentEqualsIgnoreCase(PATH.value, header)) {
                    return PATH;
                }
                break;
            case 'c':
                if (AsciiString.contentEqualsIgnoreCase(SCHEME.value, header)) {
                    return SCHEME;
                }
                break;
            case 'e':
                if (AsciiString.contentEqualsIgnoreCase(METHOD.value, header)) {
                    return METHOD;
                }
                break;
            case 'r':
                if (AsciiString.contentEqualsIgnoreCase(PROTOCOL.value, header)) {
                    return PROTOCOL;
                }
                break;
            case 't':
                if (AsciiString.contentEqualsIgnoreCase(STATUS.value, header)) {
                    return STATUS;
                }
                break;
            case 'u':
                if (AsciiString.contentEqualsIgnoreCase(AUTHORITY.value, header)) {
                    return AUTHORITY;
                }
                break;
            }
            return null;
        }

        /**
         * Indicates whether the pseudo-header is to be used in a request context.
         *
         * @return {@code true} if the pseudo-header is to be used in a request context
         */
        public boolean isRequestOnly() {
            return requestOnly;
        }
    }
    /**
     * Create a header instance with default size hint, and all validation checks turned on.
     *
     * @return A new empty header instance.
     */
    static Http2Headers newHeaders() {
        return newHeaders(16, true, true, true);
    }

    /**
     * Create a header instance with the given size hint, and the given validation checks turned on.
     *
     * @return A new empty header instance with the given configuration.
     */
    static Http2Headers newHeaders(int sizeHint, boolean checkNames, boolean checkCookies, boolean checkValues) {
        return new DefaultHttp2Headers(sizeHint, checkNames, checkCookies, checkValues);
    }

    /**
     * Sets the {@link PseudoHeaderName#METHOD} header
     */
    Http2Headers method(CharSequence value);

    /**
     * Sets the {@link PseudoHeaderName#SCHEME} header
     */
    Http2Headers scheme(CharSequence value);

    /**
     * Sets the {@link PseudoHeaderName#AUTHORITY} header
     */
    Http2Headers authority(CharSequence value);

    /**
     * Sets the {@link PseudoHeaderName#PATH} header
     */
    Http2Headers path(CharSequence value);

    /**
     * Sets the {@link PseudoHeaderName#STATUS} header
     */
    Http2Headers status(CharSequence value);

    /**
     * Gets the {@link PseudoHeaderName#METHOD} header or {@code null} if there is no such header
     */
    CharSequence method();

    /**
     * Gets the {@link PseudoHeaderName#SCHEME} header or {@code null} if there is no such header
     */
    CharSequence scheme();

    /**
     * Gets the {@link PseudoHeaderName#AUTHORITY} header or {@code null} if there is no such header
     */
    CharSequence authority();

    /**
     * Gets the {@link PseudoHeaderName#PATH} header or {@code null} if there is no such header
     */
    CharSequence path();

    /**
     * Gets the {@link PseudoHeaderName#STATUS} header or {@code null} if there is no such header
     */
    CharSequence status();
}
