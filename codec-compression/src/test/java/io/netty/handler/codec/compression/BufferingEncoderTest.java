/*
 * Copyright 2026 The Netty Project
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
package io.netty.handler.codec.compression;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.embedded.EmbeddedChannel;
import net.jpountz.lz4.LZ4Factory;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.Random;

import static io.netty.handler.codec.compression.Lz4Constants.DEFAULT_SEED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that the encoders which buffer data internally only complete the promise of a write once all the data of the
 * message was passed to the transport.
 */
public class BufferingEncoderTest {

    /**
     * Plays the role of the transport: empty writes complete immediately, everything else completes once the test
     * says so.
     */
    private static final class Transport extends ChannelOutboundHandlerAdapter {
        final Queue<ChannelPromise> inFlight = new ArrayDeque<ChannelPromise>();
        int written;

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
            ByteBuf buf = (ByteBuf) msg;
            written += buf.readableBytes();
            boolean empty = !buf.isReadable();
            buf.release();
            if (empty) {
                promise.setSuccess();
            } else {
                inFlight.add(promise);
            }
        }

        void completeNext() {
            ChannelPromise promise = inFlight.poll();
            assertNotNull(promise);
            promise.setSuccess();
        }

        void failNext(Throwable cause) {
            ChannelPromise promise = inFlight.poll();
            assertNotNull(promise);
            promise.setFailure(cause);
        }
    }

    private static ByteBuf randomBytes(int length) {
        byte[] bytes = new byte[length];
        new Random(42).nextBytes(bytes);
        return Unpooled.wrappedBuffer(bytes);
    }

    private static EmbeddedChannel newChannel(BufferingEncoder encoder, Transport transport) {
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline().addLast(transport).addLast(encoder);
        return channel;
    }

    @Test
    public void lz4PromiseCompletedOnceBufferedDataWasWritten() {
        Transport transport = new Transport();
        EmbeddedChannel channel = newChannel(new Lz4FrameEncoder(), transport);

        ChannelPromise first = channel.newPromise();
        ChannelPromise second = channel.newPromise();
        channel.write(randomBytes(100), first);
        channel.write(randomBytes(100), second);

        // All the data is still buffered by the encoder.
        assertEquals(0, transport.written);
        assertFalse(first.isDone());
        assertFalse(second.isDone());

        channel.flush();
        assertTrue(transport.written > 0);
        assertFalse(first.isDone());
        assertFalse(second.isDone());

        transport.completeNext();
        assertTrue(first.isSuccess());
        assertTrue(second.isSuccess());
        assertFalse(channel.finish());
    }

    @Test
    public void lz4PromiseFailsWhenWritingBufferedDataFails() {
        Transport transport = new Transport();
        EmbeddedChannel channel = newChannel(new Lz4FrameEncoder(), transport);

        ChannelPromise promise = channel.newPromise();
        channel.write(randomBytes(100), promise);
        channel.flush();
        assertFalse(promise.isDone());

        Exception cause = new Exception();
        transport.failNext(cause);
        assertSame(cause, promise.cause());
        assertFalse(channel.finish());
    }

    @Test
    public void lz4PromiseOnlyCompletedOnceTailWasWritten() {
        Transport transport = new Transport();
        // Smallest block size, so the second message completes a block and leaves a tail behind.
        Lz4FrameEncoder encoder = new Lz4FrameEncoder(
                LZ4Factory.fastestInstance(), false, 64, new Lz4XXHash32(DEFAULT_SEED));
        EmbeddedChannel channel = newChannel(encoder, transport);

        ChannelPromise first = channel.newPromise();
        ChannelPromise second = channel.newPromise();
        channel.write(randomBytes(40), first);
        channel.write(randomBytes(40), second);

        // The first message is part of the block that was written, the second one only partly.
        assertEquals(1, transport.inFlight.size());
        assertFalse(first.isDone());
        assertFalse(second.isDone());

        transport.completeNext();
        assertTrue(first.isSuccess());
        assertFalse(second.isDone());

        channel.flush();
        assertFalse(second.isDone());
        transport.completeNext();
        assertTrue(second.isSuccess());
        assertFalse(channel.finish());
    }

    @Test
    public void lz4PromiseFailsWhenOnlyHeadOfMessageFailsToWrite() {
        Transport transport = new Transport();
        Lz4FrameEncoder encoder = new Lz4FrameEncoder(
                LZ4Factory.fastestInstance(), false, 64, new Lz4XXHash32(DEFAULT_SEED));
        EmbeddedChannel channel = newChannel(encoder, transport);

        ChannelPromise promise = channel.newPromise();
        channel.write(randomBytes(100), promise);
        assertEquals(1, transport.inFlight.size());

        Exception cause = new Exception();
        transport.failNext(cause);
        assertSame(cause, promise.cause());

        // Completing the remainder later must not change the outcome.
        channel.flush();
        transport.completeNext();
        assertSame(cause, promise.cause());
        assertFalse(channel.finish());
    }

    @Test
    public void lz4PromiseCompletedWhenMessageFillsBlockExactly() {
        Transport transport = new Transport();
        Lz4FrameEncoder encoder = new Lz4FrameEncoder(
                LZ4Factory.fastestInstance(), false, 64, new Lz4XXHash32(DEFAULT_SEED));
        EmbeddedChannel channel = newChannel(encoder, transport);

        ChannelPromise promise = channel.newPromise();
        channel.write(randomBytes(64), promise);
        assertFalse(promise.isDone());
        transport.completeNext();
        assertTrue(promise.isSuccess());
        assertFalse(channel.finish());
    }

    @Test
    public void lz4PendingPromiseCompletedByClose() {
        Transport transport = new Transport();
        Lz4FrameEncoder encoder = new Lz4FrameEncoder();
        EmbeddedChannel channel = newChannel(encoder, transport);

        ChannelPromise promise = channel.newPromise();
        channel.write(randomBytes(100), promise);
        assertFalse(promise.isDone());

        ChannelPromise closePromise = channel.newPromise();
        assertSame(closePromise, encoder.close(closePromise));
        assertFalse(promise.isDone());
        assertFalse(closePromise.isDone());

        transport.completeNext();
        assertTrue(promise.isSuccess());
        assertTrue(closePromise.isSuccess());
        assertFalse(channel.finish());
    }

    @Test
    public void lz4PendingPromiseFailedWhenRemoved() {
        Transport transport = new Transport();
        Lz4FrameEncoder encoder = new Lz4FrameEncoder();
        EmbeddedChannel channel = newChannel(encoder, transport);

        ChannelPromise promise = channel.newPromise();
        channel.write(randomBytes(100), promise);
        assertFalse(promise.isDone());

        channel.pipeline().remove(encoder);
        assertFalse(promise.isSuccess());
        assertNotNull(promise.cause());
        assertFalse(channel.finish());
    }

    @Test
    public void lz4BufferedDataCountsTowardsWritability() {
        Transport transport = new Transport();
        Lz4FrameEncoder encoder = new Lz4FrameEncoder();
        EmbeddedChannel channel = newChannel(encoder, transport);
        channel.config().setWriteBufferWaterMark(new WriteBufferWaterMark(100, 1000));
        assertTrue(channel.isWritable());

        // Nothing reaches the channel as the data is held back by the encoder, but it has to count nevertheless.
        for (int i = 0; i < 10; i++) {
            channel.write(randomBytes(100));
        }
        assertEquals(0, transport.written);
        assertFalse(channel.isWritable());

        // Writing the buffered data moves the bytes to the transport.
        channel.flush();
        assertTrue(channel.isWritable());
        assertFalse(channel.finish());
    }

    @Test
    public void lz4OnlyTailOfMessageCountsTowardsWritability() {
        Transport transport = new Transport();
        Lz4FrameEncoder encoder = new Lz4FrameEncoder(
                LZ4Factory.fastestInstance(), false, 64, new Lz4XXHash32(DEFAULT_SEED));
        EmbeddedChannel channel = newChannel(encoder, transport);
        // One buffered tail entry is accounted for with its size and some overhead, so 2000 bytes would not fit.
        channel.config().setWriteBufferWaterMark(new WriteBufferWaterMark(100, 1000));

        // All but 36 bytes are compressed and written right away.
        channel.write(randomBytes(2000 + 36 - (2000 % 64)));
        assertEquals(1, transport.inFlight.size());
        assertTrue(channel.isWritable());
        assertFalse(channel.finish());
    }

    @Test
    public void lz4BufferedDataNoLongerCountsOnceRemoved() {
        Transport transport = new Transport();
        Lz4FrameEncoder encoder = new Lz4FrameEncoder();
        EmbeddedChannel channel = newChannel(encoder, transport);
        channel.config().setWriteBufferWaterMark(new WriteBufferWaterMark(100, 1000));

        channel.write(randomBytes(2000));
        assertFalse(channel.isWritable());

        channel.pipeline().remove(encoder);
        assertTrue(channel.isWritable());
        assertFalse(channel.finish());
    }

    @Test
    public void bzip2PromiseCompletedOnceBlockWasWritten() {
        Transport transport = new Transport();
        Bzip2Encoder encoder = new Bzip2Encoder(1);
        EmbeddedChannel channel = newChannel(encoder, transport);

        ChannelPromise first = channel.newPromise();
        ChannelPromise second = channel.newPromise();
        channel.write(randomBytes(1000), first);
        channel.write(randomBytes(1000), second);

        // Only the stream header was written, the data is still in the block compressor.
        assertFalse(first.isDone());
        assertFalse(second.isDone());

        ChannelPromise third = channel.newPromise();
        // Larger than a block, so the first block is written and the tail is buffered again.
        channel.write(randomBytes(150000), third);
        transport.completeNext();
        transport.completeNext();
        assertTrue(first.isSuccess());
        assertTrue(second.isSuccess());
        assertFalse(third.isDone());

        ChannelPromise closePromise = channel.newPromise();
        encoder.close(closePromise);
        assertFalse(third.isDone());
        while (!transport.inFlight.isEmpty()) {
            transport.completeNext();
        }
        assertTrue(third.isSuccess());
        assertTrue(closePromise.isSuccess());
        assertFalse(channel.finish());
    }
}
