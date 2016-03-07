/*
 * Copyright 2016 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.util.CharsetUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.InetSocketAddress;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DatagramPacketDecoderTest {

    private EmbeddedChannel channel;

    @Before
    public void setUp() {
        channel = new EmbeddedChannel(
                new DatagramPacketDecoder(
                        new StringDecoder(CharsetUtil.UTF_8)));
    }

    @After
    public void tearDown() {
        assertFalse(channel.finish());
    }

    @Test
    public void testDecode() {
        InetSocketAddress recipient = new InetSocketAddress("127.0.0.1", 10000);
        InetSocketAddress sender = new InetSocketAddress("127.0.0.1", 20000);
        ByteBuf content = Unpooled.wrappedBuffer("netty".getBytes(CharsetUtil.UTF_8));
        assertTrue(channel.writeInbound(new DatagramPacket(content, recipient, sender)));
        assertEquals("netty", channel.readInbound());
    }
}
