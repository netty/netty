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

import io.netty.handler.codec.Headers;
import io.netty.util.AsciiString;
import io.netty.util.internal.UnstableApi;

import java.util.Iterator;
import java.util.Map.Entry;

/**
 * A collection of headers sent or received via HTTP/2.
 */
@UnstableApi
public interface Http2Headers extends Headers<CharSequence, CharSequence, Http2Headers> {

    /**
     * HTTP/2 pseudo-headers names.
     */
    enum PseudoHeaderName {
        /**
         * {@code :method}.
         */
        METHOD(":method"),

        /**
         * {@code :scheme}.
         */
        SCHEME(":scheme"),

        /**
         * {@code :authority}.
         */
        AUTHORITY(":authority"),

        /**
         * {@code :path}.
         */
        PATH(":path"),

        /**
         * {@code :status}.
         */
        STATUS(":status");

        private final AsciiString value;
        private static final CharSequenceMap<AsciiString> PSEUDO_HEADERS = new CharSequenceMap<AsciiString>();
        static {
            for (PseudoHeaderName pseudoHeader : PseudoHeaderName.values()) {
                PSEUDO_HEADERS.add(pseudoHeader.value(), AsciiString.EMPTY_STRING);
            }
        }

        PseudoHeaderName(String value) {
            this.value = new AsciiString(value);
        }

        public AsciiString value() {
            // Return a slice so that the buffer gets its own reader index.
            return value;
        }

        /**
         * Indicates whether the given header name is a valid HTTP/2 pseudo header.
         */
        public static boolean isPseudoHeader(CharSequence header) {
            return PSEUDO_HEADERS.contains(header);
        }
    }

    /**
     * Returns an iterator over all HTTP/2 headers. The iteration order is as follows:
     *   1. All pseudo headers (order not specified).
     *   2. All non-pseudo headers (in insertion order).
     */
    @Override
    Iterator<Entry<CharSequence, CharSequence>> iterator();

    /**
     * Sets the {@link PseudoHeaderName#METHOD} header or {@code null} if there is no such header
     */
    Http2Headers method(CharSequence value);

    /**
     * Sets the {@link PseudoHeaderName#SCHEME} header if there is no such header
     */
    Http2Headers scheme(CharSequence value);

    /**
     * Sets the {@link PseudoHeaderName#AUTHORITY} header or {@code null} if there is no such header
     */
    Http2Headers authority(CharSequence value);

    /**
     * Sets the {@link PseudoHeaderName#PATH} header or {@code null} if there is no such header
     */
    Http2Headers path(CharSequence value);

    /**
     * Sets the {@link PseudoHeaderName#STATUS} header or {@code null} if there is no such header
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
