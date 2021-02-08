/*
 * Copyright 2012 The Netty Project
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
package io.netty.handler.stream;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.PlatformDependent;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.Channels;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.TimeUnit.*;
import static org.junit.Assert.*;

public class ChunkedWriteHandlerTest {
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
    public void testChunkedNioFileLeftPositionUnchanged() throws IOException {
        FileChannel in = null;
        final long expectedPosition = 10;
        try {
            in = new RandomAccessFile(TMP, "r").getChannel();
            in.position(expectedPosition);
            check(new ChunkedNioFile(in) {
                @Override
                public void close() throws Exception {
                    //no op
                }
            });
            Assert.assertTrue(in.isOpen());
            Assert.assertEquals(expectedPosition, in.position());
        } finally {
            if (in != null) {
                in.close();
            }
        }
    }

    @Test(expected = ClosedChannelException.class)
    public void testChunkedNioFileFailOnClosedFileChannel() throws IOException {
        final FileChannel in = new RandomAccessFile(TMP, "r").getChannel();
        in.close();
        check(new ChunkedNioFile(in) {
            @Override
            public void close() throws Exception {
                //no op
            }
        });
        Assert.fail();
    }

    @Test
    public void testUnchunkedData() throws IOException {
        check(Unpooled.wrappedBuffer(BYTES));

        check(Unpooled.wrappedBuffer(BYTES), Unpooled.wrappedBuffer(BYTES), Unpooled.wrappedBuffer(BYTES));
    }

    // Test case which shows that there is not a bug like stated here:
    // https://stackoverflow.com/a/10426305
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
        assertTrue(ch.finish());

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
        assertTrue(ch.finish());

        assertEquals(0, ch.readOutbound());
        assertNull(ch.readOutbound());
    }

    @Test
    public void testWriteFailureChunkedStream() throws IOException {
        checkFirstFailed(new ChunkedStream(new ByteArrayInputStream(BYTES)));
    }

    @Test
    public void testWriteFailureChunkedNioStream() throws IOException {
        checkFirstFailed(new ChunkedNioStream(Channels.newChannel(new ByteArrayInputStream(BYTES))));
    }

    @Test
    public void testWriteFailureChunkedFile() throws IOException {
        checkFirstFailed(new ChunkedFile(TMP));
    }

    @Test
    public void testWriteFailureChunkedNioFile() throws IOException {
        checkFirstFailed(new ChunkedNioFile(TMP));
    }

    @Test
    public void testWriteFailureUnchunkedData() throws IOException {
        checkFirstFailed(Unpooled.wrappedBuffer(BYTES));
    }

    @Test
    public void testSkipAfterFailedChunkedStream() throws IOException {
        checkSkipFailed(new ChunkedStream(new ByteArrayInputStream(BYTES)),
                        new ChunkedStream(new ByteArrayInputStream(BYTES)));
    }

    @Test
    public void testSkipAfterFailedChunkedNioStream() throws IOException {
        checkSkipFailed(new ChunkedNioStream(Channels.newChannel(new ByteArrayInputStream(BYTES))),
                        new ChunkedNioStream(Channels.newChannel(new ByteArrayInputStream(BYTES))));
    }

    @Test
    public void testSkipAfterFailedChunkedFile() throws IOException {
        checkSkipFailed(new ChunkedFile(TMP), new ChunkedFile(TMP));
    }

    @Test
    public void testSkipAfterFailedChunkedNioFile() throws IOException {
        checkSkipFailed(new ChunkedNioFile(TMP), new ChunkedFile(TMP));
    }

    // See https://github.com/netty/netty/issues/8700.
    @Test
    public void testFailureWhenLastChunkFailed() throws IOException {
        ChannelOutboundHandlerAdapter failLast = new ChannelOutboundHandlerAdapter() {
            private int passedWrites;

            @Override
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
                if (++this.passedWrites < 4) {
                    ctx.write(msg, promise);
                } else {
                    ReferenceCountUtil.release(msg);
                    promise.tryFailure(new RuntimeException());
                }
            }
        };

        EmbeddedChannel ch = new EmbeddedChannel(failLast, new ChunkedWriteHandler());
        ChannelFuture r = ch.writeAndFlush(new ChunkedFile(TMP, 1024 * 16)); // 4 chunks
        assertTrue(ch.finish());

        assertFalse(r.isSuccess());
        assertTrue(r.cause() instanceof RuntimeException);

        // 3 out of 4 chunks were already written
        int read = 0;
        for (;;) {
            ByteBuf buffer = ch.readOutbound();
            if (buffer == null) {
                break;
            }
            read += buffer.readableBytes();
            buffer.release();
        }

        assertEquals(1024 * 16 * 3, read);
    }

    @Test
    public void testDiscardPendingWritesOnInactive() throws IOException {

        final AtomicBoolean closeWasCalled = new AtomicBoolean(false);

        ChunkedInput<ByteBuf> notifiableInput = new ChunkedInput<ByteBuf>() {
            private boolean done;
            private final ByteBuf buffer = Unpooled.copiedBuffer("Test", CharsetUtil.ISO_8859_1);

            @Override
            public boolean isEndOfInput() throws Exception {
                return done;
            }

            @Override
            public void close() throws Exception {
                buffer.release();
                closeWasCalled.set(true);
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

        EmbeddedChannel ch = new EmbeddedChannel(new ChunkedWriteHandler());

        // Write 3 messages and close channel before flushing
        ChannelFuture r1 = ch.write(new ChunkedFile(TMP));
        ChannelFuture r2 = ch.write(new ChunkedNioFile(TMP));
        ch.write(notifiableInput);

        // Should be `false` as we do not expect any messages to be written
        assertFalse(ch.finish());

        assertFalse(r1.isSuccess());
        assertFalse(r2.isSuccess());
        assertTrue(closeWasCalled.get());
    }

    // See https://github.com/netty/netty/issues/8700.
    @Test
    public void testStopConsumingChunksWhenFailed() {
        final ByteBuf buffer = Unpooled.copiedBuffer("Test", CharsetUtil.ISO_8859_1);
        final AtomicInteger chunks = new AtomicInteger(0);

        ChunkedInput<ByteBuf> nonClosableInput = new ChunkedInput<ByteBuf>() {
            @Override
            public boolean isEndOfInput() throws Exception {
                return chunks.get() >= 5;
            }

            @Override
            public void close() throws Exception {
                // no-op
            }

            @Deprecated
            @Override
            public ByteBuf readChunk(ChannelHandlerContext ctx) throws Exception {
                return readChunk(ctx.alloc());
            }

            @Override
            public ByteBuf readChunk(ByteBufAllocator allocator) throws Exception {
                chunks.incrementAndGet();
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

        ChannelOutboundHandlerAdapter noOpWrites = new ChannelOutboundHandlerAdapter() {
            @Override
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
                ReferenceCountUtil.release(msg);
                promise.tryFailure(new RuntimeException());
            }
        };

        EmbeddedChannel ch = new EmbeddedChannel(noOpWrites, new ChunkedWriteHandler());
        ch.writeAndFlush(nonClosableInput).awaitUninterruptibly();
        // Should be `false` as we do not expect any messages to be written
        assertFalse(ch.finish());
        buffer.release();

        // We should expect only single chunked being read from the input.
        // It's possible to get a race condition here between resolving a promise and
        // allocating a new chunk, but should be fine when working with embedded channels.
        assertEquals(1, chunks.get());
    }

    @Test
    public void testCloseSuccessfulChunkedInput() {
        int chunks = 10;
        TestChunkedInput input = new TestChunkedInput(chunks);
        EmbeddedChannel ch = new EmbeddedChannel(new ChunkedWriteHandler());

        assertTrue(ch.writeOutbound(input));

        for (int i = 0; i < chunks; i++) {
            ByteBuf buf = ch.readOutbound();
            assertEquals(i, buf.readInt());
            buf.release();
        }

        assertTrue(input.isClosed());
        assertFalse(ch.finish());
    }

    @Test
    public void testCloseFailedChunkedInput() {
        Exception error = new Exception("Unable to produce a chunk");
        ThrowingChunkedInput input = new ThrowingChunkedInput(error);

        EmbeddedChannel ch = new EmbeddedChannel(new ChunkedWriteHandler());

        try {
            ch.writeOutbound(input);
            fail("Exception expected");
        } catch (Exception e) {
            assertEquals(error, e);
        }

        assertTrue(input.isClosed());
        assertFalse(ch.finish());
    }

    @Test
    public void testWriteListenerInvokedAfterSuccessfulChunkedInputClosed() throws Exception {
        final TestChunkedInput input = new TestChunkedInput(2);
        EmbeddedChannel ch = new EmbeddedChannel(new ChunkedWriteHandler());

        final AtomicBoolean inputClosedWhenListenerInvoked = new AtomicBoolean();
        final CountDownLatch listenerInvoked = new CountDownLatch(1);

        ChannelFuture writeFuture = ch.write(input);
        writeFuture.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) {
                inputClosedWhenListenerInvoked.set(input.isClosed());
                listenerInvoked.countDown();
            }
        });
        ch.flush();

        assertTrue(listenerInvoked.await(10, SECONDS));
        assertTrue(writeFuture.isSuccess());
        assertTrue(inputClosedWhenListenerInvoked.get());
        assertTrue(ch.finishAndReleaseAll());
    }

    @Test
    public void testWriteListenerInvokedAfterFailedChunkedInputClosed() throws Exception {
        final ThrowingChunkedInput input = new ThrowingChunkedInput(new RuntimeException());
        EmbeddedChannel ch = new EmbeddedChannel(new ChunkedWriteHandler());

        final AtomicBoolean inputClosedWhenListenerInvoked = new AtomicBoolean();
        final CountDownLatch listenerInvoked = new CountDownLatch(1);

        ChannelFuture writeFuture = ch.write(input);
        writeFuture.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) {
                inputClosedWhenListenerInvoked.set(input.isClosed());
                listenerInvoked.countDown();
            }
        });
        ch.flush();

        assertTrue(listenerInvoked.await(10, SECONDS));
        assertFalse(writeFuture.isSuccess());
        assertTrue(inputClosedWhenListenerInvoked.get());
        assertFalse(ch.finish());
    }

    @Test
    public void testWriteListenerInvokedAfterChannelClosedAndInputFullyConsumed() throws Exception {
        // use empty input which has endOfInput = true
        final TestChunkedInput input = new TestChunkedInput(0);
        EmbeddedChannel ch = new EmbeddedChannel(new ChunkedWriteHandler());

        final AtomicBoolean inputClosedWhenListenerInvoked = new AtomicBoolean();
        final CountDownLatch listenerInvoked = new CountDownLatch(1);

        ChannelFuture writeFuture = ch.write(input);
        writeFuture.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) {
                inputClosedWhenListenerInvoked.set(input.isClosed());
                listenerInvoked.countDown();
            }
        });
        ch.close(); // close channel to make handler discard the input on subsequent flush
        ch.flush();

        assertTrue(listenerInvoked.await(10, SECONDS));
        assertTrue(writeFuture.isSuccess());
        assertTrue(inputClosedWhenListenerInvoked.get());
        assertFalse(ch.finish());
    }

    @Test
    public void testWriteListenerInvokedAfterChannelClosedAndInputNotFullyConsumed() throws Exception {
        // use non-empty input which has endOfInput = false
        final TestChunkedInput input = new TestChunkedInput(42);
        EmbeddedChannel ch = new EmbeddedChannel(new ChunkedWriteHandler());

        final AtomicBoolean inputClosedWhenListenerInvoked = new AtomicBoolean();
        final CountDownLatch listenerInvoked = new CountDownLatch(1);

        ChannelFuture writeFuture = ch.write(input);
        writeFuture.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) {
                inputClosedWhenListenerInvoked.set(input.isClosed());
                listenerInvoked.countDown();
            }
        });
        ch.close(); // close channel to make handler discard the input on subsequent flush
        ch.flush();

        assertTrue(listenerInvoked.await(10, SECONDS));
        assertFalse(writeFuture.isSuccess());
        assertTrue(inputClosedWhenListenerInvoked.get());
        assertFalse(ch.finish());
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

    private static void checkFirstFailed(Object input) {
        ChannelOutboundHandlerAdapter noOpWrites = new ChannelOutboundHandlerAdapter() {
            @Override
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
                ReferenceCountUtil.release(msg);
                promise.tryFailure(new RuntimeException());
            }
        };

        EmbeddedChannel ch = new EmbeddedChannel(noOpWrites, new ChunkedWriteHandler());
        ChannelFuture r = ch.writeAndFlush(input);

        // Should be `false` as we do not expect any messages to be written
        assertFalse(ch.finish());
        assertTrue(r.cause() instanceof RuntimeException);
    }

    private static void checkSkipFailed(Object input1, Object input2) {
        ChannelOutboundHandlerAdapter failFirst = new ChannelOutboundHandlerAdapter() {
            private boolean alreadyFailed;

            @Override
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
                if (alreadyFailed) {
                    ctx.write(msg, promise);
                } else {
                    this.alreadyFailed = true;
                    ReferenceCountUtil.release(msg);
                    promise.tryFailure(new RuntimeException());
                }
            }
        };

        EmbeddedChannel ch = new EmbeddedChannel(failFirst, new ChunkedWriteHandler());
        ChannelFuture r1 = ch.write(input1);
        ChannelFuture r2 = ch.writeAndFlush(input2).awaitUninterruptibly();
        assertTrue(ch.finish());

        assertTrue(r1.cause() instanceof RuntimeException);
        assertTrue(r2.isSuccess());

        // note, that after we've "skipped" the first write,
        // we expect to see the second message, chunk by chunk
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

        assertEquals(BYTES.length, read);
    }

    private static final class TestChunkedInput implements ChunkedInput<ByteBuf> {
        private final int chunksToProduce;

        private int chunksProduced;
        private volatile boolean closed;

        TestChunkedInput(int chunksToProduce) {
            this.chunksToProduce = chunksToProduce;
        }

        @Override
        public boolean isEndOfInput() {
            return chunksProduced >= chunksToProduce;
        }

        @Override
        public void close() {
            closed = true;
        }

        @Override
        public ByteBuf readChunk(ChannelHandlerContext ctx) {
            return readChunk(ctx.alloc());
        }

        @Override
        public ByteBuf readChunk(ByteBufAllocator allocator) {
            ByteBuf buf = allocator.buffer();
            buf.writeInt(chunksProduced);
            chunksProduced++;
            return buf;
        }

        @Override
        public long length() {
            return chunksToProduce;
        }

        @Override
        public long progress() {
            return chunksProduced;
        }

        boolean isClosed() {
            return closed;
        }
    }

    private static final class ThrowingChunkedInput implements ChunkedInput<ByteBuf> {
        private final Exception error;

        private volatile boolean closed;

        ThrowingChunkedInput(Exception error) {
            this.error = error;
        }

        @Override
        public boolean isEndOfInput() {
            return false;
        }

        @Override
        public void close() {
            closed = true;
        }

        @Override
        public ByteBuf readChunk(ChannelHandlerContext ctx) throws Exception {
            return readChunk(ctx.alloc());
        }

        @Override
        public ByteBuf readChunk(ByteBufAllocator allocator) throws Exception {
            throw error;
        }

        @Override
        public long length() {
            return -1;
        }

        @Override
        public long progress() {
            return -1;
        }

        boolean isClosed() {
            return closed;
        }
    }
}
