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
package io.netty.handler.codec.protobuf;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.CorruptedFrameException;
import io.netty.microbench.util.AbstractMicrobenchmark;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.Random;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(org.openjdk.jmh.annotations.Mode.AverageTime)
@Fork(2)
@Warmup(iterations = 10, time = 400, timeUnit = java.util.concurrent.TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 400, timeUnit = java.util.concurrent.TimeUnit.MILLISECONDS)
public class VarintDecodingBenchmark extends AbstractMicrobenchmark {

    private static final int SEED = 0;

    // Ryzen 7950X is exceptionally good to predict branches, so we need to use A LOT of inputs!
    @Param({ "1", "128", "128000" })
    int inputs;

    public enum InputDistribution {
        SMALL,
        LARGE,
        MEDIUM,
        ALL
    }

    @Param
    InputDistribution inputDistribution;
    ByteBuf[] data;
    int index;

    @Setup
    public void init() {
        ByteBuf[] dataSet;
        switch (inputDistribution) {
        case SMALL:
            dataSet = new ByteBuf[] {
                    generateData(1, 1),
                    generateData(2, 2),
                    generateData(3, 3)
            };
            break;
        case LARGE:
            dataSet = new ByteBuf[] {
                    generateData(5, 5)
            };
            if (inputs > 1) {
                System.exit(1);
            }
            break;
        case MEDIUM:
            dataSet = new ByteBuf[] {
                    generateData(1, 5),
                    generateData(2, 5),
                    generateData(3, 5),
                    generateData(4, 5)
            };
            break;
        case ALL:
            dataSet = new ByteBuf[] {
                    generateData(1, 1),
                    generateData(2, 2),
                    generateData(3, 3),
                    generateData(1, 5),
                    generateData(2, 5),
                    generateData(3, 5),
                    generateData(4, 5),
                    generateData(5, 5)
            };
            break;
        default:
            throw new RuntimeException("Unknown distribution");
        }
        data = new ByteBuf[inputs];
        Random rnd = new Random(SEED);
        for (int i = 0; i < inputs; i++) {
            data[i] = dataSet[rnd.nextInt(dataSet.length)];
        }
        index = 0;
    }

    public static ByteBuf generateData(int varintLength, int capacity) {
        byte[] bytes = new byte[capacity];
        for (int i = 0; i < (varintLength - 1); i++) {
            bytes[i] = (byte) 150;
        }
        // delimiter
        bytes[varintLength - 1] = (byte) 1;
        return Unpooled.wrappedBuffer(bytes);
    }

    public ByteBuf nextData() {
        index++;
        if (index == data.length) {
            index = 0;
        }
        return data[index].resetReaderIndex();
    }

    @Benchmark
    public int oldReadRawVarint32() {
        return oldReadRawVarint32(nextData());
    }

    @Benchmark
    public int readRawVarint32() {
        return ProtobufVarint32FrameDecoder.readRawVarint32(nextData());
    }

    /**
     * Reads variable length 32bit int from buffer
     *
     * @return decoded int if buffers readerIndex has been forwarded else nonsense value
     */
    private static int oldReadRawVarint32(ByteBuf buffer) {
        if (!buffer.isReadable()) {
            return 0;
        }
        buffer.markReaderIndex();

        byte tmp = buffer.readByte();
        if (tmp >= 0) {
            return tmp;
        } else {
            int result = tmp & 127;
            if (!buffer.isReadable()) {
                buffer.resetReaderIndex();
                return 0;
            }
            if ((tmp = buffer.readByte()) >= 0) {
                result |= tmp << 7;
            } else {
                result |= (tmp & 127) << 7;
                if (!buffer.isReadable()) {
                    buffer.resetReaderIndex();
                    return 0;
                }
                if ((tmp = buffer.readByte()) >= 0) {
                    result |= tmp << 14;
                } else {
                    result |= (tmp & 127) << 14;
                    if (!buffer.isReadable()) {
                        buffer.resetReaderIndex();
                        return 0;
                    }
                    if ((tmp = buffer.readByte()) >= 0) {
                        result |= tmp << 21;
                    } else {
                        result |= (tmp & 127) << 21;
                        if (!buffer.isReadable()) {
                            buffer.resetReaderIndex();
                            return 0;
                        }
                        result |= (tmp = buffer.readByte()) << 28;
                        if (tmp < 0) {
                            throw new CorruptedFrameException("malformed varint.");
                        }
                    }
                }
            }
            return result;
        }
    }

}
