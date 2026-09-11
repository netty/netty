/*
 * Copyright 2025 The Netty Project
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
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static io.netty.handler.codec.compression.Decompressor.Status.COMPLETE;
import static io.netty.handler.codec.compression.Decompressor.Status.NEED_INPUT;
import static io.netty.handler.codec.compression.Decompressor.Status.NEED_OUTPUT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BackpressureDecompressionHandlerTest {
    private static final String READ_COMPLETE = "readComplete";

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void maxMessages(boolean autoRead) {
        EmbeddedChannel channel = new EmbeddedChannel(
                new NumberToBuffer(),
                BackpressureDecompressionHandler.builder(new MockDecompressor.Builder()
                                .needInput()
                                .needOutput(4)
                                .complete())
                        .backpressureGaugeBuilder(BackpressureGauge.builder().messagesPerRead(2))
                        .build(),
                new BufferToNumber()
        );
        channel.config().setAutoRead(autoRead);

        channel.writeInbound(0);

        assertEquals(1, channel.<Integer>readInbound());
        assertEquals(2, channel.<Integer>readInbound());

        if (!autoRead) {
            assertEquals(READ_COMPLETE, channel.readInbound());

            assertNull(channel.readInbound());
            channel.read();
        }

        assertEquals(3, channel.<Integer>readInbound());
        assertEquals(4, channel.<Integer>readInbound());
        assertEquals(BackpressureDecompressionHandler.EndOfContentEvent.INSTANCE, channel.readInbound());
        assertEquals(READ_COMPLETE, channel.readInbound());
        assertNull(channel.readInbound());

        channel.finish();
    }

    @Test
    public void endOfInput() {
        EmbeddedChannel channel = new EmbeddedChannel(
                new NumberToBuffer(),
                BackpressureDecompressionHandler.builder(new MockDecompressor.Builder()
                                .needInput()
                                .needOutput(1)
                                .needInput()
                                .needOutput(1)
                                .complete())
                        .backpressureGaugeBuilder(BackpressureGauge.builder().messagesPerRead(2))
                        .build(),
                new BufferToNumber()
        );

        channel.writeInbound(0);
        assertEquals(1, channel.<Integer>readInbound());
        assertEquals(READ_COMPLETE, channel.readInbound());

        channel.pipeline().firstContext()
                .fireUserEventTriggered(BackpressureDecompressionHandler.EndOfContentEvent.INSTANCE);

        assertEquals(3, channel.<Integer>readInbound());
        assertEquals(BackpressureDecompressionHandler.EndOfContentEvent.INSTANCE, channel.readInbound());
        assertEquals(READ_COMPLETE, channel.readInbound());
        assertNull(channel.readInbound());

        channel.finish();
    }

    @Test
    public void replaysHeldMessages() {
        EmbeddedChannel channel = new EmbeddedChannel(
                new NumberToBuffer(),
                BackpressureDecompressionHandler.builder(new MockDecompressor.Builder()
                                .needInput()
                                .needOutput(2)
                                .needInput()
                                .needOutput(1)
                                .complete())
                        .backpressureGaugeBuilder(BackpressureGauge.builder().messagesPerRead(1))
                        .build(),
                new BufferToNumber()
        );
        channel.config().setAutoRead(false);

        channel.writeInbound(0, 3);

        assertEquals(1, channel.<Integer>readInbound());
        assertEquals(READ_COMPLETE, channel.readInbound());
        assertNull(channel.readInbound());

        channel.read();

        assertEquals(2, channel.<Integer>readInbound());
        assertEquals(READ_COMPLETE, channel.readInbound());
        assertNull(channel.readInbound());

        channel.read();

        assertEquals(4, channel.<Integer>readInbound());
        assertEquals(BackpressureDecompressionHandler.EndOfContentEvent.INSTANCE, channel.readInbound());
        assertEquals(READ_COMPLETE, channel.readInbound());
        assertNull(channel.readInbound());

        channel.finish();
    }

    @Test
    public void preservesReadFromChannelRead() {
        ReadCounter readCounter = new ReadCounter();
        EmbeddedChannel channel = new EmbeddedChannel(
                readCounter,
                new NumberToBuffer(),
                BackpressureDecompressionHandler.builder(new MockDecompressor.Builder()
                                .needInput()
                                .needOutput(1)
                                .needInput())
                        .backpressureGaugeBuilder(BackpressureGauge.builder().messagesPerRead(1))
                        .build(),
                new BufferToNumber(),
                new ReadOnOne()
        );
        channel.config().setAutoRead(false);

        channel.writeInbound(0);

        assertEquals(1, channel.<Integer>readInbound());
        assertEquals(READ_COMPLETE, channel.readInbound());
        assertNull(channel.readInbound());
        assertEquals(1, readCounter.reads);

        channel.finish();
    }

    @Test
    public void preservesReadAfterCompletion() {
        ReadCounter readCounter = new ReadCounter();
        EmbeddedChannel channel = new EmbeddedChannel(
                readCounter,
                new NumberToBuffer(),
                BackpressureDecompressionHandler.builder(new MockDecompressor.Builder()
                                .needInput()
                                .needOutput(1)
                                .complete())
                        .backpressureGaugeBuilder(BackpressureGauge.builder().messagesPerRead(1))
                        .build(),
                new BufferToNumber(),
                new ReadOnOne()
        );
        channel.config().setAutoRead(false);

        channel.writeInbound(0);

        assertEquals(1, channel.<Integer>readInbound());
        assertEquals(BackpressureDecompressionHandler.EndOfContentEvent.INSTANCE, channel.readInbound());
        assertEquals(READ_COMPLETE, channel.readInbound());
        assertNull(channel.readInbound());
        assertEquals(1, readCounter.reads);

        channel.finish();
    }

    @Test
    public void reentrantReadIsSatisfiedByOutput() {
        ReadCounter readCounter = new ReadCounter();
        EmbeddedChannel channel = new EmbeddedChannel(
                readCounter,
                new NumberToBuffer(),
                BackpressureDecompressionHandler.builder(new MockDecompressor.Builder()
                                .needInput()
                                .needOutput(2)
                                .needInput())
                        .backpressureGaugeBuilder(BackpressureGauge.builder().messagesPerRead(2))
                        .build(),
                new BufferToNumber(),
                new ReadOnOne()
        );
        channel.config().setAutoRead(false);

        channel.writeInbound(0);

        assertEquals(1, channel.<Integer>readInbound());
        assertEquals(2, channel.<Integer>readInbound());
        assertEquals(READ_COMPLETE, channel.readInbound());
        assertNull(channel.readInbound());
        assertEquals(0, readCounter.reads);

        channel.finish();
    }

    @Test
    public void boundsInputBytesWithoutOutput() {
        InputOnRead input = new InputOnRead();
        EmbeddedChannel channel = new EmbeddedChannel(
                input,
                BackpressureDecompressionHandler.builder(new MockDecompressor.Builder()
                                .needInput()
                                .needInput()
                                .needInput())
                        .backpressureGaugeBuilder(BackpressureGauge.builder()
                                .messagesPerRead(64)
                                .bytesPerRead(8))
                        .build(),
                new BufferToNumber()
        );
        channel.config().setAutoRead(false);

        channel.read();

        assertEquals(2, input.reads);
        assertEquals(READ_COMPLETE, channel.readInbound());
        assertNull(channel.readInbound());

        channel.finish();
    }

    @Test
    public void boundsEmptyInputWithoutOutput() {
        EmptyInputOnRead input = new EmptyInputOnRead();
        EmbeddedChannel channel = new EmbeddedChannel(
                input,
                BackpressureDecompressionHandler.builder(new MockDecompressor.Builder().needInput())
                        .backpressureGaugeBuilder(BackpressureGauge.builder().messagesPerRead(2))
                        .build(),
                new BufferToNumber()
        );
        channel.config().setAutoRead(false);

        channel.read();

        assertEquals(2, input.reads);
        assertEquals(READ_COMPLETE, channel.readInbound());
        assertNull(channel.readInbound());

        channel.finish();
    }

    @Test
    public void forwardsReadCompleteWithoutOutputWithAutoRead() {
        EmbeddedChannel channel = new EmbeddedChannel(
                new NumberToBuffer(),
                BackpressureDecompressionHandler.create(new MockDecompressor.Builder().needInput().needInput()),
                new BufferToNumber()
        );

        channel.writeInbound(0);

        assertEquals(READ_COMPLETE, channel.readInbound());
        assertNull(channel.readInbound());

        channel.finish();
    }

    @Test
    public void preservesReadAfterEndOfInputWithoutOutput() {
        ReadCounter readCounter = new ReadCounter();
        EmbeddedChannel channel = new EmbeddedChannel(
                readCounter,
                BackpressureDecompressionHandler.create(new MockDecompressor.Builder()
                        .needInput()
                        .complete()),
                new ReadOnEndOfContent()
        );
        channel.config().setAutoRead(false);

        channel.pipeline().firstContext()
                .fireUserEventTriggered(BackpressureDecompressionHandler.EndOfContentEvent.INSTANCE);

        assertEquals(1, readCounter.reads);

        channel.finish();
    }

    @Test
    public void handlerRemovalWhileResumingOutput() {
        BackpressureDecompressionHandler.Builder builder = BackpressureDecompressionHandler.builder(
                new MockDecompressor.Builder().needInput().needOutput(2).complete());
        builder.backpressureGaugeBuilder(BackpressureGauge.builder().messagesPerRead(1));
        BackpressureDecompressionHandler handler = builder.build();
        EmbeddedChannel channel = new EmbeddedChannel(
                new NumberToBuffer(), handler, new BufferToNumber(), new RemoveHandlerOnTwo(handler));
        channel.config().setAutoRead(false);

        channel.writeInbound(0);
        assertEquals(1, channel.<Integer>readInbound());
        assertEquals(READ_COMPLETE, channel.readInbound());

        channel.read();

        assertEquals(2, channel.<Integer>readInbound());
        assertNull(channel.readInbound());
        assertNull(channel.pipeline().context(handler));

        channel.finish();
    }

    @Test
    public void handlerRemovalOnStatusFailureWhileResumingOutput() {
        BackpressureDecompressionHandler.Builder builder = BackpressureDecompressionHandler.builder(
                new MockDecompressor.Builder()
                        .needInput().needOutput(2).needInput().failStatusCall(9));
        builder.backpressureGaugeBuilder(BackpressureGauge.builder().messagesPerRead(1));
        BackpressureDecompressionHandler handler = builder.build();
        EmbeddedChannel channel = new EmbeddedChannel(
                new NumberToBuffer(), handler, new BufferToNumber(), new RemoveHandlerOnException(handler));
        channel.config().setAutoRead(false);

        channel.writeInbound(0);
        assertEquals(1, channel.<Integer>readInbound());
        assertEquals(READ_COMPLETE, channel.readInbound());

        channel.read();

        assertEquals(2, channel.<Integer>readInbound());
        assertEquals(BackpressureDecompressionHandler.EndOfContentEvent.INSTANCE, channel.readInbound());
        assertNull(channel.readInbound());
        assertNull(channel.pipeline().context(handler));

        channel.finish();
    }

    private static ByteBuf numberedBuffer(int index) {
        ByteBuf buf = ByteBufAllocator.DEFAULT.buffer(4);
        buf.writeInt(index);
        return buf;
    }

    private static final class NumberToBuffer extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            ctx.fireChannelRead(numberedBuffer((Integer) msg));
        }
    }

    private static final class BufferToNumber extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            ByteBuf buf = (ByteBuf) msg;
            ctx.fireChannelRead(buf.readInt());
            buf.release();
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
            ctx.fireChannelRead(READ_COMPLETE);
            ctx.fireChannelReadComplete();
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            if (evt == BackpressureDecompressionHandler.EndOfContentEvent.INSTANCE) {
                ctx.fireChannelRead(BackpressureDecompressionHandler.EndOfContentEvent.INSTANCE);
                ctx.fireChannelReadComplete();
            }
        }
    }

    private static final class RemoveHandlerOnTwo extends ChannelInboundHandlerAdapter {
        private final BackpressureDecompressionHandler handler;

        RemoveHandlerOnTwo(BackpressureDecompressionHandler handler) {
            this.handler = handler;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (Integer.valueOf(2).equals(msg)) {
                ctx.pipeline().remove(handler);
            }
            ctx.fireChannelRead(msg);
        }
    }

    private static final class ReadOnOne extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (Integer.valueOf(1).equals(msg)) {
                ctx.read();
            }
            ctx.fireChannelRead(msg);
        }
    }

    private static final class ReadCounter extends ChannelDuplexHandler {
        private int reads;

        @Override
        public void read(ChannelHandlerContext ctx) throws Exception {
            reads++;
        }
    }

    private static final class ReadOnEndOfContent extends ChannelInboundHandlerAdapter {
        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            if (evt == BackpressureDecompressionHandler.EndOfContentEvent.INSTANCE) {
                ctx.read();
            }
            ctx.fireUserEventTriggered(evt);
        }
    }

    private static final class InputOnRead extends ChannelDuplexHandler {
        private int reads;

        @Override
        public void read(ChannelHandlerContext ctx) throws Exception {
            ctx.fireChannelRead(numberedBuffer(reads++));
            ctx.fireChannelReadComplete();
        }
    }

    private static final class EmptyInputOnRead extends ChannelDuplexHandler {
        private int reads;

        @Override
        public void read(ChannelHandlerContext ctx) throws Exception {
            reads++;
            ctx.fireChannelRead(Unpooled.EMPTY_BUFFER);
            ctx.fireChannelReadComplete();
        }
    }

    private static final class RemoveHandlerOnException extends ChannelInboundHandlerAdapter {
        private final BackpressureDecompressionHandler handler;

        RemoveHandlerOnException(BackpressureDecompressionHandler handler) {
            this.handler = handler;
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            ctx.pipeline().remove(handler);
        }
    }

    private static final class MockDecompressor implements Decompressor {
        private final List<Status> events;
        private final int failStatusCall;
        private int index;
        private int statusCalls;

        MockDecompressor(List<Status> events, int failStatusCall) {
            this.events = events;
            this.failStatusCall = failStatusCall;
        }

        @Override
        public Status status() throws DecompressionException {
            if (++statusCalls == failStatusCall) {
                throw new DecompressionException("status failure");
            }
            return events.get(index);
        }

        @Override
        public void addInput(ByteBuf buf) throws DecompressionException {
            assertEquals(NEED_INPUT, status());
            assertEquals(index++, buf.readInt());
            buf.release();
        }

        @Override
        public void endOfInput() throws DecompressionException {
            assertEquals(NEED_INPUT, status());
            index++;
        }

        @Override
        public ByteBuf takeOutput() throws DecompressionException {
            assertEquals(NEED_OUTPUT, status());
            return numberedBuffer(index++);
        }

        @Override
        public void close() throws DecompressionException {
        }

        static final class Builder extends AbstractDecompressorBuilder {
            private final List<Status> events = new ArrayList<>();
            private int failStatusCall = -1;

            Builder needInput() {
                events.add(NEED_INPUT);
                return this;
            }

            Builder needOutput(int count) {
                for (int i = 0; i < count; i++) {
                    events.add(NEED_OUTPUT);
                }
                return this;
            }

            Builder complete() {
                events.add(COMPLETE);
                return this;
            }

            Builder failStatusCall(int failStatusCall) {
                this.failStatusCall = failStatusCall;
                return this;
            }

            @Override
            public Decompressor build(ByteBufAllocator allocator) throws DecompressionException {
                return new DefensiveDecompressor(new MockDecompressor(events, failStatusCall));
            }
        }
    }
}
