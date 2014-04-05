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

package io.netty.handler.codec.http2.draft10.frame;

import static io.netty.handler.codec.http2.draft10.frame.Http2FrameCodecUtil.FLAG_ACK;
import static io.netty.handler.codec.http2.draft10.frame.Http2FrameCodecUtil.FLAG_END_HEADERS;
import static io.netty.handler.codec.http2.draft10.frame.Http2FrameCodecUtil.FLAG_END_SEGMENT;
import static io.netty.handler.codec.http2.draft10.frame.Http2FrameCodecUtil.FLAG_END_STREAM;
import static io.netty.handler.codec.http2.draft10.frame.Http2FrameCodecUtil.FLAG_PAD_HIGH;
import static io.netty.handler.codec.http2.draft10.frame.Http2FrameCodecUtil.FLAG_PAD_LOW;
import static io.netty.handler.codec.http2.draft10.frame.Http2FrameCodecUtil.FLAG_PRIORITY;

/**
 * Provides utility methods for accessing specific flags as defined by the HTTP2 spec.
 */
public class Http2Flags {
    private final short value;

    public Http2Flags(short value) {
        this.value = value;
    }

    /**
     * Gets the underlying flags value.
     */
    public short getValue() {
        return value;
    }

    /**
     * Determines whether the end-of-stream flag is set.
     */
    public boolean isEndOfStream() {
        return isSet(FLAG_END_STREAM);
    }

    /**
     * Determines whether the end-of-segment flag is set.
     */
    public boolean isEndOfSegment() {
        return isSet(FLAG_END_SEGMENT);
    }

    /**
     * Determines whether the end-of-headers flag is set.
     */
    public boolean isEndOfHeaders() {
        return isSet(FLAG_END_HEADERS);
    }

    /**
     * Determines whether the flag is set indicating the presence of the priority field in a HEADERS
     * frame.
     */
    public boolean isPriorityPresent() {
        return isSet(FLAG_PRIORITY);
    }

    /**
     * Determines whether the flag is set indicating that this frame is an ACK.
     */
    public boolean isAck() {
        return isSet(FLAG_ACK);
    }

    /**
     * For frames that include padding, indicates if the pad low field is present.
     */
    public boolean isPadLowPresent() {
        return isSet(FLAG_PAD_LOW);
    }

    /**
     * For frames that include padding, indicates if the pad high field is present.
     */
    public boolean isPadHighPresent() {
        return isSet(FLAG_PAD_HIGH);
    }

    /**
     * Indicates whether the padding flags are set properly. If pad high is set, pad low must also be
     * set.
     */
    public boolean isPaddingLengthValid() {
        return isPadHighPresent() ? isPadLowPresent() : true;
    }

    /**
     * Gets the number of bytes expected in the padding length field of the payload. This is
     * determined by the {@link #isPadHighPresent()} and {@link #isPadLowPresent()} flags.
     */
    public int getNumPaddingLengthBytes() {
        return (isPadHighPresent() ? 1 : 0) + (isPadLowPresent() ? 1 : 0);
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
        Http2Flags other = (Http2Flags) obj;
        if (value != other.value) {
            return false;
        }
        return true;
    }

    private boolean isSet(short mask) {
        return (value & mask) != 0;
    }
}
