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

import io.netty.util.internal.UnstableApi;

/**
 * Provides utility methods for accessing specific flags as defined by the HTTP/2 spec.
 */
@UnstableApi
public final class Http2Flags {
    public static final short END_STREAM = 0x1;
    public static final short END_HEADERS = 0x4;
    public static final short ACK = 0x1;
    public static final short PADDED = 0x8;
    public static final short PRIORITY = 0x20;

    private short value;

    public Http2Flags() {
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
        return isFlagSet(END_STREAM);
    }

    /**
     * Determines whether the {@link #END_HEADERS} flag is set. Only applies for HEADERS,
     * PUSH_PROMISE, and CONTINUATION frames.
     */
    public boolean endOfHeaders() {
        return isFlagSet(END_HEADERS);
    }

    /**
     * Determines whether the flag is set indicating the presence of the exclusive, stream
     * dependency, and weight fields in a HEADERS frame.
     */
    public boolean priorityPresent() {
        return isFlagSet(PRIORITY);
    }

    /**
     * Determines whether the flag is set indicating that this frame is an ACK. Only applies for
     * SETTINGS and PING frames.
     */
    public boolean ack() {
        return isFlagSet(ACK);
    }

    /**
     * For frames that include padding, indicates if the {@link #PADDED} field is present. Only
     * applies to DATA, HEADERS, PUSH_PROMISE and CONTINUATION frames.
     */
    public boolean paddingPresent() {
        return isFlagSet(PADDED);
    }

    /**
     * Gets the number of bytes expected for the priority fields of the payload. This is determined
     * by the {@link #priorityPresent()} flag.
     */
    public int getNumPriorityBytes() {
        return priorityPresent() ? 5 : 0;
    }

    /**
     * Gets the length in bytes of the padding presence field expected in the payload. This is
     * determined by the {@link #paddingPresent()} flag.
     */
    public int getPaddingPresenceFieldLength() {
        return paddingPresent() ? 1 : 0;
    }

    /**
     * Sets the {@link #END_STREAM} flag.
     */
    public Http2Flags endOfStream(boolean endOfStream) {
        return setFlag(endOfStream, END_STREAM);
    }

    /**
     * Sets the {@link #END_HEADERS} flag.
     */
    public Http2Flags endOfHeaders(boolean endOfHeaders) {
        return setFlag(endOfHeaders, END_HEADERS);
    }

    /**
     * Sets the {@link #PRIORITY} flag.
     */
    public Http2Flags priorityPresent(boolean priorityPresent) {
        return setFlag(priorityPresent, PRIORITY);
    }

    /**
     * Sets the {@link #PADDED} flag.
     */
    public Http2Flags paddingPresent(boolean paddingPresent) {
        return setFlag(paddingPresent, PADDED);
    }

    /**
     * Sets the {@link #ACK} flag.
     */
    public Http2Flags ack(boolean ack) {
        return setFlag(ack, ACK);
    }

    /**
     * Generic method to set any flag.
     * @param on if the flag should be enabled or disabled.
     * @param mask the mask that identifies the bit for the flag.
     * @return this instance.
     */
    public Http2Flags setFlag(boolean on, short mask) {
        if (on) {
            value |= mask;
        } else {
            value &= ~mask;
        }
        return this;
    }

    /**
     * Indicates whether or not a particular flag is set.
     * @param mask the mask identifying the bit for the particular flag being tested
     * @return {@code true} if the flag is set
     */
    public boolean isFlagSet(short mask) {
        return (value & mask) != 0;
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
        if (paddingPresent()) {
            builder.append("PADDING_PRESENT,");
        }
        builder.append(')');
        return builder.toString();
    }
}
