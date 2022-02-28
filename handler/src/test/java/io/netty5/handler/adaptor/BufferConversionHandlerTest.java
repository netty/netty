/*
 * Copyright 2022 The Netty Project
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
package io.netty5.handler.adaptor;

import io.netty5.buffer.ByteBuf;
import io.netty5.buffer.ByteBufAllocator;
import io.netty5.buffer.CompositeByteBuf;
import io.netty5.buffer.Unpooled;
import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.buffer.api.CompositeBuffer;
import io.netty5.buffer.api.DefaultBufferAllocators;
import io.netty5.buffer.api.Resource;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelPipeline;
import io.netty5.handler.adaptor.BufferConversionHandler.Conversion;
import io.netty5.util.Attribute;
import io.netty5.util.AttributeKey;
import io.netty5.util.concurrent.EventExecutor;
import io.netty5.util.concurrent.Future;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;
import java.util.stream.Stream.Builder;

import static java.util.Arrays.copyOfRange;
import static org.assertj.core.api.Assertions.assertThat;

class BufferConversionHandlerTest {
    private static final Object UNKNOWN_MSG = new Object();
    private static final byte[] DATA = "some data for my buffers".getBytes(StandardCharsets.UTF_8);

    static Stream<TestData> data() {
        int mid = DATA.length / 2;
        int len = DATA.length;
        Builder<TestData> builder = Stream.builder();
        ByteBuf a = Unpooled.wrappedBuffer(DATA);
        CompositeByteBuf b = new CompositeByteBuf(a.alloc(), false, 2,
                                                  Unpooled.wrappedBuffer(copyOfRange(DATA, 0, mid)),
                                                  Unpooled.wrappedBuffer(copyOfRange(DATA, mid, len)));
        Buffer c = DefaultBufferAllocators.onHeapAllocator().copyOf(DATA);
        Buffer tmp = c.copy();
        Buffer d = CompositeBuffer.compose(DefaultBufferAllocators.onHeapAllocator(),
                                           tmp.readSplit(mid).send(),
                                           tmp.send());
        for (ByteBuf byteBuf : List.of(a, b)) {
            for (Buffer buffer : List.of(c, d)) {
                builder.add(new TestData(byteBuf.copy(), buffer.copy()));
            }
        }
        Resource.dispose(a);
        Resource.dispose(b);
        Resource.dispose(c);
        Resource.dispose(d);
        return builder.build();
    }

    @ParameterizedTest
    @MethodSource("data")
    public void convertByteBufToBuffer(TestData testData) {
        testData.touch();
        assertThat(Conversion.BYTEBUF_TO_BUFFER.convert(testData.byteBuf)).isEqualTo(testData.buffer);
        assertThat(Conversion.BYTEBUF_TO_BUFFER.convert(testData.buffer)).isSameAs(testData.buffer);
        assertThat(Conversion.BYTEBUF_TO_BUFFER.convert(UNKNOWN_MSG)).isSameAs(UNKNOWN_MSG);
    }

    @ParameterizedTest
    @MethodSource("data")
    public void convertBufferToByteBuf(TestData testData) {
        testData.touch();
        assertThat(Conversion.BUFFER_TO_BYTEBUF.convert(testData.buffer)).isEqualTo(testData.byteBuf);
        assertThat(Conversion.BUFFER_TO_BYTEBUF.convert(testData.byteBuf)).isSameAs(testData.byteBuf);
        assertThat(Conversion.BUFFER_TO_BYTEBUF.convert(UNKNOWN_MSG)).isSameAs(UNKNOWN_MSG);
    }

    @ParameterizedTest
    @MethodSource("data")
    public void convertBoth(TestData testData) {
        testData.touch();
        assertThat(Conversion.BOTH.convert(testData.byteBuf)).isEqualTo(testData.buffer);
        assertThat(Conversion.BOTH.convert(testData.buffer)).isEqualTo(testData.byteBuf);
        assertThat(Conversion.BOTH.convert(UNKNOWN_MSG)).isSameAs(UNKNOWN_MSG);
    }

    @ParameterizedTest
    @MethodSource("data")
    public void convertNone(TestData testData) {
        testData.touch();
        assertThat(Conversion.NONE.convert(testData.byteBuf)).isSameAs(testData.byteBuf);
        assertThat(Conversion.NONE.convert(testData.buffer)).isSameAs(testData.buffer);
        assertThat(Conversion.NONE.convert(UNKNOWN_MSG)).isSameAs(UNKNOWN_MSG);
    }

    @ParameterizedTest
    @MethodSource("data")
    public void channelReadConversion(TestData testData) throws Exception {
        BufferConversionHandler handler = new BufferConversionHandler(
                Conversion.BYTEBUF_TO_BUFFER, Conversion.NONE, Conversion.NONE);
        class ReadContext extends ChannelHandlerContextStub {
            Buffer readBuffer;

            @Override
            public ChannelHandlerContext fireChannelRead(Object msg) {
                readBuffer = (Buffer) msg;
                return this;
            }
        }
        ReadContext ctx = new ReadContext();
        handler.channelRead(ctx, testData.byteBuf);
        assertThat(ctx.readBuffer).isEqualTo(testData.buffer);

        ctx.readBuffer = null;
        handler.channelRead(ctx, testData.buffer);
        assertThat(ctx.readBuffer).isSameAs(testData.buffer);
    }

    @ParameterizedTest
    @MethodSource("data")
    public void userEventTriggeredConversion(TestData testData) throws Exception {
        BufferConversionHandler handler = new BufferConversionHandler(
                Conversion.NONE, Conversion.NONE, Conversion.BYTEBUF_TO_BUFFER);
        class UserEventContext extends ChannelHandlerContextStub {
            Buffer userEventBuffer;

            @Override
            public ChannelHandlerContext fireUserEventTriggered(Object evt) {
                userEventBuffer = (Buffer) evt;
                return this;
            }
        }
        UserEventContext ctx = new UserEventContext();
        handler.userEventTriggered(ctx, testData.byteBuf);
        assertThat(ctx.userEventBuffer).isEqualTo(testData.buffer);

        ctx.userEventBuffer = null;
        handler.userEventTriggered(ctx, testData.buffer);
        assertThat(ctx.userEventBuffer).isSameAs(testData.buffer);
    }

    @ParameterizedTest
    @MethodSource("data")
    public void writeConversion(TestData testData) throws Exception {
        BufferConversionHandler handler = new BufferConversionHandler(
            Conversion.NONE, Conversion.BYTEBUF_TO_BUFFER, Conversion.NONE);
        class WriteContext extends ChannelHandlerContextStub {
            Buffer writeBuffer;

            @Override
            public Future<Void> write(Object msg) {
                writeBuffer = (Buffer) msg;
                return null;
            }
        }
        WriteContext ctx = new WriteContext();
        handler.write(ctx, testData.byteBuf);
        assertThat(ctx.writeBuffer).isEqualTo(testData.buffer);

        ctx.writeBuffer = null;
        handler.write(ctx, testData.buffer);
        assertThat(ctx.writeBuffer).isSameAs(testData.buffer);
    }

    static final class TestData implements AutoCloseable {
        private final ByteBuf byteBuf;
        private final Buffer buffer;

        TestData(ByteBuf byteBuf, Buffer buffer) {
            this.byteBuf = byteBuf;
            this.buffer = buffer;
        }

        /**
         * Touching the buffers with a description of the test data will, together with the stack trace that points to
         * the particular test in question, help us identify and fix leaks.
         * <p>
         * Conversion buffers should not lead to leaks, regardless of how temporary this code might be intended to be.
         */
        void touch() {
            String description = toString();
            byteBuf.touch(description);
            buffer.touch(description);
        }

        @Override
        public String toString() {
            return "TestData(" +
                   byteBuf + " (" + byteBuf.getClass().getSimpleName() + "), " +
                   buffer + " (" + buffer.getClass().getSimpleName() + "))";
        }

        @Override
        public void close() {
            // JUnit automatically closes test parameters that are AutoCloseable.
            Resource.dispose(byteBuf);
            Resource.dispose(buffer);
        }
    }

    static class ChannelHandlerContextStub implements ChannelHandlerContext {
        @Override
        public Channel channel() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String name() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChannelHandler handler() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isRemoved() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChannelHandlerContext fireChannelRegistered() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChannelHandlerContext fireChannelUnregistered() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChannelHandlerContext fireChannelActive() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChannelHandlerContext fireChannelInactive() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChannelHandlerContext fireExceptionCaught(Throwable cause) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChannelHandlerContext fireUserEventTriggered(Object evt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChannelHandlerContext fireChannelRead(Object msg) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChannelHandlerContext fireChannelReadComplete() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChannelHandlerContext fireChannelWritabilityChanged() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Future<Void> bind(SocketAddress localAddress) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Future<Void> connect(SocketAddress remoteAddress) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Future<Void> connect(SocketAddress remoteAddress, SocketAddress localAddress) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Future<Void> disconnect() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Future<Void> close() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Future<Void> register() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Future<Void> deregister() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChannelHandlerContext read() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Future<Void> write(Object msg) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChannelHandlerContext flush() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Future<Void> writeAndFlush(Object msg) {
            throw new UnsupportedOperationException();
        }

        @Override
        public EventExecutor executor() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChannelPipeline pipeline() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBufAllocator alloc() {
            throw new UnsupportedOperationException();
        }

        @Override
        public BufferAllocator bufferAllocator() {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> Attribute<T> attr(AttributeKey<T> key) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> boolean hasAttr(AttributeKey<T> key) {
            throw new UnsupportedOperationException();
        }
    }
}
