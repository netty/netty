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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class BrotliDecompressorTest extends AbstractDecompressorTest {
    @Override
    protected ChannelHandler createCompressor() {
        return new BrotliEncoder();
    }

    @Override
    protected Decompressor.AbstractDecompressorBuilder createDecompressor() {
        return BrotliDecompressor.builder();
    }

    @Test
    public void reportsOutputBeforeMoreInput() throws Exception {
        ByteBuf[] data = largeData();

        try (Decompressor decompressor = createDecompressor().build(ByteBufAllocator.DEFAULT)) {
            assertEquals(Decompressor.Status.NEED_INPUT, decompressor.status());
            decompressor.addInput(data[0]);
            data[0] = null;

            assertEquals(Decompressor.Status.NEED_OUTPUT, decompressor.status());
            decompressor.takeOutput().release();
        } finally {
            for (ByteBuf buffer : data) {
                if (buffer != null) {
                    buffer.release();
                }
            }
        }
    }

    @Test
    public void endOfInputRejectsTruncatedStream() throws Exception {
        ByteBuf[] data = smallData();
        ByteBuf truncated = data[0].readRetainedSlice(data[0].readableBytes() - 1);

        try (Decompressor decompressor = createDecompressor().build(ByteBufAllocator.DEFAULT)) {
            assertEquals(Decompressor.Status.NEED_INPUT, decompressor.status());
            decompressor.addInput(truncated);
            truncated = null;

            while (decompressor.status() == Decompressor.Status.NEED_OUTPUT) {
                decompressor.takeOutput().release();
            }

            assertEquals(Decompressor.Status.NEED_INPUT, decompressor.status());
            assertThrows(DecompressionException.class, decompressor::endOfInput);
        } finally {
            if (truncated != null) {
                truncated.release();
            }
            for (ByteBuf buffer : data) {
                buffer.release();
            }
        }
    }
}
