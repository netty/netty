/*
 * Copyright 2016 The Netty Project
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
package io.netty.microbench.buffer;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.microbench.util.AbstractMicrobenchmark;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.UTF8Decoder;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

@State(Scope.Benchmark)
public class UTF8DecoderBenchmark extends AbstractMicrobenchmark {
    private static final String EXAMPLE_JSON = "[" +
            "{\"id\":\"613517d731bb47bdbc337870d4f3adc5\",\"name\":\"Techcable\"}," +
            "{\"id\":\"069a79f444e94726a5befca90e38aaf5\",\"name\":\"Notch\"}" +
            "]";
    private static final String QUICK_BROWN_FOX = "The quick brown fox jumps over the lazy dog";

    @Param({ "github", "json", "quick_brown_fox", "japan_wiki" })
    private String testData;
    private ByteBuf encoded;
    private CharBuffer charBuffer;

    private static void downloadTo(ByteBuf output, String url) throws IOException {
        URLConnection connection = new URL(url).openConnection();
        if (connection.getContentLength() >= 0) {
            output.ensureWritable((int) connection.getContentLength()); // Expand buffer
        }
        InputStream in = null;
        try {
            in = connection.getInputStream();
            byte[] buffer = new byte[2046];
            int readBytes;
            while ((readBytes = in.read(buffer)) >= 0) {
                output.writeBytes(buffer, 0, readBytes);
            }
        } finally {
            if (in != null) {
                in.close();
            }
        }
    }

    @Setup
    public void setup() throws IOException {
        encoded = ByteBufAllocator.DEFAULT.buffer();
        if (testData.equals("github")) {
            downloadTo(encoded, "https://github.com/");
        } else if (testData.equals("json")) {
            ByteBufUtil.writeUtf8(encoded, EXAMPLE_JSON);
        } else if (testData.equals("quick_brown_fox")) {
            ByteBufUtil.writeUtf8(encoded, QUICK_BROWN_FOX);
        } else if (testData.equals("japan_wiki")) {
            downloadTo(encoded, "https://ja.wikipedia.org/wiki/\u65e5\u672c");
        } else {
            throw new AssertionError("Don't know how to get test data: " + testData);
        }
        charBuffer = CharBuffer.allocate(encoded.writerIndex());
    }

    @Benchmark
    public String decodeJdk() throws CharacterCodingException {
        int size = encoded.writerIndex();
        final CharsetDecoder decoder = CharsetUtil.decoder(CharsetUtil.UTF_8);
        charBuffer.clear();
        decoder.decode(encoded.internalNioBuffer(0, size), charBuffer, true);
        decoder.flush(charBuffer);
        return charBuffer.flip().toString();
    }

    @Benchmark
    public String decodeNetty() {
        // Has cool micro-optimizations
        return ByteBufUtil.readUtf8(encoded, 0, encoded.writerIndex());
    }

    @Benchmark
    public String decodeNettyUnoptimized() {
        int size = encoded.writerIndex();
        // Naive use of UTF8Decoder without cool micro-optimizations
        UTF8Decoder.UTF8Processor processor = new UTF8Decoder.UTF8Processor(new char[size]);
        encoded.forEachByte(encoded.readerIndex(), size, processor);
        return processor.toString();
    }

    @TearDown
    public void release() {
        encoded.release();
    }

}
