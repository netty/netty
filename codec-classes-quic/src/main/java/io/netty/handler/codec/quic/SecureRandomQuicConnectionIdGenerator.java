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

import io.netty.util.internal.ObjectUtil;

import java.nio.ByteBuffer;
import java.security.SecureRandom;

final class SecureRandomQuicConnectionIdGenerator implements QuicConnectionIdGenerator {
    private static final SecureRandom RANDOM = new SecureRandom();

    static final QuicConnectionIdGenerator INSTANCE = new SecureRandomQuicConnectionIdGenerator();

    private SecureRandomQuicConnectionIdGenerator() {
    }

    @Override
    public ByteBuffer newId(int length) {
        ObjectUtil.checkInRange(length, 0, maxConnectionIdLength(), "length");
        byte[] bytes = new byte[length];
        RANDOM.nextBytes(bytes);
        return ByteBuffer.wrap(bytes);
    }

    @Override
    public ByteBuffer newId(ByteBuffer buffer, int length) {
        return newId(length);
    }

    @Override
    public int maxConnectionIdLength() {
        return Quiche.QUICHE_MAX_CONN_ID_LEN;
    }

    @Override
    public boolean isIdempotent() {
        return false;
    }
}
