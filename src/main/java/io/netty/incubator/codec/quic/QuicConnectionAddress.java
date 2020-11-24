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
package io.netty.incubator.codec.quic;

import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * A {@link QuicConnectionAddress} that can be used to connect too.
 */
public final class QuicConnectionAddress extends SocketAddress {

    // Accessed by QuicheQuicheChannel
    final ByteBuffer connId;

    /**
     * Create a new instance
     *
     * @param connId the connection id to use.
     */
    public QuicConnectionAddress(byte[] connId) {
        this(ByteBuffer.wrap(connId.clone()));
    }

    /**
     * Create a new instance
     *
     * @param connId the connection id to use.
     */
    public QuicConnectionAddress(ByteBuffer connId) {
        Quic.ensureAvailability();
        if (connId.remaining() > Quiche.QUICHE_MAX_CONN_ID_LEN) {
            throw new IllegalArgumentException("Connection ID can only be of max length "
                    + Quiche.QUICHE_MAX_CONN_ID_LEN);
        }
        this.connId = connId;
    }

    @Override
    public String toString() {
        return "QuicConnectionAddress{" +
                "connId=" + connId + '}';
    }

    @Override
    public int hashCode() {
        return Objects.hash(connId);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof QuicConnectionAddress)) {
            return false;
        }
        QuicConnectionAddress address = (QuicConnectionAddress) obj;
        return connId.equals(address.connId);
    }

    /**
     * Return a random generated {@link QuicConnectionAddress} that can be used to connect a {@link QuicChannel}
     */
    public static QuicConnectionAddress random() {
        return new QuicConnectionAddress(QuicConnectionIdGenerator.randomGenerator().newId());
    }
}
