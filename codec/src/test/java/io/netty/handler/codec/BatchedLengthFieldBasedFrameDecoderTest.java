/*
 * Copyright 2016 The Netty Project
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

package io.netty.handler.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

public class BatchedLengthFieldBasedFrameDecoderTest {

    @Test(expected = IllegalArgumentException.class)
    public void decoderWithZeroMaxFrameLengthShouldThrowException() {
        new BatchedLengthFieldBasedFrameDecoder(0, 1, 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void decoderWithNegativeMaxFrameLengthShouldThrowException() {
        new BatchedLengthFieldBasedFrameDecoder(-1, 1, 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void decoderWithNegativeLengthFieldOffsetShouldThrowException() {
        new BatchedLengthFieldBasedFrameDecoder(1, -1, 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void decoderWithTooBigLengthFieldOffsetShouldThrowException() {
        new BatchedLengthFieldBasedFrameDecoder(10, 8, 8);
    }

    @Test
    public void writingNotAByteBufShouldPassThePipeline() {
        final EmbeddedChannel channel = new EmbeddedChannel(new BatchedLengthFieldBasedFrameDecoder(2, 0, 1));
        final Object marker = new Object();

        final boolean inboundBufferChanged = channel.writeInbound(marker);
        final Object receivedObject = channel.readInbound();

        assertThat(inboundBufferChanged, is(true));
        assertThat(receivedObject, is(equalTo(marker)));

        final boolean bufferReadable = channel.finishAndReleaseAll();

        assertThat(bufferReadable, is(false));
    }

    @Test
    public void writingNotEnoughBytesReadsNothing() {
        final EmbeddedChannel channel = new EmbeddedChannel(new BatchedLengthFieldBasedFrameDecoder(6, 2, 1));
        final ByteBuf chunk = Unpooled.copiedBuffer(new byte[] { 1, 1, 3, 0, 0 });

        final boolean inboundBufferChanged = channel.writeInbound(chunk);
        final Object receivedObject = channel.readInbound();

        assertThat(inboundBufferChanged, is(false));
        assertThat(receivedObject, is(equalTo(null)));

        final boolean bufferReadable = channel.finishAndReleaseAll();

        assertThat(bufferReadable, is(false));
    }

    @Test
    public void writingOneAndAHalfMessageShouldRetrieveOnlyOneMessage() {
        final EmbeddedChannel channel =
                new EmbeddedChannel(new BatchedLengthFieldBasedFrameDecoder(10, 2, 1));
        final byte[] completeFrame = { 1, 1, 3, 0, 0, 0 };
        final byte[] halfFrame = { 1, 1, 2, 0 };
        final ByteBuf chunk = Unpooled.copiedBuffer(completeFrame, halfFrame);

        final boolean inboundBufferChanged = channel.writeInbound(chunk);
        final ByteBuf receivedChunk = channel.readInbound();

        assertThat(inboundBufferChanged, is(true));
        assertThat(receivedChunk.readableBytes(), is(equalTo(completeFrame.length)));
        receivedChunk.release();

        final boolean bufferReadable = channel.finishAndReleaseAll();

        assertThat(bufferReadable, is(false));
    }

    @Test
    public void leftBytesShouldAccumulate() {
        final EmbeddedChannel channel =
                new EmbeddedChannel(new BatchedLengthFieldBasedFrameDecoder(10, 2, 1));
        final byte[] completeFrame = { 1, 1, 3, 0, 0, 0 };
        final byte[] halfFrame = { 1, 1, 4, 0 };
        final byte[] anotherHalfFrame = { 0, 0, 0 };
        ByteBuf chunk = Unpooled.copiedBuffer(completeFrame, halfFrame);

        boolean inboundBufferChanged = channel.writeInbound(chunk);
        ByteBuf receivedChunk = channel.readInbound();

        assertThat(inboundBufferChanged, is(true));
        assertThat(receivedChunk.readableBytes(), is(equalTo(completeFrame.length)));
        receivedChunk.release();

        chunk = Unpooled.copiedBuffer(anotherHalfFrame);
        inboundBufferChanged = channel.writeInbound(chunk);
        receivedChunk = channel.readInbound();

        assertThat(inboundBufferChanged, is(true));
        assertThat(receivedChunk.readableBytes(), is(equalTo(halfFrame.length + anotherHalfFrame.length)));
        receivedChunk.release();

        final boolean bufferReadable = channel.finishAndReleaseAll();

        assertThat(bufferReadable, is(false));
    }
}
