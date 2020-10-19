/*
 * Copyright 2020 The Netty Project
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
import io.netty.util.AsciiString;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.CompilerControl.Mode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

@Fork(value = 2, jvmArgsAppend = "-XX:MaxInlineLevel=9")
public class Utf8EncodingBenchmark extends AbstractMicrobenchmark {
    private static class AnotherCharSequence implements CharSequence {
        private final char[] chars;

        AnotherCharSequence(String chars) {
            this.chars = new char[chars.length()];
            chars.getChars(0, chars.length(), this.chars, 0);
        }

        @Override
        public int length() {
            return chars.length;
        }

        @Override
        public char charAt(int i) {
            return chars[i];
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String toString() {
            throw new UnsupportedOperationException();
        }
    }

    // experiment test input
    private String[] strings;
    private StringBuilder[] stringBuilders;
    private AnotherCharSequence[] anotherCharSequences;
    private AsciiString[] asciiStrings;
    @Param({ "false", "true" })
    private boolean direct;
    private ByteBuf buffer;
    @Param({ "false", "true" })
    private boolean noUnsafe;
    private int dataSetLength;

    @Setup
    public void init() {
        System.setProperty("io.netty.noUnsafe", Boolean.valueOf(noUnsafe).toString());
        InputStream testTextStream = null;
        InputStreamReader inStreamReader = null;
        BufferedReader buffReader = null;
        int maxExpectedSize = 0;
        List<String> strings = new ArrayList<String>();
        List<StringBuilder> stringBuilders = new ArrayList<StringBuilder>();
        List<AnotherCharSequence> anotherCharSequenceList = new ArrayList<AnotherCharSequence>();
        List<AsciiString> asciiStrings = new ArrayList<AsciiString>();
        try {
            testTextStream = getClass().getResourceAsStream("/Utf8Samples.txt");
            inStreamReader = new InputStreamReader(testTextStream, "UTF-8");
            buffReader = new BufferedReader(inStreamReader);
            String line;
            while ((line = buffReader.readLine()) != null) {
                strings.add(line);
                stringBuilders.add(new StringBuilder(line));
                anotherCharSequenceList.add(new AnotherCharSequence(line));
                asciiStrings.add(new AsciiString(line));
                maxExpectedSize = Math.max(maxExpectedSize, ByteBufUtil.utf8MaxBytes(line.length()));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            closeStream(testTextStream);
            closeReader(inStreamReader);
            closeReader(buffReader);
        }
        buffer = direct? Unpooled.directBuffer(maxExpectedSize, maxExpectedSize) :
                Unpooled.buffer(maxExpectedSize, maxExpectedSize);
        buffer.setByte(maxExpectedSize - 1, 0);
        this.strings = strings.toArray(new String[strings.size()]);
        this.stringBuilders = stringBuilders.toArray(new StringBuilder[stringBuilders.size()]);
        this.anotherCharSequences =
                anotherCharSequenceList.toArray(new AnotherCharSequence[anotherCharSequenceList.size()]);
        this.asciiStrings = asciiStrings.toArray(new AsciiString[asciiStrings.size()]);
        this.dataSetLength = this.strings.length;
    }

    private static void closeStream(InputStream inStream) {
        if (inStream != null) {
            try {
                inStream.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static void closeReader(Reader buffReader) {
        if (buffReader != null) {
            try {
                buffReader.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Benchmark
    @CompilerControl(Mode.DONT_INLINE)
    public int nestedByteBufUtilWriteUtf8String() {
        int countBytes = 0;
        for (String string : strings) {
            countBytes += nestedByteBufUtilWriteUtf8String1(string);
        }
        return countBytes;
    }

    private int nestedByteBufUtilWriteUtf8String1(String string) {
        return nestedByteBufUtilWriteUtf8String2(string);
    }

    private int nestedByteBufUtilWriteUtf8String2(String string) {
        return nestedByteBufUtilWriteUtf8String3(string);
    }

    private int nestedByteBufUtilWriteUtf8String3(String string) {
        return nestedByteBufUtilWriteUtf8String4(string);
    }

    private int nestedByteBufUtilWriteUtf8String4(String string) {
        return nestedByteBufUtilWriteUtf8String5(string);
    }

    private int nestedByteBufUtilWriteUtf8String5(String string) {
        return nestedByteBufUtilWriteUtf8String6(string);
    }

    private int nestedByteBufUtilWriteUtf8String6(String string) {
        // this calls should be inlined but...what happen to the subsequent calls > MaxInlineLevel?
        buffer.resetWriterIndex();
        ByteBufUtil.writeUtf8(buffer, string, 0, string.length());
        return buffer.writerIndex();
    }

    @Benchmark
    @CompilerControl(Mode.DONT_INLINE)
    public int byteBufUtilWriteUtf8String() {
        int countBytes = 0;
        for (String string : strings) {
            buffer.resetWriterIndex();
            ByteBufUtil.writeUtf8(buffer, string, 0, string.length());
            countBytes += buffer.writerIndex();
        }
        return countBytes;
    }

    @Benchmark
    @CompilerControl(Mode.DONT_INLINE)
    public int byteBufUtilWriteUtf8Bimorphic() {
        int countBytes = 0;
        for (int i = 0, size = dataSetLength; i < size; i++) {
            final StringBuilder stringBuilder = stringBuilders[i];
            final String string = strings[i];
            buffer.resetWriterIndex();
            ByteBufUtil.writeUtf8(buffer, stringBuilder, 0, stringBuilder.length());
            countBytes += buffer.writerIndex();
            buffer.resetWriterIndex();
            ByteBufUtil.writeUtf8(buffer, string, 0, string.length());
            countBytes += buffer.writerIndex();
        }
        return countBytes;
    }

    @Benchmark
    @CompilerControl(Mode.DONT_INLINE)
    public int byteBufUtilWriteUtf8Megamorphic() {
        int countBytes = 0;
        for (int i = 0, size = dataSetLength; i < size; i++) {
            final StringBuilder stringBuilder = stringBuilders[i];
            final String string = strings[i];
            final AnotherCharSequence anotherCharSequence = anotherCharSequences[i];
            buffer.resetWriterIndex();
            ByteBufUtil.writeUtf8(buffer, stringBuilder, 0, stringBuilder.length());
            countBytes += buffer.writerIndex();
            buffer.resetWriterIndex();
            ByteBufUtil.writeUtf8(buffer, string, 0, string.length());
            countBytes += buffer.writerIndex();
            buffer.resetWriterIndex();
            ByteBufUtil.writeUtf8(buffer, anotherCharSequence, 0, anotherCharSequence.length());
            countBytes += buffer.writerIndex();
        }
        return countBytes;
    }

    @Benchmark
    @CompilerControl(Mode.DONT_INLINE)
    public int byteBufUtilWriteUtf8CommonCharSequences() {
        int countBytes = 0;
        for (int i = 0, size = dataSetLength; i < size; i++) {
            final StringBuilder stringBuilder = stringBuilders[i];
            final String string = strings[i];
            final AsciiString asciiString = asciiStrings[i];
            buffer.resetWriterIndex();
            ByteBufUtil.writeUtf8(buffer, stringBuilder, 0, stringBuilder.length());
            countBytes += buffer.writerIndex();
            buffer.resetWriterIndex();
            ByteBufUtil.writeUtf8(buffer, string, 0, string.length());
            countBytes += buffer.writerIndex();
            buffer.resetWriterIndex();
            ByteBufUtil.writeUtf8(buffer, asciiString, 0, asciiString.length());
            countBytes += buffer.writerIndex();
        }
        return countBytes;
    }

    @Benchmark
    @CompilerControl(Mode.DONT_INLINE)
    public int byteBufUtilWriteUtf8AsciiString() {
        int countBytes = 0;
        for (int i = 0, size = dataSetLength; i < size; i++) {
            final AsciiString asciiString = asciiStrings[i];
            buffer.resetWriterIndex();
            ByteBufUtil.writeUtf8(buffer, asciiString, 0, asciiString.length());
            countBytes += buffer.writerIndex();
        }
        return countBytes;
    }

    @Benchmark
    @CompilerControl(Mode.DONT_INLINE)
    public int writeGetBytes() throws UnsupportedEncodingException {
        int countBytes = 0;
        for (String string : strings) {
            buffer.resetWriterIndex();
            final byte[] bytes = string.getBytes("UTF-8");
            buffer.writeBytes(bytes);
            countBytes += buffer.writerIndex();
        }
        return countBytes;
    }

}
