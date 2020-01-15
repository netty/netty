/*
 * Copyright 2018 The Netty Project
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
package io.netty.microbench.search;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.microbench.util.AbstractMicrobenchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.util.Arrays;

@Warmup(iterations = 5)
@Measurement(iterations = 5)
@Fork(1)
public class AbstractSearchMicrobenchmark extends AbstractMicrobenchmark {

    public enum ByteBufType {
        HEAP {
            @Override
            ByteBuf newBuffer(byte[] bytes) {
                return Unpooled.wrappedBuffer(bytes, 0, bytes.length);
            }
        },
        COMPOSITE {
            @Override
            ByteBuf newBuffer(byte[] bytes) {
                CompositeByteBuf buffer = Unpooled.compositeBuffer();
                int length = bytes.length;
                int offset = 0;
                int capacity = length / 8; // 8 buffers per composite

                while (length > 0) {
                    buffer.addComponent(true, Unpooled.wrappedBuffer(bytes, offset, Math.min(length, capacity)));
                    length -= capacity;
                    offset += capacity;
                }
                return buffer;
            }
        };
        abstract ByteBuf newBuffer(byte[] bytes);
    }

    @Param
    public ByteBufType bufferType;

    public ByteBuf haystack;
    public byte[] http1Bytes;
    public byte[] worstCaseBytes;

    protected void setup() {
        byte[] haystackBytes = new byte[256];
        Arrays.fill(haystackBytes, (byte) 'a');
        haystack = bufferType.newBuffer(haystackBytes);

        http1Bytes = new byte[] {'H', 'T', 'T', 'P', '/', '1', '.'};

        worstCaseBytes = new byte[64];
        Arrays.fill(worstCaseBytes, (byte) 'a');
        worstCaseBytes[worstCaseBytes.length - 1] = 'b';
    }

    @TearDown
    public void teardown() {
        haystack.release();
    }

}
