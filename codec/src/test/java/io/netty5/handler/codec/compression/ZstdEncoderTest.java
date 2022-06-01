/*
 * Copyright 2021 The Netty Project
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
package io.netty5.handler.codec.compression;

import com.github.luben.zstd.ZstdInputStream;
import io.netty5.buffer.BufferInputStream;
import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

public class ZstdEncoderTest extends AbstractEncoderTest {

    @Mock
    private ChannelHandlerContext ctx;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.initMocks(this);
        when(ctx.bufferAllocator()).thenReturn(BufferAllocator.onHeapUnpooled());
    }

    @Override
    public EmbeddedChannel createChannel() {
        return new EmbeddedChannel(new CompressionHandler(ZstdCompressor.newFactory()));
    }

    @ParameterizedTest
    @MethodSource("largeData")
    public void testCompressionOfLargeBatchedFlow(final Buffer data) throws Exception {
        final int dataLength = data.readableBytes();
        try (Buffer expected = data.copy()) {
            assertTrue(channel.writeOutbound(data.readSplit(data.readableBytes() / 2)));
            assertTrue(channel.writeOutbound(data.split()));
            assertTrue(channel.finish());

            try (Buffer decompressed = readDecompressed(dataLength)) {
                assertEquals(expected, decompressed);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("smallData")
    public void testCompressionOfSmallBatchedFlow(final Buffer data) throws Exception {
        testCompressionOfBatchedFlow(data);
    }

    @Override
    protected Buffer decompress(Buffer compressed, int originalLength) throws Exception {
        InputStream is = new BufferInputStream(compressed.send());
        ZstdInputStream zstdIs = null;
        byte[] decompressed = new byte[originalLength];
        try {
            zstdIs = new ZstdInputStream(is);
            int remaining = originalLength;
            while (remaining > 0) {
                int read = zstdIs.read(decompressed, originalLength - remaining, remaining);
                if (read > 0) {
                    remaining -= read;
                } else {
                    break;
                }
            }
            assertEquals(-1, zstdIs.read());
        } finally {
            if (zstdIs != null) {
                zstdIs.close();
            } else {
                is.close();
            }
        }

        return BufferAllocator.onHeapUnpooled().copyOf(decompressed);
    }
}
