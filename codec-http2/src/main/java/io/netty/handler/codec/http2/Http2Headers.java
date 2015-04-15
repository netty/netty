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

import io.netty.handler.codec.BinaryHeaders;
import io.netty.util.ByteString;
import io.netty.util.CharsetUtil;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;

/**
 * A collection of headers sent or received via HTTP/2.
 */
public interface Http2Headers extends BinaryHeaders {

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

        private final ByteString value;
        private static final Set<ByteString> PSEUDO_HEADERS = new HashSet<ByteString>();
        static {
            for (PseudoHeaderName pseudoHeader : PseudoHeaderName.values()) {
                PSEUDO_HEADERS.add(pseudoHeader.value());
            }
        }

        PseudoHeaderName(String value) {
            this.value = new ByteString(value, CharsetUtil.UTF_8);
        }

        public ByteString value() {
            // Return a slice so that the buffer gets its own reader index.
            return value;
        }

        /**
         * Indicates whether the given header name is a valid HTTP/2 pseudo header.
         */
        public static boolean isPseudoHeader(ByteString header) {
            return PSEUDO_HEADERS.contains(header);
        }
    }

    @Override
    Http2Headers add(ByteString name, ByteString value);

    @Override
    Http2Headers add(ByteString name, Iterable<? extends ByteString> values);

    @Override
    Http2Headers add(ByteString name, ByteString... values);

    @Override
    Http2Headers addObject(ByteString name, Object value);

    @Override
    Http2Headers addObject(ByteString name, Iterable<?> values);

    @Override
    Http2Headers addObject(ByteString name, Object... values);

    @Override
    Http2Headers addBoolean(ByteString name, boolean value);

    @Override
    Http2Headers addByte(ByteString name, byte value);

    @Override
    Http2Headers addChar(ByteString name, char value);

    @Override
    Http2Headers addShort(ByteString name, short value);

    @Override
    Http2Headers addInt(ByteString name, int value);

    @Override
    Http2Headers addLong(ByteString name, long value);

    @Override
    Http2Headers addFloat(ByteString name, float value);

    @Override
    Http2Headers addDouble(ByteString name, double value);

    @Override
    Http2Headers addTimeMillis(ByteString name, long value);

    @Override
    Http2Headers add(BinaryHeaders headers);

    @Override
    Http2Headers set(ByteString name, ByteString value);

    @Override
    Http2Headers set(ByteString name, Iterable<? extends ByteString> values);

    @Override
    Http2Headers set(ByteString name, ByteString... values);

    @Override
    Http2Headers setObject(ByteString name, Object value);

    @Override
    Http2Headers setObject(ByteString name, Iterable<?> values);

    @Override
    Http2Headers setObject(ByteString name, Object... values);

    @Override
    Http2Headers setBoolean(ByteString name, boolean value);

    @Override
    Http2Headers setByte(ByteString name, byte value);

    @Override
    Http2Headers setChar(ByteString name, char value);

    @Override
    Http2Headers setShort(ByteString name, short value);

    @Override
    Http2Headers setInt(ByteString name, int value);

    @Override
    Http2Headers setLong(ByteString name, long value);

    @Override
    Http2Headers setFloat(ByteString name, float value);

    @Override
    Http2Headers setDouble(ByteString name, double value);

    @Override
    Http2Headers setTimeMillis(ByteString name, long value);

    @Override
    Http2Headers set(BinaryHeaders headers);

    @Override
    Http2Headers setAll(BinaryHeaders headers);

    @Override
    Http2Headers clear();

    /**
     * Returns an iterator over all HTTP/2 headers from this instance. The iteration order is as follows:
     *   1. All non-pseudo headers (in no particular order).
     *   2. Headers with multiple values will have their values appear in insertion order.
     */
    @Override
    Iterator<Entry<ByteString, ByteString>> iterator();

    /**
     * Sets the {@link PseudoHeaderName#METHOD} header or {@code null} if there is no such header
     */
    Http2Headers method(ByteString value);

    /**
     * Sets the {@link PseudoHeaderName#SCHEME} header if there is no such header
     */
    Http2Headers scheme(ByteString value);

    /**
     * Sets the {@link PseudoHeaderName#AUTHORITY} header or {@code null} if there is no such header
     */
    Http2Headers authority(ByteString value);

    /**
     * Sets the {@link PseudoHeaderName#PATH} header or {@code null} if there is no such header
     */
    Http2Headers path(ByteString value);

    /**
     * Sets the {@link PseudoHeaderName#STATUS} header or {@code null} if there is no such header
     */
    Http2Headers status(ByteString value);

    /**
     * Gets the {@link PseudoHeaderName#METHOD} header or {@code null} if there is no such header
     */
    ByteString method();

    /**
     * Gets the {@link PseudoHeaderName#SCHEME} header or {@code null} if there is no such header
     */
    ByteString scheme();

    /**
     * Gets the {@link PseudoHeaderName#AUTHORITY} header or {@code null} if there is no such header
     */
    ByteString authority();

    /**
     * Gets the {@link PseudoHeaderName#PATH} header or {@code null} if there is no such header
     */
    ByteString path();

    /**
     * Gets the {@link PseudoHeaderName#STATUS} header or {@code null} if there is no such header
     */
    ByteString status();
}
