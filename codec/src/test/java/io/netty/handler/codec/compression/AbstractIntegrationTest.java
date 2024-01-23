/*
 * Copyright 2014 The Netty Project
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
package io.netty.handler.codec.compression;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.EmptyArrays;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class AbstractIntegrationTest {

    protected static final Random rand = new Random();

    protected EmbeddedChannel encoder;
    protected EmbeddedChannel decoder;

    protected abstract EmbeddedChannel createEncoder();
    protected abstract EmbeddedChannel createDecoder();

    public void initChannels() {
        encoder = createEncoder();
        decoder = createDecoder();
    }

    public void closeChannels() {
        encoder.close();
        for (;;) {
            Object msg = encoder.readOutbound();
            if (msg == null) {
                break;
            }
            ReferenceCountUtil.release(msg);
        }

        decoder.close();
        for (;;) {
            Object msg = decoder.readInbound();
            if (msg == null) {
                break;
            }
            ReferenceCountUtil.release(msg);
        }
    }

    @Test
    public void testEmpty() throws Exception {
        testIdentity(EmptyArrays.EMPTY_BYTES, true);
        testIdentity(EmptyArrays.EMPTY_BYTES, false);
    }

    @Test
    public void testOneByte() throws Exception {
        final byte[] data = { 'A' };
        testIdentity(data, true);
        testIdentity(data, false);
    }

    @Test
    public void testTwoBytes() throws Exception {
        final byte[] data = { 'B', 'A' };
        testIdentity(data, true);
        testIdentity(data, false);
    }

    @Test
    public void testRegular() throws Exception {
        final byte[] data = ("Netty is a NIO client server framework which enables " +
                "quick and easy development of network applications such as protocol " +
                "servers and clients.").getBytes(CharsetUtil.UTF_8);
        testIdentity(data, true);
        testIdentity(data, false);
    }

    @Test
    public void testLargeRandom() throws Exception {
        final byte[] data = new byte[1024 * 1024];
        rand.nextBytes(data);
        testIdentity(data, true);
        testIdentity(data, false);
    }

    @Test
    public void testPartRandom() throws Exception {
        final byte[] data = new byte[10240];
        rand.nextBytes(data);
        for (int i = 0; i < 1024; i++) {
            data[i] = 2;
        }
        testIdentity(data, true);
        testIdentity(data, false);
    }

    @Test
    public void testCompressible() throws Exception {
        final byte[] data = new byte[10240];
        for (int i = 0; i < data.length; i++) {
            data[i] = i % 4 != 0 ? 0 : (byte) rand.nextInt();
        }
        testIdentity(data, true);
        testIdentity(data, false);
    }

    @Test
    public void testLongBlank() throws Exception {
        final byte[] data = new byte[102400];
        testIdentity(data, true);
        testIdentity(data, false);
    }

    @Test
    public void testLongSame() throws Exception {
        final byte[] data = new byte[102400];
        Arrays.fill(data, (byte) 123);
        testIdentity(data, true);
        testIdentity(data, false);
    }

    @Test
    public void testSequential() throws Exception {
        final byte[] data = new byte[1024];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) i;
        }
        testIdentity(data, true);
        testIdentity(data, false);
    }

    protected void testIdentity(final byte[] data, boolean heapBuffer) {
        initChannels();
        final ByteBuf in = heapBuffer? Unpooled.wrappedBuffer(data) :
                Unpooled.directBuffer(data.length).writeBytes(data);
        final CompositeByteBuf compressed = Unpooled.compositeBuffer();
        final CompositeByteBuf decompressed = Unpooled.compositeBuffer();

        try {
            assertTrue(encoder.writeOutbound(in.retain()));
            assertTrue(encoder.finish());

            ByteBuf msg;
            while ((msg = encoder.readOutbound()) != null) {
                compressed.addComponent(true, msg);
            }
            assertThat(compressed, is(notNullValue()));

            decoder.writeInbound(compressed.retain());
            assertFalse(compressed.isReadable());
            while ((msg = decoder.readInbound()) != null) {
                decompressed.addComponent(true, msg);
            }
            in.readerIndex(0);
            assertEquals(in, decompressed);
        } finally {
            compressed.release();
            decompressed.release();
            in.release();
            closeChannels();
        }
    }
}
