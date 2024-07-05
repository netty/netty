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

package io.netty.testsuite_jpms.test;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import org.junit.jupiter.api.Test;

import static io.netty.buffer.Unpooled.wrappedBuffer;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class CodecProtobufTest {

    @Test
    public void testDecoder() {
        EmbeddedChannel ch = new EmbeddedChannel(new ProtobufVarint32FrameDecoder());
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
}
