/*
* Copyright 2014 The Netty Project
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
package io.netty.microbench.buffer;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.microbench.util.AbstractMicrobenchmark;
import io.netty.util.CharsetUtil;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;


@State(Scope.Benchmark)
@Warmup(iterations = 5)
@Measurement(iterations = 10)
public class
    ByteBufUtilBenchmark extends AbstractMicrobenchmark {

    @Param({ "true", "false" })
    private boolean direct;
    @Param({ "8", "16", "64", "128" })
    private int length;
    private ByteBuf buffer;
    private ByteBuf wrapped;
    private ByteBuf asciiBuffer;
    private ByteBuf utf8Buffer;

    private StringBuilder asciiSequence;
    private String ascii;

    private StringBuilder utf8Sequence;
    private String utf8;

    @Setup
    public void setup() {
        // Use buffer sizes that will also allow to write UTF-8 without grow the buffer
        final int maxBytes = ByteBufUtil.utf8MaxBytes(length);
        buffer = direct? Unpooled.directBuffer(maxBytes) : Unpooled.buffer(maxBytes);
        wrapped = Unpooled.unreleasableBuffer(direct? Unpooled.directBuffer(maxBytes) : Unpooled.buffer(maxBytes));
        asciiSequence = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            asciiSequence.append('a');
        }
        ascii = asciiSequence.toString();

        // Generate some mixed UTF-8 String for benchmark
        utf8Sequence = new StringBuilder(length);
        char[] chars = "Some UTF-8 like äÄ∏ŒŒ".toCharArray();
        for (int i = 0; i < length; i++) {
            utf8Sequence.append(chars[i % chars.length]);
        }
        utf8 = utf8Sequence.toString();
        asciiSequence = utf8Sequence;

        asciiBuffer = Unpooled.copiedBuffer(ascii, CharsetUtil.US_ASCII);
        utf8Buffer = Unpooled.copiedBuffer(utf8, CharsetUtil.UTF_8);
    }

    @TearDown
    public void tearDown() {
        buffer.release();
        wrapped.release();
        asciiBuffer.release();
        utf8Buffer.release();
    }

    @Benchmark
    public void writeAsciiStringViaArray() {
        buffer.resetWriterIndex();
        buffer.writeBytes(ascii.getBytes(CharsetUtil.US_ASCII));
    }

    @Benchmark
    public void writeAsciiStringViaArrayWrapped() {
        wrapped.resetWriterIndex();
        wrapped.writeBytes(ascii.getBytes(CharsetUtil.US_ASCII));
    }

    @Benchmark
    public void writeAsciiString() {
        buffer.resetWriterIndex();
        ByteBufUtil.writeAscii(buffer, ascii);
    }

    @Benchmark
    public void writeAsciiStringWrapped() {
        wrapped.resetWriterIndex();
        ByteBufUtil.writeAscii(wrapped, ascii);
    }

    @Benchmark
    public void writeAsciiViaArray() {
        buffer.resetWriterIndex();
        buffer.writeBytes(asciiSequence.toString().getBytes(CharsetUtil.US_ASCII));
    }

    @Benchmark
    public void writeAsciiViaArrayWrapped() {
        wrapped.resetWriterIndex();
        wrapped.writeBytes(asciiSequence.toString().getBytes(CharsetUtil.US_ASCII));
    }

    @Benchmark
    public void writeAscii() {
        buffer.resetWriterIndex();
        ByteBufUtil.writeAscii(buffer, asciiSequence);
    }

    @Benchmark
    public void writeAsciiWrapped() {
        wrapped.resetWriterIndex();
        ByteBufUtil.writeAscii(wrapped, asciiSequence);
    }

    @Benchmark
    public void writeUtf8StringViaArray() {
        buffer.resetWriterIndex();
        buffer.writeBytes(utf8.getBytes(CharsetUtil.UTF_8));
    }

    @Benchmark
    public void writeUtf8StringViaArrayWrapped() {
        wrapped.resetWriterIndex();
        wrapped.writeBytes(utf8.getBytes(CharsetUtil.UTF_8));
    }

    @Benchmark
    public void writeUtf8String() {
        buffer.resetWriterIndex();
        ByteBufUtil.writeUtf8(buffer, utf8);
    }

    @Benchmark
    public void writeUtf8StringWrapped() {
        wrapped.resetWriterIndex();
        ByteBufUtil.writeUtf8(wrapped, utf8);
    }

    @Benchmark
    public void writeUtf8ViaArray() {
        buffer.resetWriterIndex();
        buffer.writeBytes(utf8Sequence.toString().getBytes(CharsetUtil.UTF_8));
    }

    @Benchmark
    public void writeUtf8ViaArrayWrapped() {
        wrapped.resetWriterIndex();
        wrapped.writeBytes(utf8Sequence.toString().getBytes(CharsetUtil.UTF_8));
    }

    @Benchmark
    public void writeUtf8() {
        buffer.resetWriterIndex();
        ByteBufUtil.writeUtf8(buffer, utf8Sequence);
    }

    @Benchmark
    public void writeUtf8Wrapped() {
        wrapped.resetWriterIndex();
        ByteBufUtil.writeUtf8(wrapped, utf8Sequence);
    }

    @Benchmark
    public String decodeStringAscii() {
        return asciiBuffer.toString(CharsetUtil.US_ASCII);
    }

    @Benchmark
    public String decodeStringUtf8() {
        return utf8Buffer.toString(CharsetUtil.UTF_8);
    }
}
