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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class AbstractEncoderTest extends AbstractCompressionTest {

    protected EmbeddedChannel channel;

    /**
     * Decompresses data with some external library.
     */
    protected abstract ByteBuf decompress(ByteBuf compressed, int originalLength) throws Exception;

    @BeforeEach
    public final void initChannel() {
        channel = createChannel();
    }

    protected abstract EmbeddedChannel createChannel();

    @AfterEach
    public void destroyChannel() {
        if (channel != null) {
            channel.finishAndReleaseAll();
            channel = null;
        }
    }

    public static ByteBuf[] smallData() {
        ByteBuf heap = Unpooled.wrappedBuffer(BYTES_SMALL);
        ByteBuf direct = Unpooled.directBuffer(BYTES_SMALL.length);
        direct.writeBytes(BYTES_SMALL);
        return new ByteBuf[] {heap, direct};
    }

    public static ByteBuf[] largeData() {
        ByteBuf heap = Unpooled.wrappedBuffer(BYTES_LARGE);
        ByteBuf direct = Unpooled.directBuffer(BYTES_LARGE.length);
        direct.writeBytes(BYTES_LARGE);
        return new ByteBuf[] {heap, direct};
    }

    @ParameterizedTest
    @MethodSource("smallData")
    public void testCompressionOfSmallChunkOfData(ByteBuf data) throws Exception {
        testCompression(data);
    }

    @ParameterizedTest
    @MethodSource("largeData")
    public void testCompressionOfLargeChunkOfData(ByteBuf data) throws Exception {
        testCompression(data);
    }

    @ParameterizedTest
    @MethodSource("largeData")
    public void testCompressionOfBatchedFlowOfData(ByteBuf data) throws Exception {
        testCompressionOfBatchedFlow(data);
    }

    protected void testCompression(final ByteBuf data) throws Exception {
        final int dataLength = data.readableBytes();
        assertTrue(channel.writeOutbound(data.retain()));
        assertTrue(channel.finish());
        assertEquals(0, data.readableBytes());

        ByteBuf decompressed = readDecompressed(dataLength);
        assertEquals(data.resetReaderIndex(), decompressed);

        decompressed.release();
        data.release();
    }

    protected void testCompressionOfBatchedFlow(final ByteBuf data) throws Exception {
        final int dataLength = data.readableBytes();
        int written = 0, length = rand.nextInt(100);
        while (written + length < dataLength) {
            ByteBuf in = data.retainedSlice(written, length);
            assertTrue(channel.writeOutbound(in));
            assertEquals(0, in.readableBytes());
            written += length;
            length = rand.nextInt(100);
        }
        ByteBuf in = data.retainedSlice(written, dataLength - written);
        assertTrue(channel.writeOutbound(in));
        assertTrue(channel.finish());
        assertEquals(0, in.readableBytes());

        ByteBuf decompressed = readDecompressed(dataLength);
        assertEquals(data, decompressed);

        decompressed.release();
        data.release();
    }

    protected ByteBuf readDecompressed(final int dataLength) throws Exception {
        CompositeByteBuf compressed = Unpooled.compositeBuffer();
        ByteBuf msg;
        while ((msg = channel.readOutbound()) != null) {
            compressed.addComponent(true, msg);
        }
        return decompress(compressed, dataLength);
    }
}
