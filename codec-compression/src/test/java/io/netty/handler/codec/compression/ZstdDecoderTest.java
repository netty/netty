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
package io.netty.handler.codec.compression;

import com.github.luben.zstd.Zstd;
import com.github.luben.zstd.ZstdCompressCtx;
import io.netty.buffer.AbstractByteBufAllocator;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


public class ZstdDecoderTest extends AbstractDecoderTest {

    public ZstdDecoderTest() throws Exception {
    }

    @Override
    public EmbeddedChannel createChannel() {
        return new EmbeddedChannel(new ZstdDecoder());
    }

    @Override
    protected byte[] compress(byte[] data) throws Exception {
        return Zstd.compress(data);
    }

    @Test
    public void testFrameWithWindowLogAboveCapIsRejected() {
        // Incompressible random data so libzstd actually has to use the declared window
        // (highly compressible content lets libzstd shrink the effective window to the
        // content size, making setLongMax ineffective for the test).
        byte[] payload = new byte[256 * 1024];
        new Random(12345L).nextBytes(payload);

        // Compressed with windowLog = 21 (2 MiB window).
        byte[] compressed = compressWithWindowLog(payload, 21);

        // Decoder caps Window_Log at 15 (32 KiB) -> the frame must be rejected.
        EmbeddedChannel ch = new EmbeddedChannel(new ZstdDecoder(4 * 1024 * 1024, 15));
        try {
            assertThrows(DecompressionException.class,
                    () -> ch.writeInbound(Unpooled.wrappedBuffer(compressed)));
        } finally {
            ch.finishAndReleaseAll();
        }
    }

    @Test
    public void testFrameWithWindowLogWithinCapIsAccepted() {
        byte[] payload = new byte[256 * 1024];
        new Random(12345L).nextBytes(payload);

        byte[] compressed = compressWithWindowLog(payload, 18); // 256 KiB window

        EmbeddedChannel ch = new EmbeddedChannel(new ZstdDecoder(4 * 1024 * 1024, 20));
        try {
            assertTrue(ch.writeInbound(Unpooled.wrappedBuffer(compressed)));

            ByteBuf acc = Unpooled.buffer();
            try {
                ByteBuf b;
                while ((b = ch.readInbound()) != null) {
                    try {
                        acc.writeBytes(b);
                    } finally {
                        b.release();
                    }
                }
                byte[] actual = new byte[acc.readableBytes()];
                acc.readBytes(actual);
                assertArrayEquals(payload, actual);
            } finally {
                acc.release();
            }
        } finally {
            ch.finishAndReleaseAll();
        }
    }

    @Test
    public void testFrameContentSizeDoesNotDriveAllocationWhenUnlimited() {
        // Frame header declaring a Frame_Content_Size of 0x7FFFFFF0 followed by a single raw
        // block that only holds one byte.
        byte[] frame = {
                0x28, (byte) 0xB5, 0x2F, (byte) 0xFD, // magic
                (byte) 0x80, // FCS field 4 bytes, no single segment
                0x00, // Window_Descriptor: 1 KiB window
                (byte) 0xF0, (byte) 0xFF, (byte) 0xFF, 0x7F, // Frame_Content_Size
                0x09, 0x00, 0x00, // last raw block of 1 byte
                0x41
        };

        final int[] maxRequested = new int[1];
        EmbeddedChannel ch = new EmbeddedChannel(new ZstdDecoder(0));
        ch.config().setAllocator(new AbstractByteBufAllocator() {
            @Override
            protected ByteBuf newHeapBuffer(int initialCapacity, int maxCapacity) {
                maxRequested[0] = Math.max(maxRequested[0], initialCapacity);
                if (initialCapacity > CompressionUtil.DEFAULT_MAX_FORWARD_BYTES) {
                    // Don't actually allocate ~2 GiB in the test.
                    throw new IllegalStateException("requested " + initialCapacity + " bytes");
                }
                return Unpooled.buffer(initialCapacity, maxCapacity);
            }

            @Override
            protected ByteBuf newDirectBuffer(int initialCapacity, int maxCapacity) {
                return Unpooled.directBuffer(initialCapacity, maxCapacity);
            }

            @Override
            public boolean isDirectBufferPooled() {
                return false;
            }
        });
        try {
            assertThrows(DecompressionException.class, () -> ch.writeInbound(Unpooled.wrappedBuffer(frame)));
            assertTrue(maxRequested[0] <= CompressionUtil.DEFAULT_MAX_FORWARD_BYTES,
                    "requested " + maxRequested[0] + " bytes");
        } finally {
            ch.finishAndReleaseAll();
        }
    }

    @Test
    public void testDecompressLargeFrameWhenUnlimited() {
        byte[] payload = new byte[1024 * 1024];
        new Random(12345L).nextBytes(payload);
        byte[] compressed = Zstd.compress(payload);

        EmbeddedChannel ch = new EmbeddedChannel(new ZstdDecoder(0));
        try {
            assertTrue(ch.writeInbound(Unpooled.wrappedBuffer(compressed)));

            ByteBuf acc = Unpooled.buffer();
            try {
                ByteBuf b;
                while ((b = ch.readInbound()) != null) {
                    try {
                        assertTrue(b.readableBytes() <= CompressionUtil.DEFAULT_MAX_FORWARD_BYTES);
                        acc.writeBytes(b);
                    } finally {
                        b.release();
                    }
                }
                assertEquals(payload.length, acc.readableBytes());
                byte[] actual = new byte[acc.readableBytes()];
                acc.readBytes(actual);
                assertArrayEquals(payload, actual);
            } finally {
                acc.release();
            }
        } finally {
            ch.finishAndReleaseAll();
        }
    }

    private static byte[] compressWithWindowLog(byte[] data, int windowLog) {
        try (ZstdCompressCtx ctx = new ZstdCompressCtx()) {
            ctx.setLevel(Zstd.defaultCompressionLevel());
            ctx.setWindowLog(windowLog);
            byte[] dst = new byte[(int) Zstd.compressBound(data.length)];
            int written = ctx.compressByteArray(dst, 0, dst.length, data, 0, data.length);
            byte[] out = new byte[written];
            System.arraycopy(dst, 0, out, 0, written);
            return out;
        }
    }
}
