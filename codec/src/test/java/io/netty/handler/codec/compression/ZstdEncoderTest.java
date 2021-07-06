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
package io.netty.handler.codec.compression;

import com.github.luben.zstd.ZstdInputStream;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.InputStream;


import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ZstdEncoderTest extends AbstractEncoderTest {

    @Mock
    private ChannelHandlerContext ctx;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.initMocks(this);
        when(ctx.alloc()).thenReturn(ByteBufAllocator.DEFAULT);
    }

    @Override
    public EmbeddedChannel createChannel() {
        return new EmbeddedChannel(new ZstdEncoder());
    }

    @ParameterizedTest
    @MethodSource("largeData")
    public void testCompressionOfLargeBatchedFlow(final ByteBuf data) throws Exception {
        final int dataLength = data.readableBytes();
        int written = 0;

        ByteBuf in = data.retainedSlice(written, 65535);
        assertTrue(channel.writeOutbound(in));

        ByteBuf in2 = data.retainedSlice(65535, dataLength - 65535);
        assertTrue(channel.writeOutbound(in2));

        assertTrue(channel.finish());

        ByteBuf decompressed = readDecompressed(dataLength);
        assertEquals(data, decompressed);

        decompressed.release();
        data.release();
    }

    @ParameterizedTest
    @MethodSource("smallData")
    public void testCompressionOfSmallBatchedFlow(final ByteBuf data) throws Exception {
        testCompressionOfBatchedFlow(data);
    }

    @Override
    protected ByteBuf decompress(ByteBuf compressed, int originalLength) throws Exception {
        InputStream is = new ByteBufInputStream(compressed, true);
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

        return Unpooled.wrappedBuffer(decompressed);
    }
}
