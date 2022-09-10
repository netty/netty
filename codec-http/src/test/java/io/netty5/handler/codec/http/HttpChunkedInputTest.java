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
package io.netty5.handler.codec.http;

import io.netty5.buffer.Buffer;
import io.netty5.buffer.BufferAllocator;
import io.netty5.channel.embedded.EmbeddedChannel;
import io.netty5.handler.stream.ChunkedFile;
import io.netty5.handler.stream.ChunkedInput;
import io.netty5.handler.stream.ChunkedNioFile;
import io.netty5.handler.stream.ChunkedNioStream;
import io.netty5.handler.stream.ChunkedStream;
import io.netty5.handler.stream.ChunkedWriteHandler;
import io.netty5.util.Resource;
import io.netty5.util.internal.PlatformDependent;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.Channels;

import static io.netty5.buffer.DefaultBufferAllocators.preferredAllocator;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HttpChunkedInputTest {
    private static final byte[] BYTES = new byte[1024 * 64];
    private static final File TMP;

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
        check(new HttpChunkedInput(new ChunkedStream(new ByteArrayInputStream(BYTES)), emptyLastContent()));
    }

    @Test
    public void testChunkedNioStream() {
        check(new HttpChunkedInput(
                new ChunkedNioStream(Channels.newChannel(new ByteArrayInputStream(BYTES))), emptyLastContent()));
    }

    @Test
    public void testChunkedFile() throws IOException {
        check(new HttpChunkedInput(new ChunkedFile(TMP), emptyLastContent()));
    }

    @Test
    public void testChunkedNioFile() throws IOException {
        check(new HttpChunkedInput(new ChunkedNioFile(TMP), emptyLastContent()));
    }

    @Test
    public void testWrappedReturnNull() throws Exception {
        ChunkedInput<Buffer> inputStub = new ChunkedInput<>() {
            @Override
            public boolean isEndOfInput() throws Exception {
                return false;
            }

            @Override
            public void close() throws Exception {
                // NOOP
            }

            @Override
            public Buffer readChunk(BufferAllocator allocator) throws Exception {
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
        };
        HttpChunkedInput input = new HttpChunkedInput(inputStub, emptyLastContent());
        assertNull(input.readChunk(preferredAllocator()));
    }

    private static EmptyLastHttpContent emptyLastContent() {
        return new EmptyLastHttpContent(preferredAllocator());
    }

    private static void check(ChunkedInput<?>... inputs) {
        EmbeddedChannel ch = new EmbeddedChannel(new ChunkedWriteHandler());

        for (ChunkedInput<?> input : inputs) {
            ch.writeOutbound(input);
        }

        assertTrue(ch.finish());

        int i = 0;
        int read = 0;
        HttpContent<?> lastHttpContent = null;
        for (;;) {
            HttpContent<?> httpContent = ch.readOutbound();
            if (httpContent == null) {
                break;
            }
            if (lastHttpContent != null) {
                assertTrue(lastHttpContent instanceof DefaultHttpContent, "Chunk must be DefaultHttpContent");
            }

            Buffer buffer = httpContent.payload();
            int roff = buffer.readerOffset();
            while (buffer.readableBytes() > 0) {
                assertEquals(BYTES[i++], buffer.readByte());
                read++;
                if (i == BYTES.length) {
                    i = 0;
                }
            }
            buffer.readerOffset(roff);

            // Save last chunk
            Resource.dispose(lastHttpContent);
            lastHttpContent = httpContent;
        }

        assertEquals(BYTES.length * inputs.length, read);
        assertThat(lastHttpContent).isInstanceOf(LastHttpContent.class);
        assertThat(lastHttpContent.payload().readableBytes()).isZero();
    }
}
