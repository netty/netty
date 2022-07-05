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
package io.netty5.handler.stream;

import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.util.Resource;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.embedded.EmbeddedChannel;
import io.netty5.util.CharsetUtil;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.FutureListener;
import io.netty5.util.internal.PlatformDependent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static io.netty5.buffer.api.DefaultBufferAllocators.onHeapAllocator;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        final long expectedPosition = 10;
        try (FileChannel in = FileChannel.open(TMP.toPath(), StandardOpenOption.READ)) {
            in.position(expectedPosition);
            check(new ChunkedNioFile(in) {
                @Override
                public void close() throws Exception {
                    //no op
                }
            });
            assertTrue(in.isOpen());
            assertEquals(expectedPosition, in.position());
        }
    }

    @Test
    public void testChunkedNioFileFailOnClosedFileChannel() throws IOException {
        final FileChannel in = FileChannel.open(TMP.toPath(), StandardOpenOption.READ);
        in.close();

        assertThrows(ClosedChannelException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                check(new ChunkedNioFile(in) {
                    @Override
                    public void close() throws Exception {
                        //no op
                    }
                });
            }
        });
    }

    @Test
    public void testUnchunkedData() throws IOException {
        check(onHeapAllocator().copyOf(BYTES));

        check(onHeapAllocator().copyOf(BYTES), onHeapAllocator().copyOf(BYTES), onHeapAllocator().copyOf(BYTES));
    }

    // Test case which shows that there is not a bug like stated here:
    // https://stackoverflow.com/a/10426305
    @Test
    public void testListenerNotifiedWhenIsEnd() throws Exception {
        Buffer buffer = onHeapAllocator().copyOf("Test".getBytes(CharsetUtil.ISO_8859_1));

        ChunkedInput<Buffer> input = new ChunkedInput<Buffer>() {
            private boolean done;
            private final Buffer buffer = onHeapAllocator().copyOf("Test".getBytes(CharsetUtil.ISO_8859_1));

            @Override
            public boolean isEndOfInput() throws Exception {
                return done;
            }

            @Override
            public void close() throws Exception {
                buffer.close();
            }

            @Override
            public Buffer readChunk(BufferAllocator allocator) throws Exception {
                if (done) {
                    return null;
                }
                done = true;
                return buffer.copy();
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
        final FutureListener<Void> listener = future -> listenerNotified.set(true);

        EmbeddedChannel ch = new EmbeddedChannel(new ChunkedWriteHandler());
        ch.writeAndFlush(input).addListener(listener).sync();
        assertTrue(ch.finish());

        // the listener should have been notified
        assertTrue(listenerNotified.get());

        Buffer buffer2 = ch.readOutbound();
        assertEquals(buffer, buffer2);
        assertNull(ch.readOutbound());

        buffer.close();
        buffer2.close();
    }

    @Test
    public void testChunkedMessageInput() throws Exception {

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

            @Override
            public Object readChunk(BufferAllocator ctx) throws Exception {
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
        ch.writeAndFlush(input).sync();
        assertTrue(ch.finish());

        assertEquals(0, (Integer) ch.readOutbound());
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
        checkFirstFailed(onHeapAllocator().copyOf(BYTES));
    }

    @Test
    public void testSkipAfterFailedChunkedStream() throws Exception {
        checkSkipFailed(new ChunkedStream(new ByteArrayInputStream(BYTES)),
                        new ChunkedStream(new ByteArrayInputStream(BYTES)));
    }

    @Test
    public void testSkipAfterFailedChunkedNioStream() throws Exception {
        checkSkipFailed(new ChunkedNioStream(Channels.newChannel(new ByteArrayInputStream(BYTES))),
                        new ChunkedNioStream(Channels.newChannel(new ByteArrayInputStream(BYTES))));
    }

    @Test
    public void testSkipAfterFailedChunkedFile() throws Exception {
        checkSkipFailed(new ChunkedFile(TMP), new ChunkedFile(TMP));
    }

    @Test
    public void testSkipAfterFailedChunkedNioFile() throws Exception {
        checkSkipFailed(new ChunkedNioFile(TMP), new ChunkedFile(TMP));
    }

    // See https://github.com/netty/netty/issues/8700.
    @Test
    public void testFailureWhenLastChunkFailed() throws IOException {
        ChannelHandler failLast = new ChannelHandler() {
            private int passedWrites;

            @Override
            public Future<Void> write(ChannelHandlerContext ctx, Object msg) {
                if (++passedWrites < 4) {
                    return ctx.write(msg);
                }
                Resource.dispose(msg);
                return ctx.newFailedFuture(new RuntimeException());
            }
        };

        EmbeddedChannel ch = new EmbeddedChannel(failLast, new ChunkedWriteHandler());
        Future<Void> r = ch.writeAndFlush(new ChunkedFile(TMP, 1024 * 16)); // 4 chunks
        assertTrue(ch.finish());

        assertFalse(r.isSuccess());
        assertTrue(r.cause() instanceof RuntimeException);

        // 3 out of 4 chunks were already written
        int read = 0;
        for (;;) {
            Buffer buffer = ch.readOutbound();
            if (buffer == null) {
                break;
            }
            read += buffer.readableBytes();
            buffer.close();
        }

        assertEquals(1024 * 16 * 3, read);
    }

    @Test
    public void testDiscardPendingWritesOnInactive() throws IOException {

        final AtomicBoolean closeWasCalled = new AtomicBoolean(false);

        ChunkedInput<Buffer> notifiableInput = new ChunkedInput<Buffer>() {
            private boolean done;
            private final Buffer buffer = onHeapAllocator().copyOf("Test".getBytes(CharsetUtil.ISO_8859_1));

            @Override
            public boolean isEndOfInput() throws Exception {
                return done;
            }

            @Override
            public void close() throws Exception {
                buffer.close();
                closeWasCalled.set(true);
            }

            @Override
            public Buffer readChunk(BufferAllocator allocator) throws Exception {
                if (done) {
                    return null;
                }
                done = true;
                return buffer.copy();
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
        Future<Void> r1 = ch.write(new ChunkedFile(TMP));
        Future<Void> r2 = ch.write(new ChunkedNioFile(TMP));
        ch.write(notifiableInput);

        // Should be `false` as we do not expect any messages to be written
        assertFalse(ch.finish());

        assertFalse(r1.isSuccess());
        assertFalse(r2.isSuccess());
        assertTrue(closeWasCalled.get());
    }

    // See https://github.com/netty/netty/issues/8700.
    @Test
    public void testStopConsumingChunksWhenFailed() throws Exception {
        final Buffer buffer = onHeapAllocator().copyOf("Test".getBytes(CharsetUtil.ISO_8859_1));
        final AtomicInteger chunks = new AtomicInteger(0);

        ChunkedInput<Buffer> nonClosableInput = new ChunkedInput<Buffer>() {
            @Override
            public boolean isEndOfInput() throws Exception {
                return chunks.get() >= 5;
            }

            @Override
            public void close() throws Exception {
                // no-op
            }

            @Override
            public Buffer readChunk(BufferAllocator allocator) throws Exception {
                chunks.incrementAndGet();
                return buffer.copy();
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

        ChannelHandler noOpWrites = new ChannelHandler() {
            @Override
            public Future<Void> write(ChannelHandlerContext ctx, Object msg) {
                Resource.dispose(msg);
                return ctx.newFailedFuture(new RuntimeException());
            }
        };

        EmbeddedChannel ch = new EmbeddedChannel(noOpWrites, new ChunkedWriteHandler());
        ch.writeAndFlush(nonClosableInput).asStage().await();
        // Should be `false` as we do not expect any messages to be written
        assertFalse(ch.finish());
        buffer.close();

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
            Buffer buf = ch.readOutbound();
            assertEquals(i, buf.readInt());
            buf.close();
        }

        assertTrue(input.isClosed());
        assertFalse(ch.finish());
    }

    @Test
    public void testCloseFailedChunkedInput() {
        Exception error = new Exception("Unable to produce a chunk");
        final ThrowingChunkedInput input = new ThrowingChunkedInput(error);
        final EmbeddedChannel ch = new EmbeddedChannel(new ChunkedWriteHandler());

        Exception e = assertThrows(Exception.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                ch.writeOutbound(input);
            }
        });
        assertEquals(error, e);

        assertTrue(input.isClosed());
        assertFalse(ch.finish());
    }

    @Test
    public void testWriteListenerInvokedAfterSuccessfulChunkedInputClosed() throws Exception {
        final TestChunkedInput input = new TestChunkedInput(2);
        EmbeddedChannel ch = new EmbeddedChannel(new ChunkedWriteHandler());

        final AtomicBoolean inputClosedWhenListenerInvoked = new AtomicBoolean();
        final CountDownLatch listenerInvoked = new CountDownLatch(1);

        Future<Void> writeFuture = ch.write(input);
        writeFuture.addListener(future -> {
            inputClosedWhenListenerInvoked.set(input.isClosed());
            listenerInvoked.countDown();
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

        Future<Void> writeFuture = ch.write(input);
        writeFuture.addListener(future -> {
            inputClosedWhenListenerInvoked.set(input.isClosed());
            listenerInvoked.countDown();
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

        Future<Void> writeFuture = ch.write(input);
        writeFuture.addListener(future -> {
            inputClosedWhenListenerInvoked.set(input.isClosed());
            listenerInvoked.countDown();
        });
        ch.close(); // close channel to make handler discard the input on subsequent flush
        ch.flush();

        assertTrue(listenerInvoked.await(10, SECONDS));
        assertTrue(writeFuture.isSuccess());
        assertTrue(inputClosedWhenListenerInvoked.get());
        assertFalse(ch.finish());
    }

    @Test
    public void testEndOfInputWhenChannelIsClosedwhenWrite() throws Exception {
        ChunkedInput<Buffer> input = new ChunkedInput<Buffer>() {

            @Override
            public boolean isEndOfInput() {
                return true;
            }

            @Override
            public void close() {
            }

            @Override
            public Buffer readChunk(BufferAllocator allocator) {
                return null;
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

        EmbeddedChannel ch = new EmbeddedChannel(new ChannelHandler() {
            @Override
            public Future<Void> write(ChannelHandlerContext ctx, Object msg) {
                Resource.dispose(msg);
                // Calling close so we will drop all queued messages in the ChunkedWriteHandler.
                ctx.close();
                return ctx.newSucceededFuture();
            }
        }, new ChunkedWriteHandler());

        ch.writeAndFlush(input).sync();
        assertFalse(ch.finishAndReleaseAll());
    }

    @Test
    public void testWriteListenerInvokedAfterChannelClosedAndInputNotFullyConsumed() throws Exception {
        // use non-empty input which has endOfInput = false
        final TestChunkedInput input = new TestChunkedInput(42);
        EmbeddedChannel ch = new EmbeddedChannel(new ChunkedWriteHandler());

        final AtomicBoolean inputClosedWhenListenerInvoked = new AtomicBoolean();
        final CountDownLatch listenerInvoked = new CountDownLatch(1);

        Future<Void> writeFuture = ch.write(input);
        writeFuture.addListener(future -> {
            inputClosedWhenListenerInvoked.set(input.isClosed());
            listenerInvoked.countDown();
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
            Buffer buffer = ch.readOutbound();
            if (buffer == null) {
                break;
            }
            while (buffer.readableBytes() > 0) {
                assertEquals(BYTES[i++], buffer.readByte());
                read++;
                if (i == BYTES.length) {
                    i = 0;
                }
            }
            buffer.close();
        }

        assertEquals(BYTES.length * inputs.length, read);
    }

    private static void checkFirstFailed(Object input) {
        ChannelHandler noOpWrites = new ChannelHandler() {
            @Override
            public Future<Void> write(ChannelHandlerContext ctx, Object msg) {
                Resource.dispose(msg);
                return ctx.newFailedFuture(new RuntimeException());
            }
        };

        EmbeddedChannel ch = new EmbeddedChannel(noOpWrites, new ChunkedWriteHandler());
        Future<Void> r = ch.writeAndFlush(input);

        // Should be `false` as we do not expect any messages to be written
        assertFalse(ch.finish());
        assertTrue(r.cause() instanceof RuntimeException);
    }

    private static void checkSkipFailed(Object input1, Object input2) throws Exception {
        ChannelHandler failFirst = new ChannelHandler() {
            private boolean alreadyFailed;

            @Override
            public Future<Void> write(ChannelHandlerContext ctx, Object msg) {
                if (alreadyFailed) {
                    return ctx.write(msg);
                }
                alreadyFailed = true;
                Resource.dispose(msg);
                return ctx.newFailedFuture(new RuntimeException());
            }
        };

        EmbeddedChannel ch = new EmbeddedChannel(failFirst, new ChunkedWriteHandler());
        Future<Void> r1 = ch.write(input1);
        Future<Void> r2 = ch.writeAndFlush(input2).asStage().await().future();
        assertTrue(ch.finish());

        assertTrue(r1.cause() instanceof RuntimeException);
        assertTrue(r2.isSuccess());

        // note, that after we've "skipped" the first write,
        // we expect to see the second message, chunk by chunk
        int i = 0;
        int read = 0;
        for (;;) {
            Buffer buffer = ch.readOutbound();
            if (buffer == null) {
                break;
            }
            while (buffer.readableBytes() > 0) {
                assertEquals(BYTES[i++], buffer.readByte());
                read++;
                if (i == BYTES.length) {
                    i = 0;
                }
            }
            buffer.close();
        }

        assertEquals(BYTES.length, read);
    }

    private static final class TestChunkedInput implements ChunkedInput<Buffer> {
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
        public Buffer readChunk(BufferAllocator allocator) {
            Buffer buf = allocator.allocate(256);
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

    private static final class ThrowingChunkedInput implements ChunkedInput<Buffer> {
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
        public Buffer readChunk(BufferAllocator allocator) throws Exception {
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
