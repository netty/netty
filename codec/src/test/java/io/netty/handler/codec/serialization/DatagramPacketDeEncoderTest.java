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
package io.netty.handler.codec.serialization;

import io.netty.channel.AddressedEnvelope;
import io.netty.channel.DefaultAddressedEnvelope;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.internal.SocketUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.Serializable;
import java.net.InetSocketAddress;

import static org.junit.Assert.*;

public class DatagramPacketDeEncoderTest {

    private EmbeddedChannel channel;

    @Before
    public void setUp() {
        channel = new EmbeddedChannel(new DatagramObjectEncoder<TestMessage>(),
                                      new DatagramObjectDecoder(new ObjectDecoder(ClassResolvers.cacheDisabled(null))));
    }

    @After
    public void tearDown() {
        assertFalse(channel.finish());
    }

    @Test
    public void testDeEncode() {
        InetSocketAddress recipient = SocketUtils.socketAddress("127.0.0.1", 10000);
        InetSocketAddress sender = SocketUtils.socketAddress("127.0.0.1", 20000);
        assertTrue(channel.writeOutbound(
                new DefaultAddressedEnvelope<TestMessage, InetSocketAddress>(new TestMessage("netty"), recipient,
                                                                             sender)));
        DatagramPacket packet = channel.readOutbound();
        assertEquals(packet.recipient(), recipient);
        assertEquals(packet.sender(), sender);
        assertTrue(channel.writeInbound(packet));
        AddressedEnvelope<TestMessage, InetSocketAddress> result = channel.readInbound();

        assertEquals(result.content().hello, "netty");
        assertEquals(result.recipient(), recipient);
        assertEquals(result.sender(), sender);
    }

    private static final class TestMessage implements Serializable {
        private final String hello;

        private TestMessage(String hello) {
            this.hello = hello;
        }
    }

    @Test
    public void testUnmatchedType() {
        String netty = "netty";
        assertTrue(channel.writeOutbound(netty));
        assertSame(netty, channel.readOutbound());
    }

}
