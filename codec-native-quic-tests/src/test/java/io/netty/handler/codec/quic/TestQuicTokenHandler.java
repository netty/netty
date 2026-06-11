/*
 * Copyright 2024 The Netty Project
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

import java.net.InetSocketAddress;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class TestQuicTokenHandler implements QuicTokenHandler {
    public static final QuicTokenHandler INSTANCE = new TestQuicTokenHandler();

    @Override
    @SuppressWarnings("deprecation")
    public boolean writeToken(ByteBuf out, ByteBuf dcid, InetSocketAddress address) {
        assertEquals(ByteOrder.BIG_ENDIAN, out.order());
        return InsecureQuicTokenHandler.INSTANCE.writeToken(out, dcid, address);
    }

    @Override
    @SuppressWarnings("deprecation")
    public int validateToken(ByteBuf token, InetSocketAddress address) {
        assertEquals(ByteOrder.BIG_ENDIAN, token.order());
        return InsecureQuicTokenHandler.INSTANCE.validateToken(token, address);
    }

    @Override
    public int maxTokenLength() {
        return InsecureQuicTokenHandler.INSTANCE.maxTokenLength();
    }

    private TestQuicTokenHandler() { }
}
