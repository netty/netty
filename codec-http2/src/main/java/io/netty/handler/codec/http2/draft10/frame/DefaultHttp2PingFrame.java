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

import static io.netty.handler.codec.http2.draft10.frame.Http2FrameCodecUtil.PING_FRAME_PAYLOAD_LENGTH;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.DefaultByteBufHolder;

/**
 * Default implementation of {@link Http2PingFrame}.
 */
public final class DefaultHttp2PingFrame extends DefaultByteBufHolder implements Http2PingFrame {

    private final boolean ack;

    private DefaultHttp2PingFrame(Builder builder) {
        super(builder.data);
        ack = builder.ack;
    }

    @Override
    public boolean isAck() {
        return ack;
    }

    @Override
    public DefaultHttp2PingFrame copy() {
        return new Builder().setAck(ack).setData(content().copy()).build();
    }

    @Override
    public DefaultHttp2PingFrame duplicate() {
        return new Builder().setAck(ack).setData(content().duplicate()).build();
    }

    @Override
    public DefaultHttp2PingFrame retain() {
        super.retain();
        return this;
    }

    @Override
    public DefaultHttp2PingFrame retain(int increment) {
        super.retain(increment);
        return this;
    }

    @Override
    public DefaultHttp2PingFrame touch() {
        super.touch();
        return this;
    }

    @Override
    public DefaultHttp2PingFrame touch(Object hint) {
        super.touch(hint);
        return this;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = content().hashCode();
        result = prime * result + (ack ? 1231 : 1237);
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
        DefaultHttp2PingFrame other = (DefaultHttp2PingFrame) obj;
        if (ack != other.ack) {
            return false;
        }
        if (!content().equals(other.content())) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(getClass().getSimpleName()).append("[");
        builder.append("ack=").append(ack);
        builder.append("]");
        return builder.toString();
    }

    /**
     * Builds instances of {@link DefaultHttp2PingFrame}.
     */
    public static class Builder {
        private boolean ack;
        private ByteBuf data;

        /**
         * Sets the data for this ping. This buffer will be retained when the frame is built.
         */
        public Builder setData(ByteBuf data) {
            if (data == null) {
                throw new IllegalArgumentException("data must not be null.");
            }
            if (data.readableBytes() != PING_FRAME_PAYLOAD_LENGTH) {
                throw new IllegalArgumentException(String.format(
                        "Incorrect data length for ping.  Expected %d, found %d",
                        PING_FRAME_PAYLOAD_LENGTH, data.readableBytes()));
            }
            this.data = data;
            return this;
        }

        public Builder setAck(boolean ack) {
            this.ack = ack;
            return this;
        }

        public DefaultHttp2PingFrame build() {
            if (data == null) {
                throw new IllegalArgumentException("debug data must be provided");
            }

            return new DefaultHttp2PingFrame(this);
        }
    }
}
