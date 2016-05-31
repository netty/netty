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

public class BulkLengthFieldBasedFrameDecoderTest {

    @Test(expected = IllegalArgumentException.class)
    public void decoderWithZeroMaxFrameLengthShouldThrowException() {
        new BulkLengthFieldBasedFrameDecoder(0, 1, 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void decoderWithNegativeMaxFrameLengthShouldThrowException() {
        new BulkLengthFieldBasedFrameDecoder(-1, 1, 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void decoderWithNegativeLengthFieldOffsetShouldThrowException() {
        new BulkLengthFieldBasedFrameDecoder(1, -1, 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void decoderWithTooBigLengthFieldOffsetShouldThrowException() {
        new BulkLengthFieldBasedFrameDecoder(10, 8, 8);
    }

    @Test
    public void writingNotAByteBufShouldPassThePipeline() {
        final EmbeddedChannel channel = new EmbeddedChannel(new BulkLengthFieldBasedFrameDecoder(2, 0, 1));
        final Object marker = new Object();

        channel.writeInbound(marker);
        final Object receivedObject = channel.readInbound();

        assertThat(receivedObject, is(equalTo(marker)));

        channel.finish();
    }

    @Test
    public void writingNotEnoughBytesReadsNothing() {
        final EmbeddedChannel channel = new EmbeddedChannel(new BulkLengthFieldBasedFrameDecoder(6, 2, 1));
        final ByteBuf chunk = Unpooled.copiedBuffer(new byte[] { 1, 1, 3, 0, 0 });

        channel.writeInbound(chunk);
        final Object receivedObject = channel.readInbound();
        assertThat(receivedObject, is(equalTo(null)));

        channel.finish();
    }

    @Test
    public void writingOneAndAHalfMessageShouldRetrieveOnlyOneMessage() {
        final EmbeddedChannel channel =
                new EmbeddedChannel(new BulkLengthFieldBasedFrameDecoder(10, 2, 1));
        final byte[] completeFrame = { 1, 1, 3, 0, 0, 0 };
        final byte[] halfFrame = { 1, 1, 2, 0 };
        final ByteBuf chunk = Unpooled.copiedBuffer(completeFrame, halfFrame);

        channel.writeInbound(chunk);
        final ByteBuf receivedChunk = channel.readInbound();
        assertThat(receivedChunk.readableBytes(), is(equalTo(completeFrame.length)));
        receivedChunk.release();

        channel.finish();
    }

    @Test
    public void leftBytesShouldAccumulate() {
        final EmbeddedChannel channel =
                new EmbeddedChannel(new BulkLengthFieldBasedFrameDecoder(10, 2, 1));
        final byte[] completeFrame = { 1, 1, 3, 0, 0, 0 };
        final byte[] halfFrame = { 1, 1, 4, 0 };
        final byte[] anotherHalfFrame = { 0, 0, 0 };
        ByteBuf chunk = Unpooled.copiedBuffer(completeFrame, halfFrame);

        channel.writeInbound(chunk);
        ByteBuf receivedChunk = channel.readInbound();
        assertThat(receivedChunk.readableBytes(), is(equalTo(completeFrame.length)));
        receivedChunk.release();

        chunk = Unpooled.copiedBuffer(anotherHalfFrame);
        channel.writeInbound(chunk);
        receivedChunk = channel.readInbound();
        assertThat(receivedChunk.readableBytes(), is(equalTo(halfFrame.length + anotherHalfFrame.length)));
        receivedChunk.release();

        channel.finish();
    }
}
