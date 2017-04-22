/*
 * Copyright 2013 The Netty Project
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

package io.netty.buffer.stream;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Promise;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class StreamTest {

    private static EventExecutor executorA;
    private static EventExecutor executorB;

    @BeforeClass
    public static void setUp() {
        executorA = new DefaultEventExecutorGroup(1).next();
        executorB = new DefaultEventExecutorGroup(1).next();
    }

    @AfterClass
    public static void tearDown() {
        executorB.shutdown();
        executorA.shutdown();
    }

    @Test(timeout = 5000)
    public void testSimpleWithSameExecutor() throws Exception {
        final long size = 1048576L * 1024L * 1024L; // Transfer whooping 1 TiB of garbage
        testSimple0(executorA, executorA, size);
    }

    @Test(timeout = 10000)
    public void testSimpleWithDifferentExecutors() throws Exception {
        final long size = 1048576L * 1024L * 16L; // Transfer 16 GiB of garbage
        testSimple0(executorA, executorB, size);
    }

    private static void testSimple0(EventExecutor executorA, EventExecutor executorB, long size) throws Exception {
        LargeByteStreamConsumer consumer = new LargeByteStreamConsumer(size);
        Stream<ByteBuf> stream = new LargeByteStream(executorA, size);
        stream.accept(executorB, consumer);

        for (;;) {
            if (consumer.future.await(1000)) {
                break;
            }

            System.err.println(consumer.counter + " / " + size);
        }

        consumer.future.sync();
    }

    private static class LargeByteStream extends AbstractStream<ByteBuf> {
        LargeByteStream(EventExecutor executor, long size) {
            super(executor, new LargeByteStreamProducer(size));
        }
    }

    private static class LargeByteStreamProducer implements StreamProducer<ByteBuf> {

        private final long size;
        private long counter;

        LargeByteStreamProducer(long size) {
            this.size = size;
        }

        @Override
        public void streamAccepted(StreamProducerContext<ByteBuf> ctx) throws Exception {
            generate(ctx);
        }

        @Override
        public void streamConsumed(StreamProducerContext<ByteBuf> ctx) throws Exception {
            generate(ctx);
        }

        private void generate(StreamProducerContext<ByteBuf> ctx) {
            ByteBuf buf = ctx.buffer();
            int chunkSize = (int) Math.min(size - counter, buf.maxWritableBytes());
            buf.ensureWritable(chunkSize);
            buf.writerIndex(buf.writerIndex() + chunkSize);
            ctx.update();
            counter += chunkSize;
            if (counter == size) {
                ctx.close();
                return;
            }

            if (counter > size) {
                throw new AssertionError("counter > size");
            }
        }

        @Override
        public void streamDiscarded(StreamProducerContext<ByteBuf> ctx) throws Exception {
            throw new AssertionError("stream discarded");
        }

        @Override
        public void streamRejected(StreamProducerContext<ByteBuf> ctx, Throwable cause) throws Exception {
            throw new AssertionError("stream rejected", cause);
        }
    }

    private static class LargeByteStreamConsumer implements StreamConsumer<ByteBuf> {

        private final long size;
        volatile Promise future;
        volatile long counter;

        LargeByteStreamConsumer(long size) {
            this.size = size;
        }

        @Override
        public ByteBuf newStreamBuffer(StreamConsumerContext<ByteBuf> ctx) throws Exception {
            future = new DefaultPromise(ctx.executor());
            return Unpooled.buffer(0, 65536); // Only use 64 KiB at max.
        }

        @Override
        public void streamOpen(StreamConsumerContext<ByteBuf> ctx) throws Exception { }

        @Override
        public void streamUpdated(StreamConsumerContext<ByteBuf> ctx) throws Exception {
            ByteBuf buf = ctx.buffer();
            int chunkSize = buf.readableBytes();
            buf.skipBytes(chunkSize);
            buf.discardReadBytes();
            counter += chunkSize;
            if (counter == size) {
                future.setSuccess();
            } else if (counter > size) {
                AssertionError error = new AssertionError("counter > size");
                ctx.reject(error);
                future.setFailure(error);
            } else {
                ctx.next();
            }
        }

        @Override
        public void streamAborted(StreamConsumerContext<ByteBuf> ctx, Throwable cause) throws Exception {
            future.setFailure(cause);
        }

        @Override
        public void streamClosed(StreamConsumerContext<ByteBuf> ctx) throws Exception {
            future.tryFailure(new AssertionError("tryFailure() must return false."));
        }
    }
}
