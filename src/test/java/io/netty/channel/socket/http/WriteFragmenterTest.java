/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.netty.channel.socket.http;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import io.netty.buffer.ChannelBuffer;
import io.netty.buffer.ChannelBuffers;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.Channels;
import io.netty.channel.MessageEvent;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests write fragmenting capabilities
 */
public class WriteFragmenterTest {

    private FakeSocketChannel channel;

    private WriteFragmenter fragmenter;

    private FakeChannelSink downstreamCatcher;

    @Before
    public void setUp() throws Exception {
        fragmenter = new WriteFragmenter(100);

        ChannelPipeline pipeline = Channels.pipeline();
        pipeline.addLast(WriteFragmenter.NAME, fragmenter);
        downstreamCatcher = new FakeChannelSink();
        channel =
                new FakeSocketChannel(null, null, pipeline, downstreamCatcher);
    }

    @Test
    public void testLeavesWritesBeneathThresholdUntouched() {
        ChannelBuffer data = ChannelBuffers.wrappedBuffer(new byte[99]);
        Channels.write(channel, data);

        assertEquals(1, downstreamCatcher.events.size());
        ChannelBuffer sentData =
                NettyTestUtils.checkIsDownstreamMessageEvent(
                        downstreamCatcher.events.poll(), ChannelBuffer.class);
        assertSame(data, sentData);
    }

    @Test
    public void testLeavesMessagesOnThresholdUntouched() {
        ChannelBuffer data = ChannelBuffers.wrappedBuffer(new byte[100]);
        Channels.write(channel, data);

        assertEquals(1, downstreamCatcher.events.size());
        ChannelBuffer sentData =
                NettyTestUtils.checkIsDownstreamMessageEvent(
                        downstreamCatcher.events.poll(), ChannelBuffer.class);
        assertSame(data, sentData);
    }

    @Test
    public void testSplitsMessagesAboveThreshold_twoChunks() {
        ChannelBuffer data = ChannelBuffers.wrappedBuffer(new byte[101]);
        Channels.write(channel, data);

        assertEquals(2, downstreamCatcher.events.size());
        ChannelBuffer chunk1 =
                NettyTestUtils.checkIsDownstreamMessageEvent(
                        downstreamCatcher.events.poll(), ChannelBuffer.class);
        ChannelBuffer chunk2 =
                NettyTestUtils.checkIsDownstreamMessageEvent(
                        downstreamCatcher.events.poll(), ChannelBuffer.class);
        assertEquals(100, chunk1.readableBytes());
        assertEquals(1, chunk2.readableBytes());
    }

    @Test
    public void testSplitsMessagesAboveThreshold_multipleChunks() {
        ChannelBuffer data = ChannelBuffers.wrappedBuffer(new byte[2540]);
        Channels.write(channel, data);

        assertEquals(26, downstreamCatcher.events.size());
        for (int i = 0; i < 25; i ++) {
            ChannelBuffer chunk =
                    NettyTestUtils.checkIsDownstreamMessageEvent(
                            downstreamCatcher.events.poll(),
                            ChannelBuffer.class);
            assertEquals(100, chunk.readableBytes());
        }

        ChannelBuffer endChunk =
                NettyTestUtils.checkIsDownstreamMessageEvent(
                        downstreamCatcher.events.poll(), ChannelBuffer.class);
        assertEquals(40, endChunk.readableBytes());
    }

    @Test
    public void testChannelFutureTriggeredOnlyWhenAllChunksWritten() {
        ChannelBuffer data = ChannelBuffers.wrappedBuffer(new byte[2540]);
        ChannelFuture mainWriteFuture = Channels.write(channel, data);

        assertEquals(26, downstreamCatcher.events.size());
        for (int i = 0; i < 25; i ++) {
            ((MessageEvent) downstreamCatcher.events.poll()).getFuture()
                    .setSuccess();
            assertFalse(mainWriteFuture.isDone());
        }

        ((MessageEvent) downstreamCatcher.events.poll()).getFuture()
                .setSuccess();
        assertTrue(mainWriteFuture.isDone());
        assertTrue(mainWriteFuture.isSuccess());
    }

    @Test
    public void testChannelFutureFailsOnFirstWriteFailure() {
        ChannelBuffer data = ChannelBuffers.wrappedBuffer(new byte[2540]);
        ChannelFuture mainWriteFuture = Channels.write(channel, data);

        assertEquals(26, downstreamCatcher.events.size());
        for (int i = 0; i < 10; i ++) {
            ((MessageEvent) downstreamCatcher.events.poll()).getFuture()
                    .setSuccess();
            assertFalse(mainWriteFuture.isDone());
        }

        ((MessageEvent) downstreamCatcher.events.poll()).getFuture()
                .setFailure(new Exception("Something bad happened"));
        assertTrue(mainWriteFuture.isDone());
        assertFalse(mainWriteFuture.isSuccess());

        // check all the subsequent writes got cancelled
        for (int i = 0; i < 15; i ++) {
            assertTrue(((MessageEvent) downstreamCatcher.events.poll())
                    .getFuture().isCancelled());
        }
    }
}
