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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class IdleStateHandlerTest {

    @Test
    public void testReaderIdle() throws Exception {
        IdleStateHandler idleStateHandler = new IdleStateHandler(1L, 0L, 0L, TimeUnit.SECONDS);

        // We start with one FIRST_READER_IDLE_STATE_EVENT, followed by an infinite number of READER_IDLE_STATE_EVENTs
        anyIdle(idleStateHandler, IdleStateEvent.FIRST_READER_IDLE_STATE_EVENT,
                IdleStateEvent.READER_IDLE_STATE_EVENT, IdleStateEvent.READER_IDLE_STATE_EVENT);
    }

    @Test
    public void testWriterIdle() throws Exception {
        IdleStateHandler idleStateHandler = new IdleStateHandler(0L, 1L, 0L, TimeUnit.SECONDS);

        anyIdle(idleStateHandler, IdleStateEvent.FIRST_WRITER_IDLE_STATE_EVENT,
                IdleStateEvent.WRITER_IDLE_STATE_EVENT, IdleStateEvent.WRITER_IDLE_STATE_EVENT);
    }

    @Test
    public void testAllIdle() throws Exception {
        IdleStateHandler idleStateHandler = new IdleStateHandler(0L, 0L, 1L, TimeUnit.SECONDS);

        anyIdle(idleStateHandler, IdleStateEvent.FIRST_ALL_IDLE_STATE_EVENT,
                IdleStateEvent.ALL_IDLE_STATE_EVENT, IdleStateEvent.ALL_IDLE_STATE_EVENT);
    }

    private static void anyIdle(IdleStateHandler idleStateHandler, Object... expected) throws Exception {
        assertThat(expected.length).isGreaterThanOrEqualTo(1);

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
        final IdleStateHandler idleStateHandler = new IdleStateHandler(1L, 0L, 0L, TimeUnit.SECONDS);

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
        final IdleStateHandler idleStateHandler = new IdleStateHandler(0L, 1L, 0L, TimeUnit.SECONDS);

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
        IdleStateHandler idleStateHandler = new IdleStateHandler(1L, 0L, 0L, TimeUnit.SECONDS);

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
        IdleStateHandler idleStateHandler = new IdleStateHandler(0L, 1L, 0L, TimeUnit.SECONDS);

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
        IdleStateHandler idleStateHandler = new IdleStateHandler(0L, 0L, 1L, TimeUnit.SECONDS);

        Action reader = new Action() {
            @Override
            public void run(EmbeddedChannel channel) throws Exception {
                channel.writeInbound("Hello, World!");
            }
        };

        anyNotIdle(idleStateHandler, reader, IdleStateEvent.FIRST_ALL_IDLE_STATE_EVENT);

        // Writer...
        idleStateHandler = new IdleStateHandler(0L, 0L, 1L, TimeUnit.SECONDS);

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

    private interface Action {
        void run(EmbeddedChannel channel) throws Exception;
    }
}
