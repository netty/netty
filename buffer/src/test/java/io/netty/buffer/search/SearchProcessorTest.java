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
package io.netty.buffer.search;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.CharsetUtil;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SearchProcessorTest {

    private enum Algorithm {
        KNUTH_MORRIS_PRATT {
            @Override
            SearchProcessorFactory newFactory(byte[] needle) {
                return AbstractSearchProcessorFactory.newKmpSearchProcessorFactory(needle);
            }
        },
        BITAP {
            @Override
            SearchProcessorFactory newFactory(byte[] needle) {
                return AbstractSearchProcessorFactory.newBitapSearchProcessorFactory(needle);
            }
        },
        AHO_CORASIC {
            @Override
            SearchProcessorFactory newFactory(byte[] needle) {
                return AbstractMultiSearchProcessorFactory.newAhoCorasicSearchProcessorFactory(needle);
            }
        };
        abstract SearchProcessorFactory newFactory(byte[] needle);
    }

    @ParameterizedTest
    @EnumSource(Algorithm.class)
    public void testSearch(Algorithm algorithm) {
        final ByteBuf haystack = Unpooled.copiedBuffer("abc☺", CharsetUtil.UTF_8);

        assertEquals(0, haystack.forEachByte(factory(algorithm, "a").newSearchProcessor()));
        assertEquals(1, haystack.forEachByte(factory(algorithm, "ab").newSearchProcessor()));
        assertEquals(2, haystack.forEachByte(factory(algorithm, "abc").newSearchProcessor()));
        assertEquals(5, haystack.forEachByte(factory(algorithm, "abc☺").newSearchProcessor()));
        assertEquals(-1, haystack.forEachByte(factory(algorithm, "abc☺☺").newSearchProcessor()));
        assertEquals(-1, haystack.forEachByte(factory(algorithm, "abc☺x").newSearchProcessor()));

        assertEquals(1, haystack.forEachByte(factory(algorithm, "b").newSearchProcessor()));
        assertEquals(2, haystack.forEachByte(factory(algorithm, "bc").newSearchProcessor()));
        assertEquals(5, haystack.forEachByte(factory(algorithm, "bc☺").newSearchProcessor()));
        assertEquals(-1, haystack.forEachByte(factory(algorithm, "bc☺☺").newSearchProcessor()));
        assertEquals(-1, haystack.forEachByte(factory(algorithm, "bc☺x").newSearchProcessor()));

        assertEquals(2, haystack.forEachByte(factory(algorithm, "c").newSearchProcessor()));
        assertEquals(5, haystack.forEachByte(factory(algorithm, "c☺").newSearchProcessor()));
        assertEquals(-1, haystack.forEachByte(factory(algorithm, "c☺☺").newSearchProcessor()));
        assertEquals(-1, haystack.forEachByte(factory(algorithm, "c☺x").newSearchProcessor()));

        assertEquals(5, haystack.forEachByte(factory(algorithm, "☺").newSearchProcessor()));
        assertEquals(-1, haystack.forEachByte(factory(algorithm, "☺☺").newSearchProcessor()));
        assertEquals(-1, haystack.forEachByte(factory(algorithm, "☺x").newSearchProcessor()));

        assertEquals(-1, haystack.forEachByte(factory(algorithm, "z").newSearchProcessor()));
        assertEquals(-1, haystack.forEachByte(factory(algorithm, "aa").newSearchProcessor()));
        assertEquals(-1, haystack.forEachByte(factory(algorithm, "ba").newSearchProcessor()));
        assertEquals(-1, haystack.forEachByte(factory(algorithm, "abcd").newSearchProcessor()));
        assertEquals(-1, haystack.forEachByte(factory(algorithm, "abcde").newSearchProcessor()));

        haystack.release();
    }

    @ParameterizedTest
    @EnumSource(Algorithm.class)
    public void testRepeating(Algorithm algorithm) {
        final ByteBuf haystack = Unpooled.copiedBuffer("abcababc", CharsetUtil.UTF_8);
        final int length = haystack.readableBytes();
        SearchProcessor processor = factory(algorithm, "ab").newSearchProcessor();

        assertEquals(1,  haystack.forEachByte(processor));
        assertEquals(4,  haystack.forEachByte(2, length - 2, processor));
        assertEquals(6,  haystack.forEachByte(5, length - 5, processor));
        assertEquals(-1, haystack.forEachByte(7, length - 7, processor));

        haystack.release();
    }

    @ParameterizedTest
    @EnumSource(Algorithm.class)
    public void testOverlapping(Algorithm algorithm) {
        final ByteBuf haystack = Unpooled.copiedBuffer("ababab", CharsetUtil.UTF_8);
        final int length = haystack.readableBytes();
        SearchProcessor processor = factory(algorithm, "bab").newSearchProcessor();

        assertEquals(3,  haystack.forEachByte(processor));
        assertEquals(5,  haystack.forEachByte(4, length - 4, processor));
        assertEquals(-1, haystack.forEachByte(6, length - 6, processor));

        haystack.release();
    }

    @ParameterizedTest
    @EnumSource(Algorithm.class)
    public void testLongInputs(Algorithm algorithm) {
        final int haystackLen = 1024;
        final int needleLen = 64;

        final byte[] haystackBytes = new byte[haystackLen];
        haystackBytes[haystackLen - 1] = 1;
        final ByteBuf haystack = Unpooled.copiedBuffer(haystackBytes); // 00000...00001

        final byte[] needleBytes = new byte[needleLen]; // 000...000
        assertEquals(needleLen - 1, haystack.forEachByte(factory(algorithm, needleBytes).newSearchProcessor()));

        needleBytes[needleLen - 1] = 1; // 000...001
        assertEquals(haystackLen - 1, haystack.forEachByte(factory(algorithm, needleBytes).newSearchProcessor()));

        needleBytes[needleLen - 1] = 2; // 000...002
        assertEquals(-1, haystack.forEachByte(factory(algorithm, needleBytes).newSearchProcessor()));

        needleBytes[needleLen - 1] = 0;
        needleBytes[0] = 1; // 100...000
        assertEquals(-1, haystack.forEachByte(factory(algorithm, needleBytes).newSearchProcessor()));
    }

    @ParameterizedTest
    @EnumSource(Algorithm.class)
    public void testUniqueLen64Substrings(Algorithm algorithm) {
        final byte[] haystackBytes = new byte[32 * 65]; // 1, 2, 2, 3, 3, 3, 4, 4, 4, 4, ...
        int pos = 0;
        for (int i = 1; i <= 64; i++) {
            for (int j = 0; j < i; j++) {
                haystackBytes[pos++] = (byte) i;
            }
        }
        final ByteBuf haystack = Unpooled.copiedBuffer(haystackBytes);

        for (int start = 0; start < haystackBytes.length - 64; start++) {
            final byte[] needle = Arrays.copyOfRange(haystackBytes, start, start + 64);
            assertEquals(start + 63, haystack.forEachByte(factory(algorithm, needle).newSearchProcessor()));
        }
    }

    private SearchProcessorFactory factory(Algorithm algorithm, byte[] needle) {
        return algorithm.newFactory(needle);
    }

    private SearchProcessorFactory factory(Algorithm algorithm, String needle) {
        return factory(algorithm, needle.getBytes(CharsetUtil.UTF_8));
    }

}
