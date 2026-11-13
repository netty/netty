/*
 * Copyright 2026 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec.http2;

import io.netty.channel.ChannelFuture;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Http2ConnectionHandlerGracefulShutdownDrainTest {
    private static final long DRAIN_MILLIS = 1000;

    private EmbeddedChannel channel;

    @AfterEach
    public void tearDown() {
        if (channel != null) {
            channel.finishAndReleaseAll();
        }
    }

    private Http2ConnectionHandler newServer(long gracefulShutdownTimeoutMillis) {
        Http2ConnectionHandler handler = new Http2ConnectionHandlerBuilder()
                .server(true)
                .frameListener(new Http2FrameAdapter())
                .gracefulShutdownTimeoutMillis(gracefulShutdownTimeoutMillis)
                .gracefulShutdownDrainMillis(DRAIN_MILLIS)
                .build();
        channel = new EmbeddedChannel(handler);
        channel.freezeTime();
        return handler;
    }

    // EmbeddedChannel.close() cancels the tasks scheduled while closing, so go through the pipeline instead.
    private ChannelFuture gracefulClose() {
        ChannelFuture closed = channel.pipeline().close();
        channel.runPendingTasks();
        return closed;
    }

        private void advance(long millis) {
        channel.advanceTimeBy(millis, MILLISECONDS);
        channel.runScheduledPendingTasks();
    }

    @Test
    public void connectionStaysOpenUntilDrainElapsed() {
        newServer(30_000);

        ChannelFuture closed = gracefulClose();
        assertTrue(channel.isOpen());

        advance(DRAIN_MILLIS - 1);
        assertTrue(channel.isOpen());

        advance(1);
        assertFalse(channel.isOpen());
        assertTrue(closed.isSuccess());
    }

    @Test
    public void drainEndsWhenRemoteCloses() {
        newServer(30_000);

        ChannelFuture closed = gracefulClose();
        assertTrue(channel.isOpen());

        channel.unsafe().close(channel.unsafe().voidPromise());
        channel.runPendingTasks();
        assertTrue(closed.isSuccess());
    }

    @Test
    public void drainStartsWhenLastStreamCloses() throws Http2Exception {
        Http2ConnectionHandler handler = newServer(30_000);
        Http2Stream stream = handler.connection().remote().createStream(3, false);

        gracefulClose();
        advance(DRAIN_MILLIS);
        assertTrue(channel.isOpen());

        handler.closeStream(stream, channel.newSucceededFuture());
        advance(DRAIN_MILLIS - 1);
        assertTrue(channel.isOpen());

        advance(1);
        assertFalse(channel.isOpen());
    }

    @Test
    public void gracefulShutdownTimeoutClosesWithoutDrain() throws Http2Exception {
        Http2ConnectionHandler handler = newServer(100);
        handler.connection().remote().createStream(3, false);

        gracefulClose();
        advance(99);
        assertTrue(channel.isOpen());

        advance(1);
        assertFalse(channel.isOpen());
    }

    @Test
    public void frameCodecBuilderAppliesDrain() {
        Http2FrameCodec codec = Http2FrameCodecBuilder.forServer().gracefulShutdownDrainMillis(DRAIN_MILLIS).build();
        assertEquals(DRAIN_MILLIS, codec.gracefulShutdownDrainMillis());
    }
}
