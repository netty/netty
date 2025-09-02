/*
* Copyright 2019 The Netty Project
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
package io.netty.buffer;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

import io.netty.util.internal.CleanableDirectBuffer;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import io.netty.microbench.util.AbstractMicrobenchmark;
import io.netty.util.internal.PlatformDependent;
import org.openjdk.jmh.infra.Blackhole;

@Warmup(iterations = 10, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(2)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class ByteBufAccessBenchmark extends AbstractMicrobenchmark {

    static final class NioFacade extends WrappedByteBuf {
        private final ByteBuffer byteBuffer;
        private final CleanableDirectBuffer cleanable;

        NioFacade(CleanableDirectBuffer buffer) {
            super(Unpooled.EMPTY_BUFFER);
            byteBuffer = buffer.buffer();
            cleanable = buffer;
        }
        @Override
        public ByteBuf setLong(int index, long value) {
            byteBuffer.putLong(index, value);
            return this;
        }
        @Override
        public long getLong(int index) {
            return byteBuffer.getLong(index);
        }
        @Override
        public byte readByte() {
            return byteBuffer.get();
        }
        @Override
        public ByteBuf touch() {
            // hack since WrappedByteBuf.readerIndex(int) is final
            byteBuffer.position(0);
            return this;
        }
        @Override
        public boolean release() {
            cleanable.clean();
            return true;
        }
    }

    public enum ByteBufType {
        UNSAFE {
            @Override
            ByteBuf newBuffer() {
                return new UnpooledUnsafeDirectByteBuf(
                        UnpooledByteBufAllocator.DEFAULT, 64, 64).setIndex(0, 64);
            }
        },
        DIRECT {
            @Override
            ByteBuf newBuffer() {
                return new UnpooledDirectByteBuf(
                        UnpooledByteBufAllocator.DEFAULT, 64, 64).setIndex(0, 64);
            }
        },
        UNSAFE_SLICE {
            @Override
            ByteBuf newBuffer() {
                return UNSAFE.newBuffer().slice(16, 48);
            }
        },
        UNSAFE_RETAINED_SLICE {
            @Override
            ByteBuf newBuffer() {
                ByteBuf pooledBuffer = PooledByteBufAllocator.DEFAULT.directBuffer(64, 64)
                        .setIndex(0, 64);
                if (!(pooledBuffer instanceof PooledUnsafeDirectByteBuf)) {
                    throw new IllegalStateException("Expected PooledUnsafeDirectByteBuf");
                }
                try {
                    return pooledBuffer.retainedSlice(16, 48);
                } finally {
                    pooledBuffer.release();
                }
            }
        },
        HEAP {
            @Override
            ByteBuf newBuffer() {
                if (PlatformDependent.hasUnsafe()) {
                    return new UnpooledUnsafeHeapByteBuf(
                            UnpooledByteBufAllocator.DEFAULT, 64, 64).setIndex(0, 64);
                } else {
                    return new UnpooledHeapByteBuf(
                            UnpooledByteBufAllocator.DEFAULT, 64, 64).setIndex(0, 64);
                }
            }
        },
        COMPOSITE {
            @Override
            ByteBuf newBuffer() {
                return Unpooled.wrappedBuffer(UNSAFE.newBuffer(), HEAP.newBuffer());
            }
        },
        NIO {
            @Override
            ByteBuf newBuffer() {
                return new NioFacade(PlatformDependent.allocateDirect(64));
            }
        };
        abstract ByteBuf newBuffer();
    }

    @Param
    public ByteBufType bufferType;

    @Param({
            "true",
            "false",
    })
    public String checkAccessible;

    @Param({
            "true",
            "false",
    })
    public String checkBounds;

    @Param({
            "8",
    })
    public int batchSize; // applies only to readBatch benchmark

    @Setup
    public void setup() {
        System.setProperty("io.netty.buffer.checkAccessible", checkAccessible);
        System.setProperty("io.netty.buffer.checkBounds", checkBounds);
        buffer = bufferType.newBuffer();
    }

    private ByteBuf buffer;
    private byte byteToWrite;
    private int intToWrite;
    private long longToWrite;
    private short shortToWrite;

    @TearDown
    public void tearDown() {
        buffer.release();
        System.clearProperty("io.netty.buffer.checkAccessible");
        System.clearProperty("io.netty.buffer.checkBounds");
    }

    @Benchmark
    public long setGetLong() {
        return buffer.setLong(0, 1).getLong(0);
    }

    @Benchmark
    public ByteBuf setLong() {
        return buffer.setLong(0, 1);
    }

    @Benchmark
    public int readBatch() {
        buffer.readerIndex(0);
        int result = 0;
        // WARNING!
        // Please do not replace this sum loop with a BlackHole::consume loop:
        // BlackHole::consume could prevent the JVM to perform certain optimizations
        // forcing ByteBuf::readByte to be executed in order.
        // The purpose of the benchmark is to mimic accesses on ByteBuf
        // as in a real (single-threaded) case ie without (compiler) memory barriers that would
        // disable certain optimizations or would make bounds checks (if enabled)
        // to happen on each access.
        for (int i = 0, size = batchSize; i < size; i++) {
            result += buffer.readByte();
        }
        return result;
    }

    @Benchmark
    public void getByteBatch(Blackhole bh) {
        ByteBuf buffer = this.buffer;
        for (int i = 0, size = batchSize; i < size; i++) {
            bh.consume(buffer.getByte(i));
        }
    }
    @Benchmark
    public void setByteBatch(Blackhole bh) {
        ByteBuf buffer = this.buffer;
        byte byteToWrite = this.byteToWrite;
        buffer.resetWriterIndex();
        for (int i = 0, size = batchSize; i < size; i++) {
            bh.consume(buffer.setByte(i, byteToWrite));
        }
    }

    @Benchmark
    public void readByteBatch(Blackhole bh) {
        ByteBuf buffer = this.buffer;
        buffer.readerIndex(0);
        for (int i = 0, size = batchSize; i < size; i++) {
            bh.consume(buffer.readByte());
        }
    }

    @Benchmark
    public void setBytes(Blackhole bh) {
        ByteBuf buffer = this.buffer;
        byte byteToWrite = this.byteToWrite;
        int intToWrite = this.intToWrite;
        long longToWrite = this.longToWrite;
        short shortToWrite = this.shortToWrite;
        buffer.resetWriterIndex();
        int index = buffer.writerIndex();
        bh.consume(buffer.setByte(index, byteToWrite));
        index += 1;
        bh.consume(buffer.setShortLE(index, shortToWrite));
        index += 2;
        bh.consume(buffer.setIntLE(index, intToWrite));
        index += 4;
        bh.consume(buffer.setLongLE(index, longToWrite));
    }

    @Benchmark
    public void getBytes(Blackhole bh) {
        ByteBuf buffer = this.buffer;
        int readerIndex = buffer.readerIndex();
        bh.consume(buffer.getByte(readerIndex));
        readerIndex += 1;
        bh.consume(buffer.getShortLE(readerIndex));
        readerIndex += 2;
        bh.consume(buffer.getIntLE(readerIndex));
        readerIndex += 4;
        bh.consume(buffer.getLongLE(readerIndex));
    }

    @Benchmark
    public void setBytesConstantOffset(Blackhole bh) {
        ByteBuf buffer = this.buffer;
        buffer.resetWriterIndex();
        byte byteToWrite = this.byteToWrite;
        int intToWrite = this.intToWrite;
        long longToWrite = this.longToWrite;
        short shortToWrite = this.shortToWrite;
        bh.consume(buffer.setByte(0, byteToWrite));
        bh.consume(buffer.setShortLE(1, shortToWrite));
        bh.consume(buffer.setIntLE(3, intToWrite));
        bh.consume(buffer.setLongLE(7, longToWrite));
    }

    @Benchmark
    public void getBytesConstantOffset(Blackhole bh) {
        ByteBuf buffer = this.buffer;
        bh.consume(buffer.getByte(0));
        bh.consume(buffer.getShortLE(1));
        bh.consume(buffer.getIntLE(3));
        bh.consume(buffer.getLongLE(7));
    }

    @Benchmark
    public void readBytes(Blackhole bh) {
        buffer.readerIndex(0);
        ByteBuf buffer = this.buffer;
        bh.consume(buffer.readByte());
        bh.consume(buffer.readShortLE());
        bh.consume(buffer.readIntLE());
        bh.consume(buffer.readLongLE());
    }
}
