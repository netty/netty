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

import static org.junit.Assert.*;

public class MultiSearchProcessorTest {

    @Test
    public void testSearchForMultiple() {
        final ByteBuf haystack = Unpooled.copiedBuffer("one two three one", CharsetUtil.UTF_8);
        final int length = haystack.readableBytes();

        final MultiSearchProcessor processor = AbstractMultiSearchProcessorFactory.newAhoCorasicSearchProcessorFactory(
                bytes("one"),
                bytes("two"),
                bytes("three")
        ).newSearchProcessor();

        assertEquals(-1, processor.getFoundNeedleId());

        assertEquals(2, haystack.forEachByte(processor));
        assertEquals(0, processor.getFoundNeedleId()); // index of "one" in needles[]

        assertEquals(6, haystack.forEachByte(3, length - 3, processor));
        assertEquals(1, processor.getFoundNeedleId()); // index of "two" in needles[]

        assertEquals(12, haystack.forEachByte(7, length - 7, processor));
        assertEquals(2, processor.getFoundNeedleId()); // index of "three" in needles[]

        assertEquals(16, haystack.forEachByte(13, length - 13, processor));
        assertEquals(0, processor.getFoundNeedleId()); // index of "one" in needles[]

        assertEquals(-1, haystack.forEachByte(17, length - 17, processor));

        haystack.release();
    }

    @Test
    public void testSearchForMultipleOverlapping() {
        final ByteBuf haystack = Unpooled.copiedBuffer("abcd", CharsetUtil.UTF_8);
        final int length = haystack.readableBytes();

        final MultiSearchProcessor processor = AbstractMultiSearchProcessorFactory.newAhoCorasicSearchProcessorFactory(
                bytes("ab"),
                bytes("bc"),
                bytes("cd")
        ).newSearchProcessor();

        assertEquals(1, haystack.forEachByte(processor));
        assertEquals(0, processor.getFoundNeedleId()); // index of "ab" in needles[]

        assertEquals(2, haystack.forEachByte(2, length - 2, processor));
        assertEquals(1, processor.getFoundNeedleId()); // index of "bc" in needles[]

        assertEquals(3, haystack.forEachByte(3, length - 3, processor));
        assertEquals(2, processor.getFoundNeedleId()); // index of "cd" in needles[]

        haystack.release();
    }

    @Test
    public void findLongerNeedleInCaseOfSuffixMatch() {
        final ByteBuf haystack = Unpooled.copiedBuffer("xabcx", CharsetUtil.UTF_8);

        final MultiSearchProcessor processor1 = AbstractMultiSearchProcessorFactory.newAhoCorasicSearchProcessorFactory(
                bytes("abc"),
                bytes("bc")
        ).newSearchProcessor();

        assertEquals(3, haystack.forEachByte(processor1)); // end of "abc" in haystack
        assertEquals(0, processor1.getFoundNeedleId()); // index of "abc" in needles[]

        final MultiSearchProcessor processor2 = AbstractMultiSearchProcessorFactory.newAhoCorasicSearchProcessorFactory(
                bytes("bc"),
                bytes("abc")
        ).newSearchProcessor();

        assertEquals(3, haystack.forEachByte(processor2)); // end of "abc" in haystack
        assertEquals(1, processor2.getFoundNeedleId()); // index of "abc" in needles[]

        haystack.release();
    }

    private static byte[] bytes(String s) {
        return s.getBytes(CharsetUtil.UTF_8);
    }

}
