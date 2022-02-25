/*
 * Copyright 2016 The Netty Project
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
package io.netty5.handler.codec;

import io.netty5.buffer.ByteBuf;
import io.netty5.buffer.Unpooled;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.embedded.EmbeddedChannel;
import io.netty5.channel.socket.DatagramPacket;
import io.netty5.handler.codec.string.StringDecoder;
import io.netty5.util.CharsetUtil;
import io.netty5.util.internal.SocketUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DatagramPacketDecoderTest {

    private EmbeddedChannel channel;

    @BeforeEach
    public void setUp() {
        channel = new EmbeddedChannel(
                new DatagramPacketDecoder(
                        new StringDecoder(CharsetUtil.UTF_8)));
    }

    @AfterEach
    public void tearDown() {
        assertFalse(channel.finish());
    }

    @Test
    public void testDecode() {
        InetSocketAddress recipient = SocketUtils.socketAddress("127.0.0.1", 10000);
        InetSocketAddress sender = SocketUtils.socketAddress("127.0.0.1", 20000);
        ByteBuf content = Unpooled.wrappedBuffer("netty".getBytes(CharsetUtil.UTF_8));
        assertTrue(channel.writeInbound(new DatagramPacket(content, recipient, sender)));
        assertEquals("netty", channel.readInbound());
    }

    @Test
    public void testIsNotSharable() {
        testIsSharable(false);
    }

    @Test
    public void testIsSharable() {
        testIsSharable(true);
    }

    private static void testIsSharable(boolean sharable) {
        MessageToMessageDecoder<ByteBuf> wrapped = new TestMessageToMessageDecoder(sharable);
        DatagramPacketDecoder decoder = new DatagramPacketDecoder(wrapped);
        assertEquals(wrapped.isSharable(), decoder.isSharable());
    }

    private static final class TestMessageToMessageDecoder extends MessageToMessageDecoder<ByteBuf> {

        private final boolean sharable;

        TestMessageToMessageDecoder(boolean sharable) {
            this.sharable = sharable;
        }

        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
            // NOOP
        }

        @Override
        public boolean isSharable() {
            return sharable;
        }
    }
}
