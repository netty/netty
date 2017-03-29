/*
 * Copyright 2017 The Netty Project
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
package io.netty.util.internal;

import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.ByteBuffer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

public class PlatformDependent0Test {

    @BeforeClass
    public static void assumeUnsafe() {
        assumeTrue(PlatformDependent0.hasUnsafe());
        assumeTrue(PlatformDependent0.hasDirectBufferNoCleanerConstructor());
    }

    @Test
    public void testNewDirectBufferNegativeMemoryAddress() {
        testNewDirectBufferMemoryAddress(-1);
    }

    @Test
    public void testNewDirectBufferNonNegativeMemoryAddress() {
        testNewDirectBufferMemoryAddress(10);
    }

    @Test
    public void testNewDirectBufferZeroMemoryAddress() {
        PlatformDependent0.newDirectBuffer(0, 10);
    }

    private static void testNewDirectBufferMemoryAddress(long address) {
        assumeTrue(PlatformDependent0.hasDirectBufferNoCleanerConstructor());

        int capacity = 10;
        ByteBuffer buffer = PlatformDependent0.newDirectBuffer(address, capacity);
        assertEquals(address, PlatformDependent0.directBufferAddress(buffer));
        assertEquals(capacity, buffer.capacity());
    }
}
