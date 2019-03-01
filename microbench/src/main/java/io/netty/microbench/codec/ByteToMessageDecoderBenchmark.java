/*
 * Copyright 2019 The Netty Project
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
package io.netty.microbench.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.microbench.util.AbstractMicrobenchmark;
import io.netty.util.internal.PlatformDependent;
import org.openjdk.jmh.annotations.*;

import java.nio.ByteBuffer;
import java.util.List;

/**
 * This benchmark is based on HttpRequestDecoderTest class.
 */
@State(Scope.Benchmark)
@Warmup(iterations = 10)
@Measurement(iterations = 20)
public class ByteToMessageDecoderBenchmark extends AbstractMicrobenchmark {

    public enum SelectCumulator {
        MERGE, COMPOSITE, MESSAGE_AWARE_MERGE
    }

    private static class MessageDecoder extends ByteToMessageDecoder {
        final int messageSize;
        final int skipSize;
        MessageDecoder(int messageSize, int skipSize, SelectCumulator cumulator) {
            this.messageSize = messageSize;
            this.skipSize = skipSize;
            switch (cumulator) {
                case MERGE:
                    setCumulator(MERGE_CUMULATOR);
                    break;
                case COMPOSITE:
                    setCumulator(COMPOSITE_CUMULATOR);
                    break;
                case MESSAGE_AWARE_MERGE:
                    setCumulator(new MessageAwareCumulator(messageSize));
                    break;
                default:
                    throw new IllegalStateException();
            }
        }
        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
            while (in.readableBytes() >= messageSize) {
                for (int skip = messageSize; skip > 0 ; skip -= skipSize) {
                    in.skipBytes(skipSize);
                }
            }
        }
    }

    private static final class MessageAwareCumulator implements ByteToMessageDecoder.Cumulator {
        final int messageSize;
        MessageAwareCumulator(int messageSize) {
            this.messageSize = messageSize;
        }

        @Override
        public ByteBuf cumulate(ByteBufAllocator alloc, ByteBuf cumulation, ByteBuf in) {
            if (!cumulation.isReadable() && in.capacity() == messageSize) {
                cumulation.release();
                return in;
            }
            if (cumulation.capacity() < messageSize) {
                ByteBuf newCumulation = alloc.directBuffer(messageSize);
                newCumulation.writeBytes(cumulation);
                cumulation.release();
                cumulation = newCumulation;
            }

            cumulation.writeBytes(in, 0, Math.min(in.readableBytes(), messageSize - cumulation.readableBytes()));
            if (!in.isReadable()) {
                in.release();
            }
            return cumulation;
        }
    }

    @State(Scope.Thread)
    public static class ThreadState {
        @Param({ "MERGE", "COMPOSITE", "MESSAGE_AWARE_MERGE" })
        public SelectCumulator cumulator;

        @Param({ "1" })
        public int firstInputSize;

        @Param({ "1024", "16384", "65536" })
        public int totalInputSize;

        @Param({ "16", "128", "512" })
        public int messageSize;

        @Param({ "true", "false" })
        public boolean firstBufferHasCapacityForOneMessage;

        @Param({ "true", "false" })
        public boolean decodeByByte;

        MessageDecoder decoder;
        EmbeddedChannel channel;
        // we use a ByteBuffer so we can wrap it for each test, else we are handicapping COMPOSITE_CUMULATOR
        ByteBuffer firstBuffer;
        ByteBuf firstInput;
        ByteBuf secondInput;

        @Setup(Level.Trial)
        public void setup() {
            decoder = new MessageDecoder(messageSize, decodeByByte ? 1 : messageSize, cumulator);
            channel = new EmbeddedChannel(decoder);
            firstBuffer = ByteBuffer.allocateDirect(firstBufferHasCapacityForOneMessage ? messageSize : firstInputSize);
            secondInput = Unpooled.directBuffer(totalInputSize - firstInputSize);
            secondInput.retain();
        }

        @Setup(Level.Invocation)
        public void reset() {
            firstInput = Unpooled.wrappedBuffer(firstBuffer);
            firstInput.readerIndex(0);
            firstInput.writerIndex(firstInputSize);
            secondInput.retain();
            secondInput.readerIndex(0);
            secondInput.writerIndex(totalInputSize - firstInputSize);
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            PlatformDependent.freeDirectBuffer(firstBuffer);
            secondInput.release();
        }
    }

    @Benchmark
    public void testCumulationAndDecode(ThreadState state) {
        state.channel.writeInbound(state.firstInput);
        state.channel.writeInbound(state.secondInput);
    }

}
