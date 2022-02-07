/*
 * Copyright 2022 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec.http2;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.stream.ChunkedFile;
import io.netty.handler.stream.ChunkedInput;
import io.netty.handler.stream.ChunkedNioFile;
import io.netty.handler.stream.ChunkedNioStream;
import io.netty.handler.stream.ChunkedStream;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.util.internal.PlatformDependent;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.Channels;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Http2DataChunkedInputTest {
    private static final byte[] BYTES = new byte[1024 * 64];
    private static final File TMP;

    // Just a dummy interface implementation of stream
    private static final Http2FrameStream STREAM = new Http2FrameStream() {
        @Override
        public int id() {
            return 1;
        }

        @Override
        public Http2Stream.State state() {
            return Http2Stream.State.OPEN;
        }
    };

    static {
        for (int i = 0; i < BYTES.length; i++) {
            BYTES[i] = (byte) i;
        }

        FileOutputStream out = null;
        try {
            TMP = PlatformDependent.createTempFile("netty-chunk-", ".tmp", null);
            TMP.deleteOnExit();
            out = new FileOutputStream(TMP);
            out.write(BYTES);
            out.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    @Test
    public void testChunkedStream() {
        check(new Http2DataChunkedInput(new ChunkedStream(new ByteArrayInputStream(BYTES)), STREAM));
    }

    @Test
    public void testChunkedNioStream() {
        check(new Http2DataChunkedInput(new ChunkedNioStream(Channels.newChannel(new ByteArrayInputStream(BYTES))),
                STREAM));
    }

    @Test
    public void testChunkedFile() throws IOException {
        check(new Http2DataChunkedInput(new ChunkedFile(TMP), STREAM));
    }

    @Test
    public void testChunkedNioFile() throws IOException {
        check(new Http2DataChunkedInput(new ChunkedNioFile(TMP), STREAM));
    }

    @Test
    public void testWrappedReturnNull() throws Exception {
        Http2DataChunkedInput input = new Http2DataChunkedInput(new ChunkedInput<ByteBuf>() {

            @Override
            public boolean isEndOfInput() throws Exception {
                return false;
            }

            @Override
            public void close() throws Exception {
                // NOOP
            }

            @Override
            public ByteBuf readChunk(ChannelHandlerContext ctx) throws Exception {
                return null;
            }

            @Override
            public ByteBuf readChunk(ByteBufAllocator allocator) throws Exception {
                return null;
            }

            @Override
            public long length() {
                return 0;
            }

            @Override
            public long progress() {
                return 0;
            }
        }, STREAM);
        assertNull(input.readChunk(ByteBufAllocator.DEFAULT));
    }

    private static void check(ChunkedInput<?>... inputs) {
        EmbeddedChannel ch = new EmbeddedChannel(new ChunkedWriteHandler());

        for (ChunkedInput<?> input : inputs) {
            ch.writeOutbound(input);
        }

        assertTrue(ch.finish());

        int i = 0;
        int read = 0;
        Http2DataFrame http2DataFrame = null;
        for (;;) {
            Http2DataFrame dataFrame = ch.readOutbound();
            if (dataFrame == null) {
                break;
            }

            ByteBuf buffer = dataFrame.content();
            while (buffer.isReadable()) {
                assertEquals(BYTES[i++], buffer.readByte());
                read++;
                if (i == BYTES.length) {
                    i = 0;
                }
            }
            buffer.release();

            // Save last chunk
            http2DataFrame = dataFrame;
        }

        assertEquals(BYTES.length * inputs.length, read);
        assertNotNull(http2DataFrame);
        assertTrue(http2DataFrame.isEndStream(), "Last chunk must be Http2DataFrame#isEndStream() set to true");
    }
}
