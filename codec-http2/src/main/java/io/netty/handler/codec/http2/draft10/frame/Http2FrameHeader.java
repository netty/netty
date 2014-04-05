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


/**
 * Encapsulates the content of an HTTP2 frame header.
 */
public final class Http2FrameHeader {
    private final int payloadLength;
    private final int type;
    private final Http2Flags flags;
    private final int streamId;

    private Http2FrameHeader(Builder builder) {
        payloadLength = builder.payloadLength;
        type = builder.type;
        flags = builder.flags;
        streamId = builder.streamId;
    }

    public int getPayloadLength() {
        return payloadLength;
    }

    public int getType() {
        return type;
    }

    public Http2Flags getFlags() {
        return flags;
    }

    public int getStreamId() {
        return streamId;
    }

    /**
     * Builds instances of {@link Http2FrameHeader}.
     */
    public static class Builder {
        private int payloadLength;
        private int type;
        private Http2Flags flags = new Http2Flags((short) 0);
        private int streamId;

        public Builder setPayloadLength(int payloadLength) {
            this.payloadLength = payloadLength;
            return this;
        }

        public Builder setType(int type) {
            this.type = type;
            return this;
        }

        public Builder setFlags(Http2Flags flags) {
            this.flags = flags;
            return this;
        }

        public Builder setStreamId(int streamId) {
            this.streamId = streamId;
            return this;
        }

        public Http2FrameHeader build() {
            return new Http2FrameHeader(this);
        }
    }
}
