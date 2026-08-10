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
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.stream.ChunkedFile;
import io.netty.handler.stream.ChunkedInput;
import io.netty.handler.stream.ChunkedNioFile;
import io.netty.handler.stream.ChunkedNioStream;
import io.netty.handler.stream.ChunkedStream;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.util.concurrent.Future;
import io.netty.util.internal.PlatformDependent;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.Channels;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HttpChunkedInputTest {
    private static final byte[] BYTES = new byte[1024 * 64];
    private static final File TMP;

    static {
        for (int i = 0; i < BYTES.length; i++) {
            BYTES[i] = (byte) i;
        }

        try {
            TMP = PlatformDependent.createTempFile("netty-chunk-", ".tmp", null);
            TMP.deleteOnExit();
            try (FileOutputStream out = new FileOutputStream(TMP)) {
                out.write(BYTES);
                out.flush();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testChunkedStream() throws Exception {
        check(new HttpChunkedInput(new ChunkedStream(new ByteArrayInputStream(BYTES))));
    }

    @Test
    public void testChunkedNioStream() throws Exception {
        check(new HttpChunkedInput(new ChunkedNioStream(Channels.newChannel(new ByteArrayInputStream(BYTES)))));
    }

    @Test
    public void testChunkedFile() throws Exception {
        check(new HttpChunkedInput(new ChunkedFile(TMP)));
    }

    @Test
    public void testChunkedNioFile() throws Exception {
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

    @Test
    public void testCloseReleasesUnsentLastHttpContent() throws Exception {
        TestChunkedInput chunkedInput = new TestChunkedInput(false, false);
        LastHttpContent lastHttpContent = new DefaultLastHttpContent(Unpooled.buffer(1).writeByte(1));
        HttpChunkedInput input = new HttpChunkedInput(chunkedInput, lastHttpContent);
        EmbeddedChannel channel = new EmbeddedChannel(new ChunkedWriteHandler());

        try {
            Future<Void> writeFuture = channel.writeAndFlush(input);
            assertFalse(writeFuture.isDone());

            assertFalse(channel.finish());
            assertTrue(chunkedInput.closed);
            assertFalse(writeFuture.isSuccess());
            assertEquals(0, lastHttpContent.refCnt());

            input.close();
            assertEquals(0, lastHttpContent.refCnt());
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    public void testCloseDoesNotReleaseSentLastHttpContent() throws Exception {
        LastHttpContent lastHttpContent = new DefaultLastHttpContent(Unpooled.buffer(1).writeByte(1));
        HttpChunkedInput input = new HttpChunkedInput(new TestChunkedInput(true, false), lastHttpContent);
        EmbeddedChannel channel = new EmbeddedChannel(new ChunkedWriteHandler());

        try {
            assertTrue(channel.writeOutbound(input));
            assertSame(lastHttpContent, channel.readOutbound());
            assertNull(channel.readOutbound());
            assertEquals(1, lastHttpContent.refCnt());

            input.close();
            input.close();
            assertEquals(1, lastHttpContent.refCnt());
        } finally {
            lastHttpContent.release();
            channel.finishAndReleaseAll();
        }
    }

    @Test
    public void testCloseReleasesUnsentLastHttpContentWhenInputCloseThrows() throws Exception {
        LastHttpContent lastHttpContent = new DefaultLastHttpContent(Unpooled.buffer(1).writeByte(1));
        HttpChunkedInput input = new HttpChunkedInput(new TestChunkedInput(false, true), lastHttpContent);

        assertThrows(IOException.class, input::close);
        assertEquals(0, lastHttpContent.refCnt());
        assertThrows(IOException.class, input::close);
        assertEquals(0, lastHttpContent.refCnt());
    }

    private static void check(ChunkedInput<?>... inputs) throws Exception {
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
                assertTrue(lastHttpContent instanceof DefaultHttpContent, "Chunk must be DefaultHttpContent");
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
        assertSame(LastHttpContent.EMPTY_LAST_CONTENT, lastHttpContent,
                "Last chunk must be LastHttpContent.EMPTY_LAST_CONTENT");
    }

    private static final class TestChunkedInput implements ChunkedInput<ByteBuf> {
        private final boolean endOfInput;
        private final boolean failOnClose;
        private boolean closed;

        TestChunkedInput(boolean endOfInput, boolean failOnClose) {
            this.endOfInput = endOfInput;
            this.failOnClose = failOnClose;
        }

        @Override
        public boolean isEndOfInput() {
            return endOfInput;
        }

        @Override
        public void close() throws Exception {
            closed = true;
            if (failOnClose) {
                throw new IOException("close failed");
            }
        }

        @Deprecated
        @Override
        public ByteBuf readChunk(ChannelHandlerContext ctx) {
            return null;
        }

        @Override
        public ByteBuf readChunk(ByteBufAllocator allocator) {
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
    }
}
