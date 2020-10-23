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
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;

import java.io.InputStream;

import static io.netty.handler.codec.compression.Bzip2Constants.*;
import static org.junit.Assert.*;

public class Bzip2EncoderTest extends AbstractEncoderTest {

    @Override
    public void initChannel() {
        channel = new EmbeddedChannel(new Bzip2Encoder(MIN_BLOCK_SIZE));
    }

    @Override
    protected ByteBuf decompress(ByteBuf compressed, int originalLength) throws Exception {
        InputStream is = new ByteBufInputStream(compressed, true);
        BZip2CompressorInputStream bzip2Is = null;
        byte[] decompressed = new byte[originalLength];
        try {
            bzip2Is = new BZip2CompressorInputStream(is);
            int remaining = originalLength;
            while (remaining > 0) {
                int read = bzip2Is.read(decompressed, originalLength - remaining, remaining);
                if (read > 0) {
                    remaining -= read;
                } else {
                    break;
                }
            }
            assertEquals(-1, bzip2Is.read());
        } finally {
            if (bzip2Is != null) {
                bzip2Is.close();
            } else {
                is.close();
            }
        }

        return Unpooled.wrappedBuffer(decompressed);
    }
}
