/*
 * Copyright 2024 The Netty Project
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
package io.netty.handler.ssl;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.ByteBuffer;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SslHandlerCoalescingBufferQueueTest {
    static final Supplier<ByteBuf> BYTEBUF_SUPPLIER = new Supplier<ByteBuf>() {
        @Override
        public ByteBuf get() {
            ByteBuf buf = Unpooled.directBuffer(128);
            buf.writeLong(1);
            buf.writeLong(2);
            return buf;
        }
    };
    static final Function<ByteBuf, ByteBuf> NO_WRAPPER = new Function<ByteBuf, ByteBuf>() {
        @Override
        public ByteBuf apply(ByteBuf byteBuf) {
            return byteBuf;
        }
    };
    static final Function<ByteBuf, ByteBuf> DUPLICATE_WRAPPER = new Function<ByteBuf, ByteBuf>() {
        @Override
        public ByteBuf apply(ByteBuf byteBuf) {
            return byteBuf.duplicate();
        }
    };
    static final Function<ByteBuf, ByteBuf> SLICE_WRAPPER = new Function<ByteBuf, ByteBuf>() {
        @Override
        public ByteBuf apply(ByteBuf byteBuf) {
            return byteBuf.slice();
        }
    };
    static ByteBuffer createNioBuffer() {
        ByteBuffer nioBuffer = ByteBuffer.allocateDirect(128);
        nioBuffer.putLong(1);
        nioBuffer.putLong(2);
        nioBuffer.position(nioBuffer.limit());
        nioBuffer.flip();
        return nioBuffer;
    }

    enum CumulationTestScenario {
        BASIC_NIO_BUFFER(new Supplier() {
            @Override
            public ByteBuf get() {
                return Unpooled.wrappedBuffer(createNioBuffer());
            }
        }, NO_WRAPPER),
        READ_ONLY_AND_DUPLICATE_NIO_BUFFER(new Supplier() {
            @Override
            public ByteBuf get() {
                return Unpooled.wrappedBuffer(createNioBuffer().asReadOnlyBuffer());
            }
        }, DUPLICATE_WRAPPER),
        BASIC_DIRECT_BUFFER(BYTEBUF_SUPPLIER, NO_WRAPPER),
        DUPLICATE_DIRECT_BUFFER(BYTEBUF_SUPPLIER, DUPLICATE_WRAPPER),
        SLICED_DIRECT_BUFFER(BYTEBUF_SUPPLIER, SLICE_WRAPPER);

        final Supplier<ByteBuf> bufferSupplier;
        final Function<ByteBuf, ByteBuf> bufferWrapper;

        CumulationTestScenario(Supplier<ByteBuf> bufferSupplier, Function<ByteBuf, ByteBuf> bufferWrapper) {
            this.bufferSupplier = bufferSupplier;
            this.bufferWrapper = bufferWrapper;
        }
    }

    @ParameterizedTest
    @EnumSource(CumulationTestScenario.class)
    public void testCumulation(CumulationTestScenario testScenario) {
        EmbeddedChannel channel = new EmbeddedChannel();
        SslHandlerCoalescingBufferQueue queue = new SslHandlerCoalescingBufferQueue(channel, 16, false) {
            @Override
            protected int wrapDataSize() {
                return 128;
            }
        };

        ByteBuf original = testScenario.bufferSupplier.get();
        original.writerIndex(8);
        ByteBuf first = testScenario.bufferWrapper.apply(original);
        first.retain();
        queue.add(first);
        ByteBuf second = Unpooled.copyLong(3);
        queue.add(second);

        ChannelPromise promise = channel.newPromise();
        assertFalse(queue.isEmpty());
        ByteBuf buffer = queue.remove(UnpooledByteBufAllocator.DEFAULT, 128, promise);
        try {
            assertEquals(16, buffer.readableBytes());
            assertEquals(1, buffer.readLong());
            assertEquals(3, buffer.readLong());
        } finally {
            buffer.release();
        }
        assertTrue(queue.isEmpty());
        assertEquals(8, original.writerIndex());
        original.writerIndex(original.capacity());
        assertEquals(2, original.getLong(8));
        first.release();
        assertEquals(0, first.refCnt());
        assertEquals(0, second.refCnt());
    }
}
