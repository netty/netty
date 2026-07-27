/*
 * Copyright 2026 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package io.netty.handler.timeout;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Tests that {@link IdleStateHandler#resetWriteTimeout()} and
 * {@link IdleStateHandler#resetReadTimeout()} correctly restore the
 * "first idle" semantics after a non-first event has already fired.
 *
 * The reset methods update the timestamp but don't touch the firstWriter/ReaderIdleEvent
 * flags, so the handler keeps reporting non-first events even after a reset.
 * The writeListener path doesn't have this problem because it resets both.
 */
public class IdleStateHandlerResetFlagTest {

    /**
     * If a WRITER_IDLE event has already fired as non-first and then
     * resetWriteTimeout() is called, the next event should be first again —
     * same as if an actual write had happened.
     */
    @Test
    public void testResetWriteTimeoutResetsFirstEventFlag() throws Exception {
        final IdleStateHandler idleStateHandler = new IdleStateHandler(
                false, 0L, 1L, 0L, TimeUnit.SECONDS);

        final List<IdleStateEvent> events = new ArrayList<IdleStateEvent>();
        EmbeddedChannel channel = new EmbeddedChannel(idleStateHandler,
                new ChannelInboundHandlerAdapter() {
                    @Override
                    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                        if (evt instanceof IdleStateEvent) {
                            events.add((IdleStateEvent) evt);
                        }
                    }
                });
        channel.freezeTime();

        try {
            // first idle — expected
            channel.advanceTimeBy(1100L, TimeUnit.MILLISECONDS);
            channel.runPendingTasks();
            assertEquals(1, events.size());
            assertSame(IdleStateEvent.FIRST_WRITER_IDLE_STATE_EVENT, events.get(0),
                    "First idle after connect should be FIRST_WRITER_IDLE_STATE_EVENT");

            // second idle, no activity in between — non-first
            channel.advanceTimeBy(1100L, TimeUnit.MILLISECONDS);
            channel.runPendingTasks();
            assertEquals(2, events.size());
            assertSame(IdleStateEvent.WRITER_IDLE_STATE_EVENT, events.get(1),
                    "Second idle without reset should be WRITER_IDLE_STATE_EVENT (first=false)");

            // reset: tells the handler to treat this moment as a fresh start
            idleStateHandler.resetWriteTimeout();

            // should fire as first again, but currently doesn't because
            // resetWriteTimeout() only updates lastWriteTime, not firstWriterIdleEvent
            channel.advanceTimeBy(1100L, TimeUnit.MILLISECONDS);
            channel.runPendingTasks();
            assertEquals(3, events.size());
            assertSame(IdleStateEvent.FIRST_WRITER_IDLE_STATE_EVENT, events.get(2),
                    "After resetWriteTimeout(), next idle MUST be FIRST_WRITER_IDLE_STATE_EVENT. " +
                    "Bug: firstWriterIdleEvent is not reset by resetWriteTimeout().");
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    /**
     * Same issue on the read side: resetReadTimeout() resets the clock but
     * leaves firstReaderIdleEvent as-is, so the next event stays non-first.
     */
    @Test
    public void testResetReadTimeoutResetsFirstEventFlag() throws Exception {
        final IdleStateHandler idleStateHandler = new IdleStateHandler(
                false, 1L, 0L, 0L, TimeUnit.SECONDS);

        final List<IdleStateEvent> events = new ArrayList<IdleStateEvent>();
        EmbeddedChannel channel = new EmbeddedChannel(idleStateHandler,
                new ChannelInboundHandlerAdapter() {
                    @Override
                    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                        if (evt instanceof IdleStateEvent) {
                            events.add((IdleStateEvent) evt);
                        }
                    }
                });
        channel.freezeTime();

        try {
            // first idle
            channel.advanceTimeBy(1100L, TimeUnit.MILLISECONDS);
            channel.runPendingTasks();
            assertEquals(1, events.size());
            assertSame(IdleStateEvent.FIRST_READER_IDLE_STATE_EVENT, events.get(0),
                    "First idle after connect should be FIRST_READER_IDLE_STATE_EVENT");

            // non-first idle
            channel.advanceTimeBy(1100L, TimeUnit.MILLISECONDS);
            channel.runPendingTasks();
            assertEquals(2, events.size());
            assertSame(IdleStateEvent.READER_IDLE_STATE_EVENT, events.get(1),
                    "Second idle without reset should be READER_IDLE_STATE_EVENT (first=false)");

            idleStateHandler.resetReadTimeout();

            // should be first again after the reset
            channel.advanceTimeBy(1100L, TimeUnit.MILLISECONDS);
            channel.runPendingTasks();
            assertEquals(3, events.size());
            assertSame(IdleStateEvent.FIRST_READER_IDLE_STATE_EVENT, events.get(2),
                    "After resetReadTimeout(), next idle MUST be FIRST_READER_IDLE_STATE_EVENT. " +
                    "Bug: firstReaderIdleEvent is not reset by resetReadTimeout().");
        } finally {
            channel.finishAndReleaseAll();
        }
    }
}
