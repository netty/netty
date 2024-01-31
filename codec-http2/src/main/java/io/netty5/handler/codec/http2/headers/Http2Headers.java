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

import io.netty5.handler.codec.http.headers.HttpCookiePair;
import io.netty5.handler.codec.http.headers.HttpHeaders;
import io.netty5.handler.codec.http.headers.HttpSetCookie;
import io.netty5.util.AsciiString;

import java.util.Iterator;

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
                return asciiHeaderName.length() > 0 && asciiHeaderName.byteAt(0) == PSEUDO_HEADER_PREFIX_BYTE;
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
         * Indicates whether the given header name is a valid HTTP/2 pseudo header.
         */
        public static boolean isPseudoHeader(AsciiString header) {
            return getPseudoHeader(header) != null;
        }

        /**
         * Indicates whether the given header name is a valid HTTP/2 pseudo header.
         */
        public static boolean isPseudoHeader(String header) {
            return getPseudoHeader(header) != null;
        }

        /**
         * Returns the {@link PseudoHeaderName} corresponding to the specified header name.
         *
         * @return corresponding {@link PseudoHeaderName} if any, {@code null} otherwise.
         */
        public static PseudoHeaderName getPseudoHeader(CharSequence header) {
            if (header instanceof AsciiString) {
                return getPseudoHeader((AsciiString) header);
            }
            return getPseudoHeaderName(header);
        }

        private static PseudoHeaderName getPseudoHeaderName(CharSequence header) {
            int length = header.length();
            if (length > 0 && header.charAt(0) == PSEUDO_HEADER_PREFIX) {
                switch (length) {
                    case 5:
                        // :path
                        return ":path".contentEquals(header)? PATH : null;
                    case 7:
                        // :method, :scheme, :status
                        if (":method" == header) {
                            return METHOD;
                        }
                        if (":scheme" == header) {
                            return SCHEME;
                        }
                        if (":status" == header) {
                            return STATUS;
                        }
                        if (":method".contentEquals(header)) {
                            return METHOD;
                        }
                        if (":scheme".contentEquals(header)) {
                            return SCHEME;
                        }
                        return ":status".contentEquals(header)? STATUS : null;
                    case 9:
                        // :protocol
                        return ":protocol".contentEquals(header)? PROTOCOL : null;
                    case 10:
                        // :authority
                        return ":authority".contentEquals(header)? AUTHORITY : null;
                }
            }
            return null;
        }

        /**
         * Returns the {@link PseudoHeaderName} corresponding to the specified header name.
         *
         * @return corresponding {@link PseudoHeaderName} if any, {@code null} otherwise.
         */
        public static PseudoHeaderName getPseudoHeader(AsciiString header) {
            int length = header.length();
            if (length > 0 && header.charAt(0) == PSEUDO_HEADER_PREFIX) {
                switch (length) {
                    case 5:
                        // :path
                        return PATH.value().equals(header) ? PATH : null;
                    case 7:
                        if (header == METHOD.value()) {
                            return METHOD;
                        }
                        if (header == SCHEME.value()) {
                            return SCHEME;
                        }
                        if (header == STATUS.value()) {
                            return STATUS;
                        }
                        // :method, :scheme, :status
                        if (METHOD.value().equals(header)) {
                            return METHOD;
                        }
                        if (SCHEME.value().equals(header)) {
                            return SCHEME;
                        }
                        return STATUS.value().equals(header)? STATUS : null;
                    case 9:
                        // :protocol
                        return PROTOCOL.value().equals(header)? PROTOCOL : null;
                    case 10:
                        // :authority
                        return AUTHORITY.value().equals(header)? AUTHORITY : null;
                }
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
     * Create a headers instance that is expected to remain empty.
     *
     * @return A new headers instance that use up as few resources as possible.
     */
    static Http2Headers emptyHeaders() {
        return newHeaders(2, false, false, false);
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
     * Create a headers instance with default size hint, and all validation checks turned on.
     *
     * @param validate {@code true} to validate header names, values, and cookies.
     * @return A new empty headers instance.
     */
    static Http2Headers newHeaders(boolean validate) {
        return newHeaders(16, validate, validate, validate);
    }

    /**
     * Create a header instance with the given size hint, and the given validation checks turned on.
     *
     * @return A new empty header instance with the given configuration.
     */
    static Http2Headers newHeaders(int sizeHint, boolean checkNames, boolean checkCookies, boolean checkValues) {
        return new DefaultHttp2Headers(sizeHint, checkNames, checkCookies, checkValues);
    }

    @Override
    Http2Headers copy();

    @Override
    Http2Headers add(CharSequence name, CharSequence value);

    @Override
    Http2Headers add(CharSequence name, Iterable<? extends CharSequence> values);

    @Override
    default Http2Headers add(CharSequence name, Iterator<? extends CharSequence> valuesItr) {
        HttpHeaders.super.add(name, valuesItr);
        return this;
    }

    @Override
    Http2Headers add(CharSequence name, CharSequence... values);

    @Override
    Http2Headers add(HttpHeaders headers);

    @Override
    Http2Headers set(CharSequence name, CharSequence value);

    @Override
    Http2Headers set(CharSequence name, Iterable<? extends CharSequence> values);

    @Override
    default Http2Headers set(CharSequence name, Iterator<? extends CharSequence> valueItr) {
        HttpHeaders.super.set(name, valueItr);
        return this;
    }

    @Override
    default Http2Headers set(CharSequence name, CharSequence... values) {
        HttpHeaders.super.set(name, values);
        return this;
    }

    @Override
    default Http2Headers set(final HttpHeaders headers) {
        HttpHeaders.super.set(headers);
        return this;
    }

    @Override
    default Http2Headers replace(final HttpHeaders headers) {
        HttpHeaders.super.replace(headers);
        return this;
    }

    @Override
    Http2Headers clear();

    @Override
    Http2Headers addCookie(HttpCookiePair cookie);

    @Override
    default Http2Headers addCookie(final CharSequence name, final CharSequence value) {
        HttpHeaders.super.addCookie(name, value);
        return this;
    }

    @Override
    Http2Headers addSetCookie(HttpSetCookie cookie);

    @Override
    default Http2Headers addSetCookie(final CharSequence name, final CharSequence value) {
        HttpHeaders.super.addSetCookie(name, value);
        return this;
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
