/*
 * Copyright 2021 The Netty Project
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
package io.netty5.handler.timeout;

import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.embedded.EmbeddedChannel;
import io.netty5.util.concurrent.Future;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class AbstractIdleStateHandlerTest {

    @Test
    public void testReaderIdle() throws Exception {
        TestableIdleStateHandler idleStateHandler = new TestableIdleStateHandler(1L, 0L, 0L, TimeUnit.SECONDS);

        // We start with one FIRST_READER_IDLE_STATE_EVENT, followed by an infinite number of READER_IDLE_STATE_EVENTs
        anyIdle(idleStateHandler, IdleStateEvent.FIRST_READER_IDLE_STATE_EVENT,
                IdleStateEvent.READER_IDLE_STATE_EVENT, IdleStateEvent.READER_IDLE_STATE_EVENT);
    }

    @Test
    public void testWriterIdle() throws Exception {
        TestableIdleStateHandler idleStateHandler = new TestableIdleStateHandler(0L, 1L, 0L, TimeUnit.SECONDS);

        anyIdle(idleStateHandler, IdleStateEvent.FIRST_WRITER_IDLE_STATE_EVENT,
                IdleStateEvent.WRITER_IDLE_STATE_EVENT, IdleStateEvent.WRITER_IDLE_STATE_EVENT);
    }

    @Test
    public void testAllIdle() throws Exception {
        TestableIdleStateHandler idleStateHandler = new TestableIdleStateHandler(0L, 0L, 1L, TimeUnit.SECONDS);

        anyIdle(idleStateHandler, IdleStateEvent.FIRST_ALL_IDLE_STATE_EVENT,
                IdleStateEvent.ALL_IDLE_STATE_EVENT, IdleStateEvent.ALL_IDLE_STATE_EVENT);
    }

    private static void anyIdle(TestableIdleStateHandler idleStateHandler, Object... expected) throws Exception {
        assertThat(expected.length,  greaterThanOrEqualTo(1));

        final List<Object> events = new ArrayList<>();
        ChannelHandler handler = new ChannelHandler() {
            @Override
            public void channelInboundEvent(ChannelHandlerContext ctx, Object evt) throws Exception {
                events.add(evt);
            }
        };

        EmbeddedChannel channel = new EmbeddedChannel(idleStateHandler, handler);
        try {
            // For each expected event advance the ticker and run() the task. Each
            // step should yield in an IdleStateEvent because we haven't written
            // or read anything from the channel.
            for (int i = 0; i < expected.length; i++) {
                idleStateHandler.tickRun();
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
    public void testReaderNotIdle() throws Exception {
        TestableIdleStateHandler idleStateHandler = new TestableIdleStateHandler(1L, 0L, 0L, TimeUnit.SECONDS);

        Action action = channel -> channel.writeInbound("Hello, World!");

        anyNotIdle(idleStateHandler, action, IdleStateEvent.FIRST_READER_IDLE_STATE_EVENT);
    }

    @Test
    public void testWriterNotIdle() throws Exception {
        TestableIdleStateHandler idleStateHandler = new TestableIdleStateHandler(0L, 1L, 0L, TimeUnit.SECONDS);

        Action action = channel -> channel.writeAndFlush("Hello, World!");

        anyNotIdle(idleStateHandler, action, IdleStateEvent.FIRST_WRITER_IDLE_STATE_EVENT);
    }

    @Test
    public void testAllNotIdle() throws Exception {
        // Reader...
        TestableIdleStateHandler idleStateHandler = new TestableIdleStateHandler(0L, 0L, 1L, TimeUnit.SECONDS);

        Action reader = channel -> channel.writeInbound("Hello, World!");

        anyNotIdle(idleStateHandler, reader, IdleStateEvent.FIRST_ALL_IDLE_STATE_EVENT);

        // Writer...
        idleStateHandler = new TestableIdleStateHandler(0L, 0L, 1L, TimeUnit.SECONDS);

        Action writer = channel -> channel.writeAndFlush("Hello, World!");

        anyNotIdle(idleStateHandler, writer, IdleStateEvent.FIRST_ALL_IDLE_STATE_EVENT);
    }

    private static void anyNotIdle(TestableIdleStateHandler idleStateHandler,
                                   Action action, Object expected) throws Exception {

        final List<Object> events = new ArrayList<>();
        ChannelHandler handler = new ChannelHandler() {
            @Override
            public void channelInboundEvent(ChannelHandlerContext ctx, Object evt) throws Exception {
                events.add(evt);
            }
        };

        EmbeddedChannel channel = new EmbeddedChannel(idleStateHandler, handler);
        try {
            idleStateHandler.tick(1L, TimeUnit.NANOSECONDS);
            action.run(channel);

            // Advance the ticker by some fraction and run() the task.
            // There shouldn't be an IdleStateEvent getting fired because
            // we've just performed an action on the channel that is meant
            // to reset the idle task.
            long delayInNanos = idleStateHandler.delay(TimeUnit.NANOSECONDS);
            assertNotEquals(0L, delayInNanos);

            idleStateHandler.tickRun(delayInNanos / 2L, TimeUnit.NANOSECONDS);
            assertEquals(0, events.size());

            // Advance the ticker by the full amount and it should yield
            // in an IdleStateEvent.
            idleStateHandler.tickRun();
            assertEquals(1, events.size());
            assertSame(expected, events.get(0));
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    private interface Action {
        void run(EmbeddedChannel channel) throws Exception;
    }

    private static class TestableIdleStateHandler extends IdleStateHandler {

        private Runnable task;

        private long delayInNanos;

        private long ticksInNanos;

        TestableIdleStateHandler(long readerIdleTime, long writerIdleTime, long allIdleTime,
                TimeUnit unit) {
            super(readerIdleTime, writerIdleTime, allIdleTime, unit);
        }

        public long delay(TimeUnit unit) {
            return unit.convert(delayInNanos, TimeUnit.NANOSECONDS);
        }

        public void run() {
            task.run();
        }

        public void tickRun() {
            tickRun(delayInNanos, TimeUnit.NANOSECONDS);
        }

        public void tickRun(long delay, TimeUnit unit) {
            tick(delay, unit);
            run();
        }

        /**
         * Advances the current ticker by the given amount.
         */
        public void tick(long delay, TimeUnit unit) {
            ticksInNanos += unit.toNanos(delay);
        }

        @Override
        long ticksInNanos() {
            return ticksInNanos;
        }

        @Override
        Future<?> schedule(ChannelHandlerContext ctx, Runnable task, long delay, TimeUnit unit) {
            this.task = task;
            this.delayInNanos = unit.toNanos(delay);
            return null;
        }
    }
}
