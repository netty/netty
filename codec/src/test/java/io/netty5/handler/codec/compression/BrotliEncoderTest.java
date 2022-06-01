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

import com.aayushatharva.brotli4j.decoder.Decoder;
import com.aayushatharva.brotli4j.decoder.DecoderJNI;
import com.aayushatharva.brotli4j.decoder.DirectDecompress;
import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.channel.embedded.EmbeddedChannel;
import io.netty5.util.internal.PlatformDependent;
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
        return new EmbeddedChannel(new CompressionHandler(BrotliCompressor.newFactory()));
    }

    @Override
    protected Buffer decompress(Buffer compressed, int originalLength) throws Exception {
        try (compressed) {
            byte[] compressedArray = new byte[compressed.readableBytes()];
            compressed.readBytes(compressedArray, 0, compressedArray.length);

            DirectDecompress decompress = Decoder.decompress(compressedArray);
            if (decompress.getResultStatus() == DecoderJNI.Status.ERROR) {
                throw new DecompressionException("Brotli stream corrupted");
            }

            byte[] decompressed = decompress.getDecompressedData();
            return BufferAllocator.onHeapUnpooled().copyOf(decompressed);
        }
    }

    @Override
    protected Buffer readDecompressed(final int dataLength) throws Exception {
        return CompressionTestUtils.compose(channel.bufferAllocator(), () -> {
            for (;;) {
                Buffer msg = channel.readOutbound();
                if (msg == null) {
                    return null;
                }
                if (msg.readableBytes() == 0) {
                    msg.close();
                } else {
                    try {
                        return decompress(msg, -1);
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                }
            }
        });
    }

    static boolean isNotSupported() {
        return PlatformDependent.isOsx() && "aarch_64".equals(PlatformDependent.normalizedArch());
    }
}
