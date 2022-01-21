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
import com.aayushatharva.brotli4j.decoder.DecoderJNI;
import com.aayushatharva.brotli4j.decoder.DirectDecompress;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.internal.PlatformDependent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.DisabledIf;

@DisabledIf(value = "isNotSupported", disabledReason = "Brotli is not supported on this platform")
public class BrotliEncoderTest extends AbstractEncoderTest {

    @BeforeAll
    static void setUp() {
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
        byte[] compressedArray = new byte[compressed.readableBytes()];
        compressed.readBytes(compressedArray);
        compressed.release();

        DirectDecompress decompress = Decoder.decompress(compressedArray);
        if (decompress.getResultStatus() == DecoderJNI.Status.ERROR) {
            throw new DecompressionException("Brotli stream corrupted");
        }

        byte[] decompressed = decompress.getDecompressedData();
        return Unpooled.wrappedBuffer(decompressed);
    }

    @Override
    protected ByteBuf readDecompressed(final int dataLength) throws Exception {
        CompositeByteBuf decompressed = Unpooled.compositeBuffer();
        ByteBuf msg;
        while ((msg = channel.readOutbound()) != null) {
            if (msg.isReadable()) {
                decompressed.addComponent(true, decompress(msg, -1));
            } else {
                msg.release();
            }
        }
        return decompressed;
    }

    static boolean isNotSupported() {
        return PlatformDependent.isOsx() && "aarch_64".equals(PlatformDependent.normalizedArch());
    }
}
