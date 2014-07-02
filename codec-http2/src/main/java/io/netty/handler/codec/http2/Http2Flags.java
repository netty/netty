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

import io.netty.buffer.ByteBuf;

/**
 * Provides utility methods for accessing specific flags as defined by the HTTP/2 spec.
 */
public class Http2Flags {
    public static final Http2Flags EMPTY = new Http2Flags();
    public static final Http2Flags ACK_ONLY = new Builder().ack(true).build();

    public static final short END_STREAM = 0x1;
    public static final short END_SEGMENT = 0x2;
    public static final short END_HEADERS = 0x4;
    public static final short ACK = 0x1;
    public static final short PAD_LOW = 0x8;
    public static final short PAD_HIGH = 0x10;
    public static final short PRIORITY = 0x20;
    public static final short COMPRESSED = 0x20;

    private final short value;

    private Http2Flags() {
        this((short) 0);
    }

    public Http2Flags(short value) {
        this.value = value;
    }

    /**
     * Gets the underlying flags value.
     */
    public short value() {
        return value;
    }

    /**
     * Determines whether the {@link #END_STREAM} flag is set. Only applies to DATA and HEADERS
     * frames.
     */
    public boolean endOfStream() {
        return endOfStream(value);
    }

    /**
     * Determines whether the {@link #END_SEGMENT} flag is set. Only applies to DATA and HEADERS
     * frames.
     */
    public boolean endOfSegment() {
        return endOfSegment(value);
    }

    /**
     * Determines whether the {@link #END_HEADERS} flag is set. Only applies for HEADERS,
     * PUSH_PROMISE, and CONTINUATION frames.
     */
    public boolean endOfHeaders() {
        return endOfHeaders(value);
    }

    /**
     * Determines whether the flag is set indicating the presence of the exclusive, stream
     * dependency, and weight fields in a HEADERS frame.
     */
    public boolean priorityPresent() {
        return priorityPresent(value);
    }

    /**
     * Determines whether the flag is set indicating that this frame is an ACK. Only applies for
     * SETTINGS and PING frames.
     */
    public boolean ack() {
        return ack(value);
    }

    /**
     * For DATA frames, indicates that the data is compressed using gzip compression.
     */
    public boolean compressed() {
        return compressed(value);
    }

    /**
     * For frames that include padding, indicates if the {@link #PAD_LOW} field is present. Only
     * applies to DATA, HEADERS, PUSH_PROMISE and CONTINUATION frames.
     */
    public boolean padLowPresent() {
        return padLowPresent(value);
    }

    /**
     * For frames that include padding, indicates if the {@link #PAD_HIGH} field is present. Only
     * applies to DATA, HEADERS, PUSH_PROMISE and CONTINUATION frames.
     */
    public boolean padHighPresent() {
        return padHighPresent(value);
    }

    /**
     * Indicates whether the padding flags are set properly. If pad high is set, pad low must also
     * be set.
     */
    public boolean isPaddingLengthValid() {
        return isPaddingLengthValid(value);
    }

    /**
     * Gets the number of bytes expected in the padding length field of the payload. This is
     * determined by the {@link #padHighPresent()} and {@link #padLowPresent()} flags.
     */
    public int getNumPaddingLengthBytes() {
        return getNumPaddingLengthBytes(value);
    }

    /**
     * Gets the number of bytes expected for the priority fields of the payload. This is determined
     * by the {@link #priorityPresent()} flag.
     */
    public int getNumPriorityBytes() {
        return getNumPriorityBytes(value);
    }

    /**
     * Reads the variable-length padding length field from the payload.
     */
    public int readPaddingLength(ByteBuf payload) {
        return readPaddingLength(value, payload);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + value;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }

        return value == ((Http2Flags) obj).value;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("value = ").append(value).append(" (");
        if (ack()) {
            builder.append("ACK,");
        }
        if (endOfHeaders()) {
            builder.append("END_OF_HEADERS,");
        }
        if (endOfStream()) {
            builder.append("END_OF_STREAM,");
        }
        if (priorityPresent()) {
            builder.append("PRIORITY_PRESENT,");
        }
        if (endOfSegment()) {
            builder.append("END_OF_SEGMENT,");
        }
        if (padHighPresent()) {
            builder.append("PAD_HIGH,");
        }
        if (padLowPresent()) {
            builder.append("PAD_LOW,");
        }
        builder.append(')');
        return builder.toString();
    }

    /**
     * Shortcut for creating a new {@link Builder} instance.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Builder for instances of {@link Http2Flags}.
     */
    public static final class Builder {
        private short value;

        /**
         * Sets the {@link #END_STREAM} flag.
         */
        public Builder endOfStream(boolean endOfStream) {
            return setFlag(endOfStream, END_STREAM);
        }

        /**
         * Sets the {@link #END_SEGMENT} flag.
         */
        public Builder endOfSegment(boolean endOfSegment) {
            return setFlag(endOfSegment, END_SEGMENT);
        }

        /**
         * Sets the {@link #END_HEADERS} flag.
         */
        public Builder endOfHeaders(boolean endOfHeaders) {
            return setFlag(endOfHeaders, END_HEADERS);
        }

        /**
         * Sets the {@link #PRIORITY} flag.
         */
        public Builder priorityPresent(boolean priorityPresent) {
            return setFlag(priorityPresent, PRIORITY);
        }

        /**
         * Sets the {@link #ACK} flag.
         */
        public Builder ack(boolean ack) {
            return setFlag(ack, ACK);
        }

        /**
         * Sets the {@link #COMPRESSED} flag.
         */
        public Builder compressed(boolean compressed) {
            return setFlag(compressed, COMPRESSED);
        }

        /**
         * Sets the padding flags in the given flags value as appropriate based on the padding
         * length.
         */
        public Builder setPaddingFlags(int paddingLength) {
            if (paddingLength > 255) {
                value |= PAD_HIGH;
            }
            if (paddingLength > 0) {
                value |= PAD_LOW;
            }
            return this;
        }

        /**
         * Determines whether the {@link #END_STREAM} flag is set. Only applies to DATA and HEADERS
         * frames.
         */
        public boolean endOfStream() {
            return Http2Flags.endOfStream(value);
        }

        /**
         * Determines whether the {@link #END_SEGMENT} flag is set. Only applies to DATA and HEADERS
         * frames.
         */
        public boolean endOfSegment() {
            return Http2Flags.endOfSegment(value);
        }

        /**
         * Determines whether the {@link #END_HEADERS} flag is set. Only applies for HEADERS,
         * PUSH_PROMISE, and CONTINUATION frames.
         */
        public boolean endOfHeaders() {
            return Http2Flags.endOfHeaders(value);
        }

        /**
         * Determines whether the flag is set indicating the presence of the exclusive, stream
         * dependency, and weight fields in a HEADERS frame.
         */
        public boolean priorityPresent() {
            return Http2Flags.priorityPresent(value);
        }

        /**
         * Determines whether the flag is set indicating that this frame is an ACK. Only applies for
         * SETTINGS and PING frames.
         */
        public boolean ack() {
            return Http2Flags.ack(value);
        }

        /**
         * For DATA frames, indicates that the data is compressed using gzip compression.
         */
        public boolean compressed() {
            return Http2Flags.compressed(value);
        }

        /**
         * For frames that include padding, indicates if the {@link #PAD_LOW} field is present. Only
         * applies to DATA, HEADERS, PUSH_PROMISE and CONTINUATION frames.
         */
        public boolean padLowPresent() {
            return Http2Flags.padLowPresent(value);
        }

        /**
         * For frames that include padding, indicates if the {@link #PAD_HIGH} field is present. Only
         * applies to DATA, HEADERS, PUSH_PROMISE and CONTINUATION frames.
         */
        public boolean padHighPresent() {
            return Http2Flags.padHighPresent(value);
        }

        /**
         * Indicates whether the padding flags are set properly. If pad high is set, pad low must also
         * be set.
         */
        public boolean isPaddingLengthValid() {
            return Http2Flags.isPaddingLengthValid(value);
        }

        /**
         * Gets the number of bytes expected in the padding length field of the payload. This is
         * determined by the {@link #padHighPresent()} and {@link #padLowPresent()} flags.
         */
        public int getNumPaddingLengthBytes() {
            return Http2Flags.getNumPaddingLengthBytes(value);
        }

        /**
         * Gets the number of bytes expected for the priority fields of the payload. This is determined
         * by the {@link #priorityPresent()} flag.
         */
        public int getNumPriorityBytes() {
            return Http2Flags.getNumPriorityBytes(value);
        }

        /**
         * Reads the variable-length padding length field from the payload.
         */
        public int readPaddingLength(ByteBuf payload) {
            return Http2Flags.readPaddingLength(value, payload);
        }
        /**
         * Builds a new {@link Http2Flags} instance.
         */
        public Http2Flags build() {
            return new Http2Flags(value);
        }

        private Builder setFlag(boolean on, short mask) {
            if (on) {
                value |= mask;
            } else {
                value &= ~mask;
            }
            return this;
        }
    }

    /**
     * Determines whether the {@link #END_STREAM} flag is set. Only applies to DATA and HEADERS
     * frames.
     */
    public static boolean endOfStream(short value) {
        return isFlagSet(value, END_STREAM);
    }

    /**
     * Determines whether the {@link #END_SEGMENT} flag is set. Only applies to DATA and HEADERS
     * frames.
     */
    public static boolean endOfSegment(short value) {
        return isFlagSet(value, END_SEGMENT);
    }

    /**
     * Determines whether the {@link #END_HEADERS} flag is set. Only applies for HEADERS,
     * PUSH_PROMISE, and CONTINUATION frames.
     */
    public static boolean endOfHeaders(short value) {
        return isFlagSet(value, END_HEADERS);
    }

    /**
     * Determines whether the flag is set indicating the presence of the exclusive, stream
     * dependency, and weight fields in a HEADERS frame.
     */
    public static boolean priorityPresent(short value) {
        return isFlagSet(value, PRIORITY);
    }

    /**
     * Determines whether the flag is set indicating that this frame is an ACK. Only applies for
     * SETTINGS and PING frames.
     */
    public static boolean ack(short value) {
        return isFlagSet(value, ACK);
    }

    /**
     * For DATA frames, indicates that the data is compressed using gzip compression.
     */
    public static boolean compressed(short value) {
        return isFlagSet(value, COMPRESSED);
    }

    /**
     * For frames that include padding, indicates if the {@link #PAD_LOW} field is present. Only
     * applies to DATA, HEADERS, PUSH_PROMISE and CONTINUATION frames.
     */
    public static boolean padLowPresent(short value) {
        return isFlagSet(value, PAD_LOW);
    }

    /**
     * For frames that include padding, indicates if the {@link #PAD_HIGH} field is present. Only
     * applies to DATA, HEADERS, PUSH_PROMISE and CONTINUATION frames.
     */
    public static boolean padHighPresent(short value) {
        return isFlagSet(value, PAD_HIGH);
    }

    /**
     * Indicates whether the padding flags are set properly. If pad high is set, pad low must also
     * be set.
     */
    public static boolean isPaddingLengthValid(short value) {
        return padHighPresent(value) ? padLowPresent(value) : true;
    }

    /**
     * Gets the number of bytes expected in the padding length field of the payload. This is
     * determined by the {@link #padHighPresent()} and {@link #padLowPresent()} flags.
     */
    public static int getNumPaddingLengthBytes(short value) {
        return (padHighPresent(value) ? 1 : 0) + (padLowPresent(value) ? 1 : 0);
    }

    /**
     * Gets the number of bytes expected for the priority fields of the payload. This is determined
     * by the {@link #priorityPresent()} flag.
     */
    public static int getNumPriorityBytes(short value) {
        return priorityPresent(value) ? 5 : 0;
    }

    /**
     * Reads the variable-length padding length field from the payload.
     */
    public static int readPaddingLength(short value, ByteBuf payload) {
        int paddingLength = 0;
        if (padHighPresent(value)) {
            paddingLength += payload.readUnsignedByte() * 256;
        }
        if (padLowPresent(value)) {
            paddingLength += payload.readUnsignedByte();
        }

        return paddingLength;
    }

    private static boolean isFlagSet(short value, short mask) {
        return (value & mask) != 0;
    }
}
