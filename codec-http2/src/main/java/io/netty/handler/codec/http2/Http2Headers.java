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

import java.util.HashSet;
import java.util.Set;

/**
 * A collection of headers sent or received via HTTP/2.
 */
public interface Http2Headers extends BinaryHeaders {

    /**
     * HTTP/2 pseudo-headers names.
     */
    public enum PseudoHeaderName {
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
        private static final Set<AsciiString> PSEUDO_HEADERS = new HashSet<AsciiString>();
        static {
            for (PseudoHeaderName pseudoHeader : PseudoHeaderName.values()) {
                PSEUDO_HEADERS.add(pseudoHeader.value());
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
        public static boolean isPseudoHeader(AsciiString header) {
            return PSEUDO_HEADERS.contains(header);
        }
    }

    @Override
    Http2Headers add(AsciiString name, AsciiString value);

    @Override
    Http2Headers add(AsciiString name, Iterable<AsciiString> values);

    @Override
    Http2Headers add(AsciiString name, AsciiString... values);

    @Override
    Http2Headers add(BinaryHeaders headers);

    @Override
    Http2Headers set(AsciiString name, AsciiString value);

    @Override
    Http2Headers set(AsciiString name, Iterable<AsciiString> values);

    @Override
    Http2Headers set(AsciiString name, AsciiString... values);

    @Override
    Http2Headers set(BinaryHeaders headers);

    @Override
    Http2Headers setAll(BinaryHeaders headers);

    @Override
    Http2Headers clear();

    @Override
    Http2Headers forEachEntry(BinaryHeaderVisitor visitor);

    /**
     * Sets the {@link PseudoHeaderName#METHOD} header or {@code null} if there is no such header
     */
    Http2Headers method(AsciiString value);

    /**
     * Sets the {@link PseudoHeaderName#SCHEME} header if there is no such header
     */
    Http2Headers scheme(AsciiString value);

    /**
     * Sets the {@link PseudoHeaderName#AUTHORITY} header or {@code null} if there is no such header
     */
    Http2Headers authority(AsciiString value);

    /**
     * Sets the {@link PseudoHeaderName#PATH} header or {@code null} if there is no such header
     */
    Http2Headers path(AsciiString value);

    /**
     * Sets the {@link PseudoHeaderName#STATUS} header or {@code null} if there is no such header
     */
    Http2Headers status(AsciiString value);

    /**
     * Gets the {@link PseudoHeaderName#METHOD} header or {@code null} if there is no such header
     */
    AsciiString method();

    /**
     * Gets the {@link PseudoHeaderName#SCHEME} header or {@code null} if there is no such header
     */
    AsciiString scheme();

    /**
     * Gets the {@link PseudoHeaderName#AUTHORITY} header or {@code null} if there is no such header
     */
    AsciiString authority();

    /**
     * Gets the {@link PseudoHeaderName#PATH} header or {@code null} if there is no such header
     */
    AsciiString path();

    /**
     * Gets the {@link PseudoHeaderName#STATUS} header or {@code null} if there is no such header
     */
    AsciiString status();
}
