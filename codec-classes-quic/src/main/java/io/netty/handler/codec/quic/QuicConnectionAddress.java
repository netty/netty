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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.util.internal.EmptyArrays;

import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * A {@link QuicConnectionAddress} that can be used to connect too.
 */
public final class QuicConnectionAddress extends SocketAddress {

    static final QuicConnectionAddress NULL_LEN = new QuicConnectionAddress(EmptyArrays.EMPTY_BYTES);

    /**
     * Special {@link QuicConnectionAddress} that should be used when the connection address should be generated
     * and chosen on the fly.
     */
    public static final QuicConnectionAddress EPHEMERAL = new QuicConnectionAddress(null, false);

    private final String toStr;

    private final ByteBuffer connId;

    /**
     * Create a new instance
     *
     * @param connId the connection id to use.
     */
    public QuicConnectionAddress(byte[] connId) {
        this(ByteBuffer.wrap(connId.clone()), true);
    }

    /**
     * Create a new instance
     *
     * @param connId the connection id to use.
     */
    public QuicConnectionAddress(ByteBuffer connId) {
        this(connId.duplicate(), true);
    }

    private QuicConnectionAddress(ByteBuffer connId, boolean validate) {
        Quic.ensureAvailability();
        if (validate && connId.remaining() > Quiche.QUICHE_MAX_CONN_ID_LEN) {
            throw new IllegalArgumentException("Connection ID can only be of max length "
                    + Quiche.QUICHE_MAX_CONN_ID_LEN);
        }
        if (connId == null) {
            this.connId = null;
            toStr = "QuicConnectionAddress{EPHEMERAL}";
        } else {
            this.connId = connId.asReadOnlyBuffer().duplicate();
            ByteBuf buffer = Unpooled.wrappedBuffer(connId);
            try {
                toStr = "QuicConnectionAddress{" +
                        "connId=" + ByteBufUtil.hexDump(buffer) + '}';
            } finally {
                buffer.release();
            }
        }
    }

    @Override
    public String toString() {
        return toStr;
    }

    @Override
    public int hashCode() {
        if (this == EPHEMERAL) {
            return System.identityHashCode(EPHEMERAL);
        }
        return Objects.hash(connId);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof QuicConnectionAddress)) {
            return false;
        }
        QuicConnectionAddress address = (QuicConnectionAddress) obj;
        if (obj == this) {
            return true;
        }
        return connId.equals(address.connId);
    }

    ByteBuffer id() {
        if (connId == null) {
            return ByteBuffer.allocate(0);
        }
        return connId.duplicate();
    }

    /**
     * Return a random generated {@link QuicConnectionAddress} of a given length
     * that can be used to connect a {@link QuicChannel}
     *
     * @param length    the length of the {@link QuicConnectionAddress} to generate.
     * @return          the generated address.
     */
    public static QuicConnectionAddress random(int length) {
        return new QuicConnectionAddress(QuicConnectionIdGenerator.randomGenerator().newId(length));
    }

    /**
     * Return a random generated {@link QuicConnectionAddress} of maximum size
     * that can be used to connect a {@link QuicChannel}
     *
     * @return the generated address.
     */
    public static QuicConnectionAddress random() {
        return random(Quiche.QUICHE_MAX_CONN_ID_LEN);
    }
}
