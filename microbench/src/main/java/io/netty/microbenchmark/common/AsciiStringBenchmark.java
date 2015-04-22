/*
 * Copyright 2015 The Netty Project
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
package io.netty.microbenchmark.common;

import static io.netty.util.internal.StringUtil.asciiToLowerCase;
import io.netty.microbench.util.AbstractMicrobenchmark;
import io.netty.util.AsciiString;
import io.netty.util.ByteProcessor;
import io.netty.util.ByteString;

import java.util.Random;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

@Threads(1)
@Fork(1)
@Warmup(iterations = 5)
@State(Scope.Benchmark)
public class AsciiStringBenchmark extends AbstractMicrobenchmark {

    public enum InputType {
        UPPER_CASE, LOWER_CASE, MIXED_CASE_SAME, MIXED_CASE_ALT, RANDOM, NOT_EQUAL;
    }

    @Param({ "5", "10", "20", "50", "100", "1000" })
    private int size;

    @Param
    public InputType inputType;

    private AsciiString a1;
    private AsciiString a2;
    private OldAsciiString a1Old;
    private OldAsciiString a2Old;
    private String string1;
    private String string2;

    @Setup(Level.Trial)
    public void setup() {
        final int max = 255;
        final int min = 0;
        final int upperA = 'A';
        final int upperZ = 'Z';
        final int upperToLower = (int) 'a' - upperA;
        Random r = new Random();
        byte[] bytes = new byte[size];
        char[] chars = new char[size];

        switch (inputType) {
        case UPPER_CASE:
            for (int i = 0; i < size; i++) {
                bytes[i] = (byte) (r.nextInt((upperZ - upperA) + 1) + upperA);
            }
            break;
        case LOWER_CASE:
            for (int i = 0; i < size; i++) {
                bytes[i] = (byte) ((r.nextInt((upperZ - upperA) + 1) + upperA) + upperToLower);
            }
            break;
        case MIXED_CASE_SAME:
            for (int i = 0; i < size; i++) {
                bytes[i] = (byte) (r.nextInt((upperZ - upperA) + 1) + upperA);
                if ((i & 1) == 0) {
                    bytes[i] += upperToLower;
                }
            }
            break;
        case NOT_EQUAL:
        case RANDOM:
            for (int i = 0; i < size; i++) {
                bytes[i] = (byte) (r.nextInt((max - min) + 1) + min);
            }
            break;
        case MIXED_CASE_ALT:
            for (int i = 0; i < size; i++) {
                bytes[i] = (byte) (r.nextInt((upperZ - upperA) + 1) + upperA);
            }
            break;
        default:
            throw new Error();
        }

        System.err.println();
        for (int i = 0; i < bytes.length; ++i) {
            chars[i] = (char) (bytes[i] & 0xFF);
        }
        a1 = new AsciiString(bytes, true);
        a1Old = new OldAsciiString(bytes, true);
        string1 = new String(chars);
        if (inputType == InputType.MIXED_CASE_ALT || inputType == InputType.NOT_EQUAL) {
            for (int i = 0; i < size; i++) {
                if (upperA <= bytes[i] && bytes[i] <= upperZ) {
                    bytes[i] += upperToLower;
                } else {
                    bytes[i] -= upperToLower;
                }
            }
            for (int i = 0; i < bytes.length; ++i) {
                chars[i] = (char) (bytes[i] & 0xFF);
            }
        }
        a2 = new AsciiString(bytes, false);
        a2Old = new OldAsciiString(bytes, false);
        string2 = new String(chars);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public int unsafeHashCodeCaseInsensitive() {
        return a1.hashCodeCaseInsensitive();
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public int oldHashCodeCaseInsensitive() throws Exception {
        ByteProcessor processor = new ByteProcessor() {
            private int hash;
            @Override
            public boolean process(byte value) throws Exception {
                hash = hash * 31 ^ asciiToLowerCase(value) & 31;
                return true;
            }

            @Override
            public int hashCode() {
                return hash;
            }
        };
        a1.forEachByte(processor);
        return processor.hashCode();
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public boolean unsafeContentEqualsAsciiString() {
        return a1.contentEquals(a2);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public boolean oldContentEqualsAsciiString() {
        return a1Old.contentEquals(a2Old);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public boolean unsafeContentEqualsString() {
        return a1.contentEquals(string2);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public boolean oldContentEqualsString() {
        return a1Old.contentEquals(string2);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public boolean unsafeEqualsCaseInsensitiveString() {
        return a1.contentEqualsIgnoreCase(string2);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public boolean oldEqualsCaseInsensitiveString() {
        return a1Old.equalsIgnoreCase(string2);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public boolean unsafeEqualsCaseInsensitiveAsciiString() {
        return a1.contentEqualsIgnoreCase(a2);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public boolean oldEqualsCaseInsensitiveAsciiString() {
        return a1Old.equalsIgnoreCase(a2Old);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public boolean unsafeEqualsCaseInsensitive2Strings() {
        return AsciiString.contentEqualsIgnoreCase(string1, string2);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public boolean oldEqualsCaseInsensitive2Strings() {
        return OldAsciiString.equalsIgnoreCase(string1, string2);
    }

    private static final class OldAsciiString extends ByteString implements CharSequence {
        public OldAsciiString(byte[] bytes, boolean cpy) {
            super(bytes, cpy);
        }

        @Override
        public OldAsciiString subSequence(int start, int end) {
           return null;
        }

        @Override
        public char charAt(int index) {
            return (char) (byteAt(index) & 0xFF);
        }

        public boolean contentEquals(CharSequence cs) {
            if (cs == null) {
                throw new NullPointerException();
            }

            int length1 = length();
            int length2 = cs.length();
            if (length1 != length2) {
                return false;
            } else if (length1 == 0) {
                return true; // since both are empty strings
            }

            return regionMatches(0, cs, 0, length2);
        }

        public boolean regionMatches(int thisStart, CharSequence string, int start, int length) {
            if (string == null) {
                throw new NullPointerException("string");
            }

            if (start < 0 || string.length() - start < length) {
                return false;
            }

            final int thisLen = length();
            if (thisStart < 0 || thisLen - thisStart < length) {
                return false;
            }

            if (length <= 0) {
                return true;
            }

            final int thatEnd = start + length;
            for (int i = start, j = thisStart + arrayOffset(); i < thatEnd; i++, j++) {
                if ((char) (value[j] & 0xFF) != string.charAt(i)) {
                    return false;
                }
            }
            return true;
        }

        public static boolean equalsIgnoreCase(CharSequence a, CharSequence b) {
            if (a == b) {
                return true;
            }

            if (a instanceof OldAsciiString) {
                OldAsciiString aa = (OldAsciiString) a;
                return aa.equalsIgnoreCase(b);
            }

            if (b instanceof OldAsciiString) {
                OldAsciiString ab = (OldAsciiString) b;
                return ab.equalsIgnoreCase(a);
            }

            if (a == null || b == null) {
                return false;
            }

            return a.toString().equalsIgnoreCase(b.toString());
        }

        public boolean equalsIgnoreCase(CharSequence string) {
            if (string == this) {
                return true;
            }

            if (string == null) {
                return false;
            }

            final int thisLen = length();
            final int thatLen = string.length();
            if (thisLen != thatLen) {
                return false;
            }

            for (int i = 0, j = arrayOffset(); i < thatLen; i++, j++) {
                char c1 = (char) (value[j] & 0xFF);
                char c2 = string.charAt(i);
                if (c1 != c2 && asciiToLowerCase(c1) != asciiToLowerCase(c2)) {
                    return false;
                }
            }
            return true;
        }
    }
}
