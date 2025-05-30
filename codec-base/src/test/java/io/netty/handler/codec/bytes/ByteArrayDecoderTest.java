/*
 * Copyright 2012 The Netty Project
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
package io.netty.handler.codec.bytes;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.internal.EmptyArrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static io.netty.buffer.Unpooled.*;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class ByteArrayDecoderTest {

    private EmbeddedChannel ch;

    @BeforeEach
    public void setUp() {
        ch = new EmbeddedChannel(new ByteArrayDecoder());
    }

    @Test
    public void testDecode() {
        byte[] b = new byte[2048];
        new Random().nextBytes(b);
        ch.writeInbound(wrappedBuffer(b));
        assertArrayEquals(b, ch.readInbound());
    }

    @Test
    public void testDecodeEmpty() {
        ch.writeInbound(EMPTY_BUFFER);
        assertArrayEquals(EmptyArrays.EMPTY_BYTES, ch.readInbound());
    }

    @Test
    public void testDecodeOtherType() {
        String str = "Meep!";
        ch.writeInbound(str);
        assertSame(str, ch.readInbound());
    }
}
