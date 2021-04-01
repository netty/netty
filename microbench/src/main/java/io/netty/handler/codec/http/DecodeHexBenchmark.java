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
package io.netty.handler.codec.http;

import io.netty.microbench.util.AbstractMicrobenchmark;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.StringUtil;
import org.jctools.util.Pow2;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.CompilerControl.Mode;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class DecodeHexBenchmark extends AbstractMicrobenchmark {

    @Param({
            //with HEX chars
            "135aBa9BBCEA030b947d79fCcaf48Bde",
            //with HEX chars + 'g'
            "4DDeA5gDD1C6fE567E1b6gf0C40FEcDg",
    })
    private String hex;
    // Needs to specify a high number of inputs to allow the current strategy
    // on nextHexDigits to produce enough branch-misses
    @Param({ "2048" })
    private int inputs;
    private char[][] hexDigits;
    private static final long SEED = 1578675524L;
    private long next;

    @Setup
    public void init() {
        final char[] hexCh = hex.toCharArray();
        next = 0;
        inputs = Pow2.roundToPowerOfTwo(inputs);
        hexDigits = new char[inputs][];
        hexDigits[0] = hexCh;
        if (inputs > 1) {
            final Random rnd = new Random(SEED);
            for (int i = 1; i < inputs; i++) {
                hexDigits[i] = shuffle(Arrays.copyOf(hexCh, hexCh.length), rnd);
            }
        }
    }

    // https://en.wikipedia.org/wiki/Fisher%E2%80%93Yates_shuffle
    private static char[] shuffle(char[] chars, Random rnd) {
        int index;
        char tmp;
        for (int i = chars.length - 1; i > 0; i--) {
            index = rnd.nextInt(i + 1);
            tmp = chars[index];
            chars[index] = chars[i];
            chars[i] = tmp;
        }
        return chars;
    }

    private int nextHexDigits() {
        final int idx = (int) (next & (inputs - 1));
        next++;
        return idx;
    }

    @Benchmark
    @CompilerControl(Mode.DONT_INLINE)
    public long hexDigits() {
        long v = 0;
        final char[] hexDigits = this.hexDigits[nextHexDigits()];
        for (int i = 0, size = hexDigits.length; i < size; i++) {
            v += StringUtil.decodeHexNibble(hexDigits[i]);
        }
        return v;
    }

    @Benchmark
    @CompilerControl(Mode.DONT_INLINE)
    public long hexDigitsWithChecks() {
        long v = 0;
        final char[] hexDigits = this.hexDigits[nextHexDigits()];
        for (int i = 0, size = hexDigits.length; i < size; i++) {
            v += decodeHexNibbleWithCheck(hexDigits[i]);
        }
        return v;
    }

    @Benchmark
    @CompilerControl(Mode.DONT_INLINE)
    public long hexDigitsOriginal() {
        long v = 0;
        final char[] hexDigits = this.hexDigits[nextHexDigits()];
        for (int i = 0, size = hexDigits.length; i < size; i++) {
            v += decodeHexNibble(hexDigits[i]);
        }
        return v;
    }

    private static int decodeHexNibble(final char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'A' && c <= 'F') {
            return c - ('A' - 0xA);
        }
        if (c >= 'a' && c <= 'f') {
            return c - ('a' - 0xA);
        }
        return -1;
    }

    private static final byte[] HEX2B;

    static {
        HEX2B = new byte['f' + 1];
        Arrays.fill(HEX2B, (byte) -1);
        HEX2B['0'] = (byte) 0;
        HEX2B['1'] = (byte) 1;
        HEX2B['2'] = (byte) 2;
        HEX2B['3'] = (byte) 3;
        HEX2B['4'] = (byte) 4;
        HEX2B['5'] = (byte) 5;
        HEX2B['6'] = (byte) 6;
        HEX2B['7'] = (byte) 7;
        HEX2B['8'] = (byte) 8;
        HEX2B['9'] = (byte) 9;
        HEX2B['A'] = (byte) 10;
        HEX2B['B'] = (byte) 11;
        HEX2B['C'] = (byte) 12;
        HEX2B['D'] = (byte) 13;
        HEX2B['E'] = (byte) 14;
        HEX2B['F'] = (byte) 15;
        HEX2B['a'] = (byte) 10;
        HEX2B['b'] = (byte) 11;
        HEX2B['c'] = (byte) 12;
        HEX2B['d'] = (byte) 13;
        HEX2B['e'] = (byte) 14;
        HEX2B['f'] = (byte) 15;
    }

    private static int decodeHexNibbleWithCheck(final char c) {
        final int index = c;
        if (index >= HEX2B.length) {
            return -1;
        }
        if (PlatformDependent.hasUnsafe()) {
            return PlatformDependent.getByte(HEX2B, index);
        }
        return HEX2B[index];
    }

}
