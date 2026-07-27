/*
 * Copyright 2025 The Netty Project
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

import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.zip.DeflaterOutputStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class JZlibDecompressorTest extends AbstractDecompressorTest {
    @Override
    protected ChannelHandler createCompressor() {
        return new JZlibEncoder();
    }

    @Override
    protected Decompressor.AbstractDecompressorBuilder createDecompressor() {
        return JZlibDecompressor.builder();
    }

    @Test
    public void testMalformedInputDoesNotLeakOutput() {
        Decompressor decompressor = JZlibDecompressor.builder().build(ByteBufAllocator.DEFAULT);
        try {
            assertEquals(Decompressor.Status.NEED_INPUT, decompressor.status());
            decompressor.addInput(Unpooled.wrappedBuffer(new byte[] { 0, 0, 0, 0 }));
            assertEquals(Decompressor.Status.NEED_OUTPUT, decompressor.status());
            assertThrows(DecompressionException.class, decompressor::takeOutput);
        } finally {
            decompressor.close();
        }
    }

    @Test
    public void testNegativeMaxAllocationRejected() {
        assertThrows(IllegalArgumentException.class, () -> JZlibDecompressor.builder().maxAllocation(-1));
    }

    @Test
    public void testTruncatedInputRejectedAtEndOfInput() throws Exception {
        byte[] compressed = deflate("truncated".getBytes(CharsetUtil.UTF_8));
        Decompressor decompressor = JZlibDecompressor.builder().build(ByteBufAllocator.DEFAULT);
        try {
            assertEquals(Decompressor.Status.NEED_INPUT, decompressor.status());
            decompressor.addInput(Unpooled.wrappedBuffer(Arrays.copyOf(compressed, compressed.length - 1)));
            while (decompressor.status() == Decompressor.Status.NEED_OUTPUT) {
                decompressor.takeOutput().release();
            }
            assertEquals(Decompressor.Status.NEED_INPUT, decompressor.status());
            assertThrows(DecompressionException.class, decompressor::endOfInput);
        } finally {
            decompressor.close();
        }
    }

    @Test
    public void testCloseBeforeCompletionIsIdempotent() {
        Decompressor decompressor = JZlibDecompressor.builder().build(ByteBufAllocator.DEFAULT);
        assertEquals(Decompressor.Status.NEED_INPUT, decompressor.status());
        decompressor.addInput(Unpooled.wrappedBuffer(new byte[] { 0x78 }));
        assertDoesNotThrow(decompressor::close);
        assertDoesNotThrow(decompressor::close);
    }

    private static byte[] deflate(byte[] data) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        DeflaterOutputStream deflater = new DeflaterOutputStream(output);
        deflater.write(data);
        deflater.close();
        return output.toByteArray();
    }
}
