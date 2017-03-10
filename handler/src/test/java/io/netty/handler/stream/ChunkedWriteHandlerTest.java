/*
 * Copyright 2012 The Netty Project
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
package io.netty.handler.stream;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.CharsetUtil;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ChunkedWriteHandlerTest {
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

    // See #310
    @Test
    public void testChunkedStream() {
        check(new ChunkedStream(new ByteArrayInputStream(BYTES)));

        check(new ChunkedStream(new ByteArrayInputStream(BYTES)),
                new ChunkedStream(new ByteArrayInputStream(BYTES)),
                new ChunkedStream(new ByteArrayInputStream(BYTES)));
    }

    @Test
    public void testChunkedNioStream() {
        check(new ChunkedNioStream(Channels.newChannel(new ByteArrayInputStream(BYTES))));

        check(new ChunkedNioStream(Channels.newChannel(new ByteArrayInputStream(BYTES))),
                new ChunkedNioStream(Channels.newChannel(new ByteArrayInputStream(BYTES))),
                new ChunkedNioStream(Channels.newChannel(new ByteArrayInputStream(BYTES))));
    }

    @Test
    public void testChunkedFile() throws IOException {
        check(new ChunkedFile(TMP));

        check(new ChunkedFile(TMP), new ChunkedFile(TMP), new ChunkedFile(TMP));
    }

    @Test
    public void testChunkedNioFile() throws IOException {
        check(new ChunkedNioFile(TMP));

        check(new ChunkedNioFile(TMP), new ChunkedNioFile(TMP), new ChunkedNioFile(TMP));
    }

    @Test
    public void testUnchunkedData() throws IOException {
        check(Unpooled.wrappedBuffer(BYTES));

        check(Unpooled.wrappedBuffer(BYTES), Unpooled.wrappedBuffer(BYTES), Unpooled.wrappedBuffer(BYTES));
    }

    // Test case which shows that there is not a bug like stated here:
    // http://stackoverflow.com/a/10426305
    @Test
    public void testListenerNotifiedWhenIsEnd() {
        ByteBuf buffer = Unpooled.copiedBuffer("Test", CharsetUtil.ISO_8859_1);

        ChunkedInput<ByteBuf> input = new ChunkedInput<ByteBuf>() {
            private boolean done;
            private final ByteBuf buffer = Unpooled.copiedBuffer("Test", CharsetUtil.ISO_8859_1);

            @Override
            public boolean isEndOfInput() throws Exception {
                return done;
            }

            @Override
            public void close() throws Exception {
                buffer.release();
            }

            @Deprecated
            @Override
            public ByteBuf readChunk(ChannelHandlerContext ctx) throws Exception {
                return readChunk(ctx.alloc());
            }

            @Override
            public ByteBuf readChunk(ByteBufAllocator allocator) throws Exception {
                if (done) {
                    return null;
                }
                done = true;
                return buffer.retainedDuplicate();
            }

            @Override
            public long length() {
                return -1;
            }

            @Override
            public long progress() {
                return 1;
            }
        };

        final AtomicBoolean listenerNotified = new AtomicBoolean(false);
        final ChannelFutureListener listener = new ChannelFutureListener() {

            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                listenerNotified.set(true);
            }
        };

        EmbeddedChannel ch = new EmbeddedChannel(new ChunkedWriteHandler());
        ch.writeAndFlush(input).addListener(listener).syncUninterruptibly();
        ch.checkException();
        ch.finish();

        // the listener should have been notified
        assertTrue(listenerNotified.get());

        ByteBuf buffer2 = ch.readOutbound();
        assertEquals(buffer, buffer2);
        assertNull(ch.readOutbound());

        buffer.release();
        buffer2.release();
    }

    @Test
    public void testChunkedMessageInput() {

        ChunkedInput<Object> input = new ChunkedInput<Object>() {
            private boolean done;

            @Override
            public boolean isEndOfInput() throws Exception {
                return done;
            }

            @Override
            public void close() throws Exception {
                // NOOP
            }

            @Deprecated
            @Override
            public Object readChunk(ChannelHandlerContext ctx) throws Exception {
                return readChunk(ctx.alloc());
            }

            @Override
            public Object readChunk(ByteBufAllocator ctx) throws Exception {
                if (done) {
                    return false;
                }
                done = true;
                return 0;
            }

            @Override
            public long length() {
                return -1;
            }

            @Override
            public long progress() {
                return 1;
            }
        };

        EmbeddedChannel ch = new EmbeddedChannel(new ChunkedWriteHandler());
        ch.writeAndFlush(input).syncUninterruptibly();
        ch.checkException();
        assertTrue(ch.finish());

        assertEquals(0, ch.readOutbound());
        assertNull(ch.readOutbound());
    }

    private static void check(Object... inputs) {
        EmbeddedChannel ch = new EmbeddedChannel(new ChunkedWriteHandler());

        for (Object input: inputs) {
            ch.writeOutbound(input);
        }

        assertTrue(ch.finish());

        int i = 0;
        int read = 0;
        for (;;) {
            ByteBuf buffer = ch.readOutbound();
            if (buffer == null) {
                break;
            }
            while (buffer.isReadable()) {
                assertEquals(BYTES[i++], buffer.readByte());
                read++;
                if (i == BYTES.length) {
                    i = 0;
                }
            }
            buffer.release();
        }

        assertEquals(BYTES.length * inputs.length, read);
    }
}
