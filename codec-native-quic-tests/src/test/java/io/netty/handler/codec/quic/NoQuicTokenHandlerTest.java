/*
 * Copyright 2026 The Netty Project
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
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class NoQuicTokenHandlerTest {

    @Test
    public void testMaxTokenLength() {
        assertEquals(0, NoQuicTokenHandler.INSTANCE.maxTokenLength());
    }

    @Test
    public void testDontWriteToken() throws UnknownHostException {
        ByteBuf out = Unpooled.buffer();
        ByteBuf dcid = Unpooled.wrappedBuffer(QuicConnectionAddress.random().id());
        try {
            boolean written = NoQuicTokenHandler.INSTANCE.writeToken(out, dcid, new InetSocketAddress(
                    InetAddress.getByAddress(new byte[] { 10, 10, 10, 10 }), 80));
            assertFalse(written);
            assertFalse(out.isReadable());
        } finally {
            out.release();
            dcid.release();
        }
    }

    @Test
    public void testValidateToken() throws UnknownHostException {
        ByteBuf token = Unpooled.wrappedBuffer(new byte[] { 0, 1, 2, 3, 4, 5, 6, 7, 8 });
        try {
            int offset = NoQuicTokenHandler.INSTANCE.validateToken(token, new InetSocketAddress(
                    InetAddress.getByAddress(new byte[]{ 10, 10, 10, 10 }), 80));
            assertEquals(-1, offset);
        } finally {
            token.release();
        }
    }
}
