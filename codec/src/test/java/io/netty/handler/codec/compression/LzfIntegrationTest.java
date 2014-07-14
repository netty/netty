/*
 * Copyright 2014 The Netty Project
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
package io.netty.handler.codec.compression;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.ThreadLocalRandom;
import org.junit.Test;

import java.util.Arrays;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

public class LzfIntegrationTest {

    private static final ThreadLocalRandom rand = ThreadLocalRandom.current();

    public static final byte[] EMPTY = new byte[0];

    @Test
    public void testEmpty() throws Exception {
        testIdentity(EMPTY);
    }

    @Test
    public void testOneByte() throws Exception {
        testIdentity(new byte[] { 'A' });
    }

    @Test
    public void testTwoBytes() throws Exception {
        testIdentity(new byte[] { 'B', 'A' });
    }

    @Test
    public void testRegular() throws Exception {
        byte[] data = ("Netty is a NIO client server framework which enables quick and easy development " +
                "of network applications such as protocol servers and clients.").getBytes();
        testIdentity(data);
    }

    @Test
    public void testLargeRandom() throws Exception {
        byte[] data = new byte[1048576];
        rand.nextBytes(data);
        testIdentity(data);
    }

    @Test
    public void testPartRandom() throws Exception {
        byte[] data = new byte[12345];
        rand.nextBytes(data);
        for (int i = 0; i < 1024; i++) {
            data[i] = 123;
        }
        testIdentity(data);
    }

    @Test
    public void testCompressible() throws Exception {
        byte[] data = new byte[10000];
        for (int i = 0; i < data.length; i++) {
            data[i] = i % 4 != 0 ? 0 : (byte) rand.nextInt();
        }
        testIdentity(data);
    }

    @Test
    public void testLongBlank() throws Exception {
        byte[] data = new byte[100000];
        testIdentity(data);
    }

    @Test
    public void testLongSame() throws Exception {
        byte[] data = new byte[100000];
        Arrays.fill(data, (byte) 123);
        testIdentity(data);
    }

    @Test
    public void testSequential() throws Exception {
        byte[] data = new byte[49];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) i;
        }
        testIdentity(data);
    }

    private static void testIdentity(byte[] data) {
        ByteBuf in = Unpooled.wrappedBuffer(data);
        EmbeddedChannel encoder = new EmbeddedChannel(new LzfEncoder());
        EmbeddedChannel decoder = new EmbeddedChannel(new LzfDecoder());
        try {
            ByteBuf msg;

            encoder.writeOutbound(in.copy());
            encoder.finish();
            CompositeByteBuf compressed = Unpooled.compositeBuffer();
            while ((msg = encoder.readOutbound()) != null) {
                compressed.addComponent(msg);
                compressed.writerIndex(compressed.writerIndex() + msg.readableBytes());
            }
            assertThat(compressed, is(notNullValue()));

            decoder.writeInbound(compressed.retain());
            assertFalse(compressed.isReadable());
            CompositeByteBuf decompressed = Unpooled.compositeBuffer();
            while ((msg = decoder.readInbound()) != null) {
                decompressed.addComponent(msg);
                decompressed.writerIndex(decompressed.writerIndex() + msg.readableBytes());
            }
            assertEquals(in, decompressed);

            compressed.release();
            decompressed.release();
            in.release();
        } finally {
            encoder.close();
            decoder.close();

            for (;;) {
                Object msg = encoder.readOutbound();
                if (msg == null) {
                    break;
                }
                ReferenceCountUtil.release(msg);
            }

            for (;;) {
                Object msg = decoder.readInbound();
                if (msg == null) {
                    break;
                }
                ReferenceCountUtil.release(msg);
            }
        }
    }
}
