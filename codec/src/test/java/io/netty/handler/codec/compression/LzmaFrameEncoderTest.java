/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.compression;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import lzma.sdk.lzma.Decoder;
import lzma.streams.LzmaInputStream;
import org.junit.experimental.theories.FromDataPoints;
import org.junit.experimental.theories.Theory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LzmaFrameEncoderTest extends AbstractEncoderTest {

    @Override
    public void initChannel() {
        channel = new EmbeddedChannel(new LzmaFrameEncoder());
    }

    @Theory
    @Override
    public void testCompressionOfBatchedFlowOfData(@FromDataPoints("smallData") ByteBuf data) throws Exception {
        testCompressionOfBatchedFlow(data);
    }

    @Override
    protected void testCompressionOfBatchedFlow(final ByteBuf data) throws Exception {
        List<Integer> originalLengths = new ArrayList<Integer>();
        final int dataLength = data.readableBytes();
        int written = 0, length = rand.nextInt(50);
        while (written + length < dataLength) {
            ByteBuf in = data.retainedSlice(written, length);
            assertTrue(channel.writeOutbound(in));
            written += length;
            originalLengths.add(length);
            length = rand.nextInt(50);
        }
        length = dataLength - written;
        ByteBuf in = data.retainedSlice(written, dataLength - written);
        originalLengths.add(length);
        assertTrue(channel.writeOutbound(in));
        assertTrue(channel.finish());

        CompositeByteBuf decompressed = Unpooled.compositeBuffer();
        ByteBuf msg;
        int i = 0;
        while ((msg = channel.readOutbound()) != null) {
            ByteBuf decompressedMsg = decompress(msg, originalLengths.get(i++));
            decompressed.addComponent(true, decompressedMsg);
            msg.release();
        }
        assertEquals(originalLengths.size(), i);
        assertEquals(data, decompressed);

        decompressed.release();
        data.release();
    }

    @Override
    protected ByteBuf decompress(ByteBuf compressed, int originalLength) throws Exception {
        InputStream is = new ByteBufInputStream(compressed);
        LzmaInputStream lzmaIs = new LzmaInputStream(is, new Decoder());

        byte[] decompressed = new byte[originalLength];
        int remaining = originalLength;
        while (remaining > 0) {
            int read = lzmaIs.read(decompressed, originalLength - remaining, remaining);
            if (read > 0) {
                remaining -= read;
            } else {
                break;
            }
        }
        assertEquals(-1, lzmaIs.read());
        lzmaIs.close();

        return Unpooled.wrappedBuffer(decompressed);
    }
}
