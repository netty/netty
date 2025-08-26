/*
 * Copyright 2020 The Netty Project
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
package io.netty.handler.codec.quic;

import java.net.SocketAddress;
import java.util.Objects;

/**
 * A {@link SocketAddress} for QUIC stream.
 */
public final class QuicStreamAddress extends SocketAddress {

    private final long streamId;

    public QuicStreamAddress(long streamId) {
        this.streamId = streamId;
    }

    /**
     * Return the id of the stream.
     *
     * @return the id.
     */
    public long streamId() {
        return streamId;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof QuicStreamAddress)) {
            return false;
        }
        QuicStreamAddress that = (QuicStreamAddress) o;
        return streamId == that.streamId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(streamId);
    }

    @Override
    public String toString() {
        return "QuicStreamAddress{" +
                "streamId=" + streamId +
                '}';
    }
}
