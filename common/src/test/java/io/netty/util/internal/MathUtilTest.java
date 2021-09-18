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
package io.netty.util.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static io.netty.util.internal.MathUtil.*;

import org.junit.jupiter.api.Test;

public class MathUtilTest {

    @Test
    public void testFindNextPositivePowerOfTwo() {
        assertEquals(1, findNextPositivePowerOfTwo(0));
        assertEquals(1, findNextPositivePowerOfTwo(1));
        assertEquals(1024, findNextPositivePowerOfTwo(1000));
        assertEquals(1024, findNextPositivePowerOfTwo(1023));
        assertEquals(2048, findNextPositivePowerOfTwo(2048));
        assertEquals(1 << 30, findNextPositivePowerOfTwo((1 << 30) - 1));
        assertEquals(1, findNextPositivePowerOfTwo(-1));
        assertEquals(1, findNextPositivePowerOfTwo(-10000));
    }

    @Test
    public void testSafeFindNextPositivePowerOfTwo() {
        assertEquals(1, safeFindNextPositivePowerOfTwo(0));
        assertEquals(1, safeFindNextPositivePowerOfTwo(1));
        assertEquals(1024, safeFindNextPositivePowerOfTwo(1000));
        assertEquals(1024, safeFindNextPositivePowerOfTwo(1023));
        assertEquals(2048, safeFindNextPositivePowerOfTwo(2048));
        assertEquals(1 << 30, safeFindNextPositivePowerOfTwo((1 << 30) - 1));
        assertEquals(1, safeFindNextPositivePowerOfTwo(-1));
        assertEquals(1, safeFindNextPositivePowerOfTwo(-10000));
        assertEquals(1 << 30, safeFindNextPositivePowerOfTwo(Integer.MAX_VALUE));
        assertEquals(1 << 30, safeFindNextPositivePowerOfTwo((1 << 30) + 1));
        assertEquals(1, safeFindNextPositivePowerOfTwo(Integer.MIN_VALUE));
        assertEquals(1, safeFindNextPositivePowerOfTwo(Integer.MIN_VALUE + 1));
    }

    @Test
    public void testIsOutOfBounds() {
        assertFalse(isOutOfBounds(0, 0, 0));
        assertFalse(isOutOfBounds(0, 0, 1));
        assertFalse(isOutOfBounds(0, 1, 1));
        assertTrue(isOutOfBounds(1, 1, 1));
        assertTrue(isOutOfBounds(Integer.MAX_VALUE, 1, 1));
        assertTrue(isOutOfBounds(Integer.MAX_VALUE, Integer.MAX_VALUE, 1));
        assertTrue(isOutOfBounds(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE));
        assertFalse(isOutOfBounds(0, Integer.MAX_VALUE, Integer.MAX_VALUE));
        assertFalse(isOutOfBounds(0, Integer.MAX_VALUE - 1, Integer.MAX_VALUE));
        assertTrue(isOutOfBounds(0, Integer.MAX_VALUE, Integer.MAX_VALUE - 1));
        assertFalse(isOutOfBounds(Integer.MAX_VALUE - 1, 1, Integer.MAX_VALUE));
        assertTrue(isOutOfBounds(Integer.MAX_VALUE - 1, 1, Integer.MAX_VALUE - 1));
        assertTrue(isOutOfBounds(Integer.MAX_VALUE - 1, 2, Integer.MAX_VALUE));
        assertTrue(isOutOfBounds(1, Integer.MAX_VALUE, Integer.MAX_VALUE));
        assertTrue(isOutOfBounds(0, 1, Integer.MIN_VALUE));
        assertTrue(isOutOfBounds(0, 1, -1));
        assertTrue(isOutOfBounds(0, Integer.MAX_VALUE, 0));
    }

    @Test
    public void testCompare() {
        assertEquals(-1, compare(0, 1));
        assertEquals(-1, compare(0L, 1L));
        assertEquals(-1, compare(0, Integer.MAX_VALUE));
        assertEquals(-1, compare(0L, Long.MAX_VALUE));
        assertEquals(0, compare(0, 0));
        assertEquals(0, compare(0L, 0L));
        assertEquals(0, compare(Integer.MIN_VALUE, Integer.MIN_VALUE));
        assertEquals(0, compare(Long.MIN_VALUE, Long.MIN_VALUE));
        assertEquals(1, compare(Integer.MAX_VALUE, 0));
        assertEquals(1, compare(Integer.MAX_VALUE, Integer.MAX_VALUE - 1));
        assertEquals(1, compare(Long.MAX_VALUE, 0L));
        assertEquals(1, compare(Long.MAX_VALUE, Long.MAX_VALUE - 1));
    }
}
