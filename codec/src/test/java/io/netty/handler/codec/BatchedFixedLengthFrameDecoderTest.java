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

public class BatchedFixedLengthFrameDecoderTest {

    @Test(expected = IllegalArgumentException.class)
    public void decoderWithZeroFrameLengthShouldThrowException() {
        new BatchedFixedLengthFrameDecoder(0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void decoderWithNegativeFrameLengthShouldThrowException() {
        new BatchedFixedLengthFrameDecoder(-1);
    }

    @Test
    public void writingNotAByteBufShouldPassThePipeline() {
        final int frameLength = 10;
        final EmbeddedChannel channel = new EmbeddedChannel(new BatchedFixedLengthFrameDecoder(frameLength));
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
        final int frameLength = 10;
        final EmbeddedChannel channel = new EmbeddedChannel(new BatchedFixedLengthFrameDecoder(frameLength));
        final ByteBuf chunk = Unpooled.copiedBuffer(new byte[5]);

        final boolean inboundBufferChanged = channel.writeInbound(chunk);
        final Object receivedObject = channel.readInbound();

        assertThat(inboundBufferChanged, is(false));
        assertThat(receivedObject, is(equalTo(null)));

        final boolean bufferReadable = channel.finishAndReleaseAll();

        assertThat(bufferReadable, is(false));
    }

    @Test
    public void writingOneAndAHalfMessageShouldRetrieveOnlyOneMessage() {
        final int frameLength = 10;
        final int oneAndAHalfMessageLength = frameLength + frameLength / 2;
        final EmbeddedChannel channel =
                new EmbeddedChannel(new BatchedFixedLengthFrameDecoder(frameLength));
        final ByteBuf chunk = Unpooled.copiedBuffer(new byte[oneAndAHalfMessageLength]);

        final boolean inboundBufferChanged = channel.writeInbound(chunk);
        final ByteBuf receivedChunk = channel.readInbound();

        assertThat(inboundBufferChanged, is(true));
        assertThat(receivedChunk.readableBytes(), is(equalTo(frameLength)));
        receivedChunk.release();

        final boolean bufferReadable = channel.finishAndReleaseAll();

        assertThat(bufferReadable, is(false));
    }

    @Test
    public void leftBytesShouldAccumulate() {
        final int frameLength = 10;
        final EmbeddedChannel channel =
                new EmbeddedChannel(new BatchedFixedLengthFrameDecoder(frameLength));

        boolean inboundBufferChanged = channel.writeInbound(Unpooled.copiedBuffer(new byte[15]));
        ByteBuf receivedChunk = channel.readInbound();

        assertThat(inboundBufferChanged, is(true));
        assertThat(receivedChunk.readableBytes(), is(equalTo(frameLength)));
        receivedChunk.release();

        inboundBufferChanged = channel.writeInbound(Unpooled.copiedBuffer(new byte[10]));
        receivedChunk = channel.readInbound();

        assertThat(inboundBufferChanged, is(true));
        assertThat(receivedChunk.readableBytes(), is(equalTo(frameLength)));
        receivedChunk.release();

        receivedChunk = channel.readInbound();
        assertThat(receivedChunk, is(equalTo(null)));

        final boolean bufferReadable = channel.finishAndReleaseAll();

        assertThat(bufferReadable, is(false));
    }
}
