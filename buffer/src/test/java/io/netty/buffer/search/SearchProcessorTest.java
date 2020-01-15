/*
 * Copyright 2020 The Netty Project
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
package io.netty.buffer.search;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.CharsetUtil;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class SearchProcessorTest {

    private enum Algorithm {
        KNUTH_MORRIS_PRATT {
            @Override
            SearchProcessorFactory newFactory(byte[] needle) {
                return SearchProcessorFactory.newKmpSearchProcessorFactory(needle);
            }
        },
        SHIFTING_BIT_MASK {
            @Override
            SearchProcessorFactory newFactory(byte[] needle) {
                return SearchProcessorFactory.newShiftingBitMaskSearchProcessorFactory(needle);
            }
        },
        AHO_CORASIC {
            @Override
            SearchProcessorFactory newFactory(byte[] needle) {
                return MultiSearchProcessorFactory.newAhoCorasicSearchProcessorFactory(needle);
            }
        };
        abstract SearchProcessorFactory newFactory(byte[] needle);
    }

    @Parameters(name = "{0} algorithm")
    public static Object[] algorithms() {
        return Algorithm.values();
    }

    @Parameter
    public Algorithm algorithm;

    @Test
    public void testSearch() {
        final ByteBuf haystack = Unpooled.copiedBuffer("abc☺", CharsetUtil.UTF_8);

        assertEquals(0, haystack.forEachByte(factory("a").newSearchProcessor()));
        assertEquals(1, haystack.forEachByte(factory("ab").newSearchProcessor()));
        assertEquals(2, haystack.forEachByte(factory("abc").newSearchProcessor()));
        assertEquals(5, haystack.forEachByte(factory("abc☺").newSearchProcessor()));

        assertEquals(1, haystack.forEachByte(factory("b").newSearchProcessor()));
        assertEquals(2, haystack.forEachByte(factory("bc").newSearchProcessor()));
        assertEquals(5, haystack.forEachByte(factory("bc☺").newSearchProcessor()));

        assertEquals(2, haystack.forEachByte(factory("c").newSearchProcessor()));
        assertEquals(5, haystack.forEachByte(factory("c☺").newSearchProcessor()));

        assertEquals(5, haystack.forEachByte(factory("☺").newSearchProcessor()));

        assertEquals(-1, haystack.forEachByte(factory("z").newSearchProcessor()));
        assertEquals(-1, haystack.forEachByte(factory("aa").newSearchProcessor()));
        assertEquals(-1, haystack.forEachByte(factory("ba").newSearchProcessor()));
        assertEquals(-1, haystack.forEachByte(factory("abcd").newSearchProcessor()));
        assertEquals(-1, haystack.forEachByte(factory("abcde").newSearchProcessor()));

        haystack.release();
    }

    @Test
    public void testRepeating() {
        final ByteBuf haystack = Unpooled.copiedBuffer("abcababc", CharsetUtil.UTF_8);
        final int length = haystack.readableBytes();
        SearchProcessor processor = factory("ab").newSearchProcessor();

        assertEquals(1,  haystack.forEachByte(processor));
        assertEquals(4,  haystack.forEachByte(2, length - 2, processor));
        assertEquals(6,  haystack.forEachByte(5, length - 5, processor));
        assertEquals(-1, haystack.forEachByte(7, length - 7, processor));

        haystack.release();
    }

    @Test
    public void testOverlapping() {
        final ByteBuf haystack = Unpooled.copiedBuffer("ababab", CharsetUtil.UTF_8);
        final int length = haystack.readableBytes();
        SearchProcessor processor = factory("bab").newSearchProcessor();

        assertEquals(3,  haystack.forEachByte(processor));
        assertEquals(5,  haystack.forEachByte(4, length - 4, processor));
        assertEquals(-1, haystack.forEachByte(6, length - 6, processor));

        haystack.release();
    }

    private SearchProcessorFactory factory(String needle) {
        return algorithm.newFactory(needle.getBytes(CharsetUtil.UTF_8));
    }

}
