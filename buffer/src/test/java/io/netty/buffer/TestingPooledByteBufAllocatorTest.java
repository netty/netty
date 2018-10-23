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

package io.netty.buffer;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertThat;

public class TestingPooledByteBufAllocatorTest {

    @Test
    public void testNoLeakByteBuffer() {
        TestingPooledByteBufAllocator allocator = new TestingPooledByteBufAllocator();
        try {
            allocator.buffer(100).release();
        } finally {
            allocator.close();
        }
    }

    @Test
    public void testLeakByteBuffer() {
        TestingPooledByteBufAllocator allocator = new TestingPooledByteBufAllocator();
        try {
            allocator.buffer(100).getByte(0);
            allocator.buffer(100).getByte(0);
        } finally {
            assertLeaked(allocator);
        }
    }

    @Test
    public void testNoLeakByteBufferDirect() {
        TestingPooledByteBufAllocator allocator = new TestingPooledByteBufAllocator(true);
        try {
            allocator.buffer(100).release();
        } finally {
            allocator.close();
        }
    }

    @Test
    public void testLeakByteBufferDirect() {
        TestingPooledByteBufAllocator allocator = new TestingPooledByteBufAllocator(true);
        try {
            allocator.buffer(100).getByte(0);
            allocator.buffer(100).getByte(0);
        } finally {
            assertLeaked(allocator);
        }
    }

    @Test
    public void testNoLeakCompositeBuffer() {
        TestingPooledByteBufAllocator allocator = new TestingPooledByteBufAllocator();
        try {
            allocator.compositeBuffer().release();
        } finally {
            allocator.close();
        }
    }

    @Test
    public void testLeakCompositeBuffer() {
        TestingPooledByteBufAllocator allocator = new TestingPooledByteBufAllocator();
        try {
            allocator.compositeBuffer();
            allocator.compositeBuffer();
        } finally {
            assertLeaked(allocator);
        }
    }

    private static void assertLeaked(TestingPooledByteBufAllocator allocator) {
        try {
            allocator.close();
        } catch (AssertionError e) {
            assertThat(e.getMessage(), containsString("LEAK"));
            assertThat(e.getMessage(), containsString("2 leak(s) detected."));
            assertThat(e.getMessage(), containsString("ByteBuf 1:"));
            assertThat(e.getMessage(), containsString("ByteBuf 2:"));
        }
    }
}
