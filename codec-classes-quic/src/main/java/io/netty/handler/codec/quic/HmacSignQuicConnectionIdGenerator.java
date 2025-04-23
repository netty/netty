/*
 * Copyright 2021 The Netty Project
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

import java.nio.ByteBuffer;

import io.netty.util.internal.ObjectUtil;

/**
 * A {@link QuicConnectionIdGenerator} which creates new connection id by signing the given input
 * using hmac algorithms.
 */
final class HmacSignQuicConnectionIdGenerator implements QuicConnectionIdGenerator {
    static final QuicConnectionIdGenerator INSTANCE = new HmacSignQuicConnectionIdGenerator();

    private HmacSignQuicConnectionIdGenerator() {
    }

    @Override
    public ByteBuffer newId(int length) {
        throw new UnsupportedOperationException(
                "HmacSignQuicConnectionIdGenerator should always have an input to sign with");
    }

    @Override
    public ByteBuffer newId(ByteBuffer buffer, int length) {
        ObjectUtil.checkNotNull(buffer, "buffer");
        ObjectUtil.checkPositive(buffer.remaining(), "buffer");
        ObjectUtil.checkInRange(length, 0, maxConnectionIdLength(), "length");

        return Hmac.sign(buffer, length);
    }

    @Override
    public int maxConnectionIdLength() {
        return Quiche.QUICHE_MAX_CONN_ID_LEN;
    }

    @Override
    public boolean isIdempotent() {
        return true;
    }
}
