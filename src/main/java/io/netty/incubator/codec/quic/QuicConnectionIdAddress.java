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

import io.netty.buffer.ByteBuf;

import java.net.SocketAddress;
import java.security.SecureRandom;

/**
 * A {@link QuicConnectionIdAddress}.
 */
public final class QuicConnectionIdAddress extends SocketAddress {

    // Accessed by the codec itself.
    final byte[] connId;

    public QuicConnectionIdAddress(byte[] connId) {
        if (connId.length > Quiche.QUICHE_MAX_CONN_ID_LEN) {
            throw new IllegalArgumentException("Connection ID can only be of max length "
                    + Quiche.QUICHE_MAX_CONN_ID_LEN);
        }
        this.connId = connId.clone();
    }

    /**
     * Return a random generated {@link QuicConnectionIdAddress}.
     */
    public static QuicConnectionIdAddress random() {
        return randomGenerator().newAddress();
    }

    /**
     * Return a {@link QuicConnectionIdAddressGenerator} which randomly generates new {@link QuicConnectionIdAddress}es.
     */
    public static QuicConnectionIdAddressGenerator randomGenerator() {
        return SecureRandomQuicConnectionIdGenerator.INSTANCE;
    }

    private static final class SecureRandomQuicConnectionIdGenerator implements QuicConnectionIdAddressGenerator {
        private static final SecureRandom RANDOM = new SecureRandom();

        public static final QuicConnectionIdAddressGenerator INSTANCE = new SecureRandomQuicConnectionIdGenerator();

        private SecureRandomQuicConnectionIdGenerator() { }

        @Override
        public QuicConnectionIdAddress newAddress() {
            return newAddress(maxConnectionIdLength());
        }

        @Override
        public QuicConnectionIdAddress newAddress(int length) {
            if (length > maxConnectionIdLength()) {
                throw new IllegalArgumentException();
            }
            byte[] bytes = new byte[length];
            RANDOM.nextBytes(bytes);
            return new QuicConnectionIdAddress(bytes);
        }

        @Override
        public QuicConnectionIdAddress newAddress(ByteBuf buffer, int length) {
            return newAddress(length);
        }

        @Override
        public int maxConnectionIdLength() {
            return Quiche.QUICHE_MAX_CONN_ID_LEN;
        }
    }
}
