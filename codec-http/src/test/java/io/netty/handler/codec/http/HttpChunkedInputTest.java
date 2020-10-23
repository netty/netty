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
package io.netty.handler.codec.http;

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
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.Channels;

import static org.junit.Assert.*;

public class HttpChunkedInputTest {
    private static final byte[] BYTES = new byte[1024 * 64];
    private static final File TMP;

    static {
        for (int i = 0; i < BYTES.length; i++) {
            BYTES[i] = (byte) i;
        }

        FileOutputStream out = null;
        try {
            TMP = File.createTempFile("netty-chunk-", ".tmp");
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
        check(new HttpChunkedInput(new ChunkedStream(new ByteArrayInputStream(BYTES))));
    }

    @Test
    public void testChunkedNioStream() {
        check(new HttpChunkedInput(new ChunkedNioStream(Channels.newChannel(new ByteArrayInputStream(BYTES)))));
    }

    @Test
    public void testChunkedFile() throws IOException {
        check(new HttpChunkedInput(new ChunkedFile(TMP)));
    }

    @Test
    public void testChunkedNioFile() throws IOException {
        check(new HttpChunkedInput(new ChunkedNioFile(TMP)));
    }

    @Test
    public void testWrappedReturnNull() throws Exception {
        HttpChunkedInput input = new HttpChunkedInput(new ChunkedInput<ByteBuf>() {
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
        });
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
        HttpContent lastHttpContent = null;
        for (;;) {
            HttpContent httpContent = ch.readOutbound();
            if (httpContent == null) {
                break;
            }
            if (lastHttpContent != null) {
                assertTrue("Chunk must be DefaultHttpContent", lastHttpContent instanceof DefaultHttpContent);
            }

            ByteBuf buffer = httpContent.content();
            while (buffer.isReadable()) {
                assertEquals(BYTES[i++], buffer.readByte());
                read++;
                if (i == BYTES.length) {
                    i = 0;
                }
            }
            buffer.release();

            // Save last chunk
            lastHttpContent = httpContent;
        }

        assertEquals(BYTES.length * inputs.length, read);
        assertSame("Last chunk must be LastHttpContent.EMPTY_LAST_CONTENT",
                LastHttpContent.EMPTY_LAST_CONTENT, lastHttpContent);
    }
}
