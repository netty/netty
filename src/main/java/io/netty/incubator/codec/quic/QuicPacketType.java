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

/**
 * The type of the
 * <a href="https://www.ietf.org/archive/id/draft-ietf-quic-transport-32.html#name-packets-and-frames">QUIC packet</a>.
 */
public enum QuicPacketType {
    /**
     * Initial packet.
     */
    INITIAL((byte) 1),

    /**
     * Retry packet.
     */
    RETRY((byte) 2),

    /**
     * Handshake packet.
     */
    HANDSHAKE((byte) 3),

    /**
     * 0-RTT packet.
     */
    ZERO_RTT((byte) 4),

    /**
     * 1-RTT short header packet.
     */
    SHORT((byte) 5),

    /**
     * Version negotiation packet.
     */
    VERSION_NEGOTIATION((byte) 6);

    final byte type;

    QuicPacketType(byte type) {
        this.type = type;
    }

    /**
     * Return the {@link QuicPacketType} for the given byte.
     *
     * @param type  the byte that represent the type.
     * @return      the {@link QuicPacketType}.
     */
    static QuicPacketType of(byte type) {
        switch(type) {
            case 1:
                return INITIAL;
            case 2:
                return RETRY;
            case 3:
                return HANDSHAKE;
            case 4:
                return ZERO_RTT;
            case 5:
                return SHORT;
            case 6:
                return VERSION_NEGOTIATION;
            default:
                throw new IllegalArgumentException("Unknown QUIC packet type: " + type);
        }
    }
}
