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

import com.aayushatharva.brotli4j.decoder.Decoder;
import com.aayushatharva.brotli4j.decoder.DirectDecompress;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;

public class BrotliEncoderTest extends AbstractEncoderTest {

    static {
        try {
            Brotli.ensureAvailability();
        } catch (Throwable throwable) {
            throw new ExceptionInInitializerError(throwable);
        }
    }

    @Override
    public EmbeddedChannel createChannel() {
        return new EmbeddedChannel(new BrotliEncoder());
    }

    @Override
    protected ByteBuf decompress(ByteBuf compressed, int originalLength) throws Exception {
        System.out.println(compressed);
        byte[] compressedArray = new byte[compressed.readableBytes()];
        compressed.readBytes(compressedArray);
        compressed.release();

        DirectDecompress decompress = Decoder.decompress(compressedArray);
        System.out.println(decompress.getResultStatus());
        byte[] decompressed = Decoder.decompress(compressedArray).getDecompressedData();
        return Unpooled.wrappedBuffer(decompressed);
    }
}
