/*
 * Copyright 2013 The Netty Project
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
package io.netty.handler.codec.dns;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.CorruptedFrameException;
import org.bouncycastle.jce.provider.JDKDSASigner;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.net.InetSocketAddress;

public class DnsResponseTest {

    private static final byte[][] packets = {
            {
                    0, 1, -127, -128, 0, 1, 0, 1, 0, 0, 0, 0, 3, 119, 119, 119, 7, 101, 120, 97, 109, 112, 108, 101, 3,
                    99, 111, 109, 0, 0, 1, 0, 1, -64, 12, 0, 1, 0, 1, 0, 0, 16, -113, 0, 4, -64, 0, 43, 10
            },
            {
                    0, 1, -127, -128, 0, 1, 0, 1, 0, 0, 0, 0, 3, 119, 119, 119, 7, 101, 120, 97, 109, 112, 108, 101, 3,
                    99, 111, 109, 0, 0, 28, 0, 1, -64, 12, 0, 28, 0, 1, 0, 0, 69, -8, 0, 16, 32, 1, 5, 0, 0, -120, 2,
                    0, 0, 0, 0, 0, 0, 0, 0, 16
            },
            {
                    0, 3, -127, -128, 0, 1, 0, 1, 0, 0, 0, 0, 3, 119, 119, 119, 7, 101, 120, 97, 109, 112, 108, 101, 3,
                    99, 111, 109, 0, 0, 16, 0, 1, -64, 12, 0, 16, 0, 1, 0, 0, 84, 75, 0, 12, 11, 118, 61, 115, 112,
                    102, 49, 32, 45, 97, 108, 108
            },
            {
                    -105, 19, -127, 0, 0, 1, 0, 0, 0, 13, 0, 0, 2, 104, 112, 11, 116, 105, 109, 98, 111, 117, 100, 114,
                    101, 97, 117, 3, 111, 114, 103, 0, 0, 1, 0, 1, 0, 0, 2, 0, 1, 0, 7, -23, 0, 0, 20, 1, 68, 12, 82,
                    79, 79, 84, 45, 83, 69, 82, 86, 69, 82, 83, 3, 78, 69, 84, 0, 0, 0, 2, 0, 1, 0, 7, -23, 0, 0, 4, 1,
                    70, -64, 49, 0, 0, 2, 0, 1, 0, 7, -23, 0, 0, 4, 1, 69, -64, 49, 0, 0, 2, 0, 1, 0, 7, -23, 0, 0, 4,
                    1, 75, -64, 49, 0, 0, 2, 0, 1, 0, 7, -23, 0, 0, 4, 1, 67, -64, 49, 0, 0, 2, 0, 1, 0, 7, -23, 0, 0,
                    4, 1, 76, -64, 49, 0, 0, 2, 0, 1, 0, 7, -23, 0, 0, 4, 1, 71, -64, 49, 0, 0, 2, 0, 1, 0, 7, -23, 0,
                    0, 4, 1, 73, -64, 49, 0, 0, 2, 0, 1, 0, 7, -23, 0, 0, 4, 1, 66, -64, 49, 0, 0, 2, 0, 1, 0, 7, -23,
                    0, 0, 4, 1, 77, -64, 49, 0, 0, 2, 0, 1, 0, 7, -23, 0, 0, 4, 1, 65, -64, 49, 0, 0, 2, 0, 1, 0, 7,
                    -23, 0, 0, 4, 1, 72, -64, 49, 0, 0, 2, 0, 1, 0, 7, -23, 0, 0, 4, 1, 74, -64, 49
            },
            {
                    0, 2, -127, -128, 0, 1, 0, 0, 0, 1, 0, 0, 3, 119, 119, 119, 7, 101, 120, 97, 109, 112, 108, 101, 3,
                    99, 111, 109, 0, 0, 15, 0, 1, -64, 16, 0, 6, 0, 1, 0, 0, 3, -43, 0, 45, 3, 115, 110, 115, 3, 100,
                    110, 115, 5, 105, 99, 97, 110, 110, 3, 111, 114, 103, 0, 3, 110, 111, 99, -64, 49, 119, -4, 39,
                    112, 0, 0, 28, 32, 0, 0, 14, 16, 0, 18, 117, 0, 0, 0, 14, 16
            }
    };

    private static final byte[] malformedLoopPacket = {
            0, 4, -127, -128, 0, 1, 0, 0, 0, 0, 0, 0, -64, 12, 0, 1, 0, 1
    };

    @Test
    public void readDatagramResponseTest() throws Exception {
        EmbeddedChannel embedder = new EmbeddedChannel(new DnsMessageFrameDecoder(), new DnsResponseDecoder());
        for (byte[] p: packets) {
            ByteBuf packet = embedder.alloc().buffer(512).writeBytes(p);
            embedder.writeInbound(new DatagramPacket(packet, null, new InetSocketAddress(0)));
            DnsResponse decoded = embedder.readInbound();
            ByteBuf raw = Unpooled.wrappedBuffer(p);
            Assert.assertEquals("Invalid id, expected: " + raw.getUnsignedShort(0) + ", actual: "
                    + decoded.header().id(), raw.getUnsignedShort(0), decoded.header().id());
            Assert.assertEquals("Invalid resource count,  expected: " + raw.getUnsignedShort(4) + ", actual: "
                    + decoded.questions().size(), raw.getUnsignedShort(4), decoded.questions().size());
            Assert.assertEquals("Invalid resource count,  expected: " + raw.getUnsignedShort(6) + ", actual: "
                    + decoded.answers().size(), raw.getUnsignedShort(6), decoded.answers().size());
            Assert.assertEquals("Invalid resource count,  expected: " + raw.getUnsignedShort(8) + ", actual: "
                    + decoded.authorityResources().size(), raw.getUnsignedShort(8), decoded.authorityResources()
                    .size());
            Assert.assertEquals("Invalid resource count,  expected: " + raw.getUnsignedShort(10) + ", actual: "
                    + decoded.additionalResources().size(), raw.getUnsignedShort(10),
                    decoded.additionalResources().size());
            decoded.release();
        }
    }

    @Test
    public void readSocketResponseTest() throws Exception {
        EmbeddedChannel embedder = new EmbeddedChannel(new DnsMessageFrameDecoder(), new DnsResponseDecoder());
        for (byte[] p: packets) {
            ByteBuf packet = embedder.alloc().buffer(512).writeShort(p.length).writeBytes(p);
            embedder.writeInbound(packet);
            DnsResponse decoded = embedder.readInbound();
            ByteBuf raw = Unpooled.wrappedBuffer(p);
            Assert.assertEquals("Invalid id, expected: " + raw.getUnsignedShort(0) + ", actual: "
                    + decoded.header().id(), raw.getUnsignedShort(0), decoded.header().id());
            Assert.assertEquals("Invalid resource count,  expected: " + raw.getUnsignedShort(4) + ", actual: "
                    + decoded.questions().size(), raw.getUnsignedShort(4), decoded.questions().size());
            Assert.assertEquals("Invalid resource count,  expected: " + raw.getUnsignedShort(6) + ", actual: "
                    + decoded.answers().size(), raw.getUnsignedShort(6), decoded.answers().size());
            Assert.assertEquals("Invalid resource count,  expected: " + raw.getUnsignedShort(8) + ", actual: "
                    + decoded.authorityResources().size(), raw.getUnsignedShort(8), decoded.authorityResources()
                    .size());
            Assert.assertEquals("Invalid resource count,  expected: " + raw.getUnsignedShort(10) + ", actual: "
                    + decoded.additionalResources().size(), raw.getUnsignedShort(10),
                    decoded.additionalResources().size());
            decoded.release();
        }
    }

    @Test
    public void readSocketMultipleWritesResponseTest() throws Exception {
        EmbeddedChannel embedder = new EmbeddedChannel(new DnsMessageFrameDecoder(), new DnsResponseDecoder());
        System.out.println("readSocketMultipleWritesResponseTest");
        for (byte[] p: packets) {
            ByteBuf prefix = embedder.alloc().buffer(514).writeShort(p.length);
            embedder.writeInbound(prefix.retain());
            Assert.assertNull(embedder.readInbound());
            embedder.writeInbound(prefix.writeBytes(p));
            DnsResponse decoded = embedder.readInbound();
            ByteBuf raw = Unpooled.wrappedBuffer(p);
            Assert.assertEquals("Invalid id, expected: " + raw.getUnsignedShort(0) + ", actual: "
                    + decoded.header().id(), raw.getUnsignedShort(0), decoded.header().id());
            Assert.assertEquals("Invalid resource count,  expected: " + raw.getUnsignedShort(4) + ", actual: "
                    + decoded.questions().size(), raw.getUnsignedShort(4), decoded.questions().size());
            Assert.assertEquals("Invalid resource count,  expected: " + raw.getUnsignedShort(6) + ", actual: "
                    + decoded.answers().size(), raw.getUnsignedShort(6), decoded.answers().size());
            Assert.assertEquals("Invalid resource count,  expected: " + raw.getUnsignedShort(8) + ", actual: "
                    + decoded.authorityResources().size(), raw.getUnsignedShort(8), decoded.authorityResources()
                    .size());
            Assert.assertEquals("Invalid resource count,  expected: " + raw.getUnsignedShort(10) + ", actual: "
                    + decoded.additionalResources().size(), raw.getUnsignedShort(10),
                    decoded.additionalResources().size());
            decoded.release();
        }
    }

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Test
    public void readMalormedResponseTest() throws Exception {
        EmbeddedChannel embedder = new EmbeddedChannel(new DnsResponseDecoder());
        ByteBuf packet = embedder.alloc().buffer(512).writeBytes(malformedLoopPacket);
        exception.expect(CorruptedFrameException.class);
        embedder.writeInbound(new DatagramPacket(packet, null, new InetSocketAddress(0)));
    }
}
