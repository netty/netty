/*
 * Copyright 2016 The Netty Project
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
package io.netty.handler.timeout;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class IdleStateHandlerTest {

    @Test
    public void testReaderIdle() throws Exception {
        IdleStateHandler idleStateHandler = new IdleStateHandler(
                false, 1L, 0L, 0L, TimeUnit.SECONDS);

        // We start with one FIRST_READER_IDLE_STATE_EVENT, followed by an infinite number of READER_IDLE_STATE_EVENTs
        anyIdle(idleStateHandler, IdleStateEvent.FIRST_READER_IDLE_STATE_EVENT,
                IdleStateEvent.READER_IDLE_STATE_EVENT, IdleStateEvent.READER_IDLE_STATE_EVENT);
    }

    @Test
    public void testWriterIdle() throws Exception {
        IdleStateHandler idleStateHandler = new IdleStateHandler(
                false, 0L, 1L, 0L, TimeUnit.SECONDS);

        anyIdle(idleStateHandler, IdleStateEvent.FIRST_WRITER_IDLE_STATE_EVENT,
                IdleStateEvent.WRITER_IDLE_STATE_EVENT, IdleStateEvent.WRITER_IDLE_STATE_EVENT);
    }

    @Test
    public void testAllIdle() throws Exception {
        IdleStateHandler idleStateHandler = new IdleStateHandler(
                false, 0L, 0L, 1L, TimeUnit.SECONDS);

        anyIdle(idleStateHandler, IdleStateEvent.FIRST_ALL_IDLE_STATE_EVENT,
                IdleStateEvent.ALL_IDLE_STATE_EVENT, IdleStateEvent.ALL_IDLE_STATE_EVENT);
    }

    private static void anyIdle(IdleStateHandler idleStateHandler, Object... expected) throws Exception {
        assertThat(expected.length,  greaterThanOrEqualTo(1));

        final List<Object> events = new ArrayList<Object>();
        ChannelInboundHandlerAdapter handler = new ChannelInboundHandlerAdapter() {
            @Override
            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                events.add(evt);
            }
        };

        EmbeddedChannel channel = new EmbeddedChannel(idleStateHandler, handler);
        channel.freezeTime();
        try {
            // For each expected event advance the ticker and run() the task. Each
            // step should yield in an IdleStateEvent because we haven't written
            // or read anything from the channel.
            for (int i = 0; i < expected.length; i++) {
                channel.advanceTimeBy(1, TimeUnit.SECONDS);
                channel.runPendingTasks();
            }

            assertEquals(expected.length, events.size());

            // Compare the expected with the actual IdleStateEvents
            for (int i = 0; i < expected.length; i++) {
                Object evt = events.get(i);
                assertSame(expected[i], evt, "Element " + i + " is not matching");
            }
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    public void testResetReader() throws Exception {
        final IdleStateHandler idleStateHandler = new IdleStateHandler(
                false, 1L, 0L, 0L, TimeUnit.SECONDS);

        Action action = new Action() {
            @Override
            public void run(EmbeddedChannel channel) throws Exception {
                idleStateHandler.resetReadTimeout();
            }
        };

        anyNotIdle(idleStateHandler, action, IdleStateEvent.FIRST_READER_IDLE_STATE_EVENT);
    }

    @Test
    public void testResetWriter() throws Exception {
        final IdleStateHandler idleStateHandler = new IdleStateHandler(
                false, 0L, 1L, 0L, TimeUnit.SECONDS);

        Action action = new Action() {
            @Override
            public void run(EmbeddedChannel channel) throws Exception {
                idleStateHandler.resetWriteTimeout();
            }
        };

        anyNotIdle(idleStateHandler, action, IdleStateEvent.FIRST_WRITER_IDLE_STATE_EVENT);
    }

    @Test
    public void testReaderNotIdle() throws Exception {
        IdleStateHandler idleStateHandler = new IdleStateHandler(
                false, 1L, 0L, 0L, TimeUnit.SECONDS);

        Action action = new Action() {
            @Override
            public void run(EmbeddedChannel channel) throws Exception {
                channel.writeInbound("Hello, World!");
            }
        };

        anyNotIdle(idleStateHandler, action, IdleStateEvent.FIRST_READER_IDLE_STATE_EVENT);
    }

    @Test
    public void testWriterNotIdle() throws Exception {
        IdleStateHandler idleStateHandler = new IdleStateHandler(
                false, 0L, 1L, 0L, TimeUnit.SECONDS);

        Action action = new Action() {
            @Override
            public void run(EmbeddedChannel channel) throws Exception {
                channel.writeAndFlush("Hello, World!");
            }
        };

        anyNotIdle(idleStateHandler, action, IdleStateEvent.FIRST_WRITER_IDLE_STATE_EVENT);
    }

    @Test
    public void testAllNotIdle() throws Exception {
        // Reader...
        IdleStateHandler idleStateHandler = new IdleStateHandler(
                false, 0L, 0L, 1L, TimeUnit.SECONDS);

        Action reader = new Action() {
            @Override
            public void run(EmbeddedChannel channel) throws Exception {
                channel.writeInbound("Hello, World!");
            }
        };

        anyNotIdle(idleStateHandler, reader, IdleStateEvent.FIRST_ALL_IDLE_STATE_EVENT);

        // Writer...
        idleStateHandler = new IdleStateHandler(
                false, 0L, 0L, 1L, TimeUnit.SECONDS);

        Action writer = new Action() {
            @Override
            public void run(EmbeddedChannel channel) throws Exception {
                channel.writeAndFlush("Hello, World!");
            }
        };

        anyNotIdle(idleStateHandler, writer, IdleStateEvent.FIRST_ALL_IDLE_STATE_EVENT);
    }

    private static void anyNotIdle(IdleStateHandler idleStateHandler,
                                   Action action, Object expected) throws Exception {

        final List<Object> events = new ArrayList<Object>();
        ChannelInboundHandlerAdapter handler = new ChannelInboundHandlerAdapter() {
            @Override
            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                events.add(evt);
            }
        };

        EmbeddedChannel channel = new EmbeddedChannel(idleStateHandler, handler);
        channel.freezeTime();
        try {
            long delayInNanos = TimeUnit.SECONDS.toNanos(1);

            channel.advanceTimeBy(delayInNanos / 2L + 1L, TimeUnit.NANOSECONDS);
            action.run(channel);

            // Advance the ticker by some fraction.
            // There shouldn't be an IdleStateEvent getting fired because
            // we've just performed an action on the channel that is meant
            // to reset the idle task.
            channel.advanceTimeBy(delayInNanos / 2L, TimeUnit.NANOSECONDS);
            channel.runPendingTasks();
            assertEquals(0, events.size());

            // Advance the ticker by the full amount and it should yield
            // in an IdleStateEvent.
            channel.advanceTimeBy(delayInNanos, TimeUnit.NANOSECONDS);
            channel.runPendingTasks();
            assertEquals(1, events.size());
            assertSame(expected, events.get(0));
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    public void testObserveWriterIdle() throws Exception {
        observeOutputIdle(true);
    }

    @Test
    public void testObserveAllIdle() throws Exception {
        observeOutputIdle(false);
    }

    private static void observeOutputIdle(boolean writer) throws Exception {

        long writerIdleTime = 0L;
        long allIdleTime = 0L;
        IdleStateEvent expected;

        if (writer) {
            writerIdleTime = 5L;
            expected = IdleStateEvent.FIRST_WRITER_IDLE_STATE_EVENT;
        } else {
            allIdleTime = 5L;
            expected = IdleStateEvent.FIRST_ALL_IDLE_STATE_EVENT;
        }

        IdleStateHandler idleStateHandler = new IdleStateHandler(
                true, 0L, writerIdleTime, allIdleTime, TimeUnit.SECONDS);

        final List<Object> events = new ArrayList<Object>();
        ChannelInboundHandlerAdapter handler = new ChannelInboundHandlerAdapter() {
            @Override
            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                events.add(evt);
            }
        };

        ObservableChannel channel = new ObservableChannel(idleStateHandler, handler);
        channel.freezeTime();
        try {
            // We're writing 3 messages that will be consumed at different rates!
            channel.writeAndFlush(Unpooled.wrappedBuffer(new byte[] { 1 }));
            channel.writeAndFlush(Unpooled.wrappedBuffer(new byte[] { 2 }));
            channel.writeAndFlush(Unpooled.wrappedBuffer(new byte[] { 3 }));
            channel.writeAndFlush(Unpooled.wrappedBuffer(new byte[5 * 1024]));

            // Establish a baseline. We're not consuming anything and let it idle once.
            channel.advanceTimeBy(5, TimeUnit.SECONDS);
            channel.runPendingTasks();
            assertEquals(1, events.size());
            assertSame(expected, events.get(0));
            events.clear();

            // Consume one message in 4 seconds, then be idle for 2 seconds,
            // then run the task and we shouldn't get an IdleStateEvent because
            // we haven't been idle for long enough!
            channel.advanceTimeBy(4, TimeUnit.SECONDS);
            channel.runPendingTasks();
            assertNotNullAndRelease(channel.consume());

            channel.advanceTimeBy(2, TimeUnit.SECONDS);
            channel.runPendingTasks();
            assertEquals(0, events.size());

            // Consume one message in 3 seconds, then be idle for 4 seconds,
            // then run the task and we shouldn't get an IdleStateEvent because
            // we haven't been idle for long enough!
            channel.advanceTimeBy(2, TimeUnit.SECONDS);
            channel.runPendingTasks();
            assertNotNullAndRelease(channel.consume());

            channel.advanceTimeBy(4, TimeUnit.SECONDS);
            channel.runPendingTasks();
            assertEquals(0, events.size());

            // Don't consume a message and be idle for 5 seconds.
            // We should get an IdleStateEvent!
            channel.advanceTimeBy(5, TimeUnit.SECONDS);
            channel.runPendingTasks();
            assertEquals(1, events.size());
            events.clear();

            // Consume one message in 2 seconds, then be idle for 1 seconds,
            // then run the task and we shouldn't get an IdleStateEvent because
            // we haven't been idle for long enough!
            channel.advanceTimeBy(2, TimeUnit.SECONDS);
            channel.runPendingTasks();
            assertNotNullAndRelease(channel.consume());

            channel.advanceTimeBy(1, TimeUnit.SECONDS);
            channel.runPendingTasks();
            assertEquals(0, events.size());

            // Consume part of the message every 2 seconds, then be idle for 1 seconds,
            // then run the task and we should get an IdleStateEvent because the first trigger
            channel.advanceTimeBy(2, TimeUnit.SECONDS);
            channel.runPendingTasks();
            assertNotNullAndRelease(channel.consumePart(1024));
            channel.advanceTimeBy(2, TimeUnit.SECONDS);
            channel.runPendingTasks();
            assertNotNullAndRelease(channel.consumePart(1024));
            channel.advanceTimeBy(1, TimeUnit.SECONDS);
            channel.runPendingTasks();
            assertEquals(1, events.size());
            events.clear();

            // Consume part of the message every 2 seconds, then be idle for 1 seconds,
            // then consume all the rest of the message, then run the task and we shouldn't
            // get an IdleStateEvent because the data is flowing and we haven't been idle for long enough!
            channel.advanceTimeBy(2, TimeUnit.SECONDS);
            channel.runPendingTasks();
            assertNotNullAndRelease(channel.consumePart(1024));
            channel.advanceTimeBy(2, TimeUnit.SECONDS);
            channel.runPendingTasks();
            assertNotNullAndRelease(channel.consumePart(1024));
            channel.advanceTimeBy(1, TimeUnit.SECONDS);
            channel.runPendingTasks();
            assertEquals(0, events.size());
            channel.advanceTimeBy(2, TimeUnit.SECONDS);
            channel.runPendingTasks();
            assertNotNullAndRelease(channel.consumePart(1024));

            // There are no messages left! Advance the ticker by 3 seconds,
            // attempt a consume() but it will be null, then advance the
            // ticker by an another 2 seconds and we should get an IdleStateEvent
            // because we've been idle for 5 seconds.
            channel.advanceTimeBy(3, TimeUnit.SECONDS);
            channel.runPendingTasks();
            assertNull(channel.consume());

            channel.advanceTimeBy(2, TimeUnit.SECONDS);
            channel.runPendingTasks();
            assertEquals(1, events.size());

            // q.e.d.
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    private static void assertNotNullAndRelease(Object msg) {
        assertNotNull(msg);
        ReferenceCountUtil.release(msg);
    }

    private interface Action {
        void run(EmbeddedChannel channel) throws Exception;
    }

    private static class ObservableChannel extends EmbeddedChannel {

        ObservableChannel(ChannelHandler... handlers) {
            super(handlers);
        }

        @Override
        protected void doWrite(ChannelOutboundBuffer in) throws Exception {
            // Overridden to change EmbeddedChannel's default behavior. We went to keep
            // the messages in the ChannelOutboundBuffer.
        }

        private Object consume() {
            ChannelOutboundBuffer buf = unsafe().outboundBuffer();
            if (buf != null) {
                Object msg = buf.current();
                if (msg != null) {
                    ReferenceCountUtil.retain(msg);
                    buf.remove();
                    return msg;
                }
            }
            return null;
        }

        /**
         * Consume the part of a message.
         *
         * @param byteCount count of byte to be consumed
         * @return the message currently being consumed
         */
        private Object consumePart(int byteCount) {
            ChannelOutboundBuffer buf = unsafe().outboundBuffer();
            if (buf != null) {
                Object msg = buf.current();
                if (msg != null) {
                    ReferenceCountUtil.retain(msg);
                    buf.removeBytes(byteCount);
                    return msg;
                }
            }
            return null;
        }
    }
}
