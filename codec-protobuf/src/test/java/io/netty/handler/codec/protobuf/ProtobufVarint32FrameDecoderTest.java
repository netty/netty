/*
 * Copyright 2015 The Netty Project
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
package io.netty.handler.codec.protobuf;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.netty.buffer.Unpooled.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProtobufVarint32FrameDecoderTest {

    private EmbeddedChannel ch;

    @BeforeEach
    public void setUp() {
        ch = new EmbeddedChannel(new ProtobufVarint32FrameDecoder());
    }

    @Test
    public void testTinyDecode() {
        byte[] b = { 4, 1, 1, 1, 1 };
        assertFalse(ch.writeInbound(wrappedBuffer(b, 0, 1)));
        assertNull(ch.readInbound());
        assertFalse(ch.writeInbound(wrappedBuffer(b, 1, 2)));
        assertNull(ch.readInbound());
        assertTrue(ch.writeInbound(wrappedBuffer(b, 3, b.length - 3)));

        ByteBuf expected = wrappedBuffer(new byte[] { 1, 1, 1, 1 });
        ByteBuf actual = ch.readInbound();

        assertEquals(expected, actual);
        assertFalse(ch.finish());

        expected.release();
        actual.release();
    }

    @Test
    public void testRegularDecode() {
        byte[] b = new byte[2048];
        for (int i = 2; i < 2048; i ++) {
            b[i] = 1;
        }
        b[0] = -2;
        b[1] = 15;
        assertFalse(ch.writeInbound(wrappedBuffer(b, 0, 1)));
        assertNull(ch.readInbound());
        assertFalse(ch.writeInbound(wrappedBuffer(b, 1, 127)));
        assertNull(ch.readInbound());
        assertFalse(ch.writeInbound(wrappedBuffer(b, 127, 600)));
        assertNull(ch.readInbound());
        assertTrue(ch.writeInbound(wrappedBuffer(b, 727, b.length - 727)));

        ByteBuf expected = wrappedBuffer(b, 2, b.length - 2);
        ByteBuf actual = ch.readInbound();
        assertEquals(expected, actual);
        assertFalse(ch.finish());

        expected.release();
        actual.release();
    }
}
