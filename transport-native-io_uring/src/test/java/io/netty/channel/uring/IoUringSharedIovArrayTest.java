/*
 * Copyright 2026 The Netty Project
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
package io.netty.channel.uring;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.unix.IovArray;
import io.netty.channel.unix.Limits;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class IoUringSharedIovArrayTest {

    @Test
    public void eachWriteMayUseIovMax() {
        IoUringSharedIovArray shared = new IoUringSharedIovArray(2 * Limits.IOV_MAX);
        ByteBuf buf = Unpooled.directBuffer().writeZero(1);
        try {
            IovArray first = shared.next();
            for (int i = 0; i < Limits.IOV_MAX; i++) {
                assertTrue(first.add(buf, 0, 1));
            }
            assertFalse(first.add(buf, 0, 1));

            assertFalse(shared.isFull());
            IovArray second = shared.next();
            assertEquals(0, second.count());
            for (int i = 0; i < Limits.IOV_MAX; i++) {
                assertTrue(second.add(buf, 0, 1));
            }
            assertEquals(first.memoryAddress(Limits.IOV_MAX), second.memoryAddress(0));
            assertTrue(shared.isFull());
        } finally {
            buf.release();
            shared.release();
        }
    }

    @Test
    public void nextWriteStartsAfterThePreviousOne() {
        IoUringSharedIovArray shared = new IoUringSharedIovArray(64);
        ByteBuf buf = Unpooled.directBuffer().writeZero(1);
        try {
            IovArray first = shared.next();
            assertTrue(first.add(buf, 0, 1));
            assertTrue(first.add(buf, 0, 1));

            IovArray second = shared.next();
            assertEquals(0, second.count());
            assertEquals(first.memoryAddress(2), second.memoryAddress(0));
        } finally {
            buf.release();
            shared.release();
        }
    }

    @Test
    public void emptyWriteReusesItsIovArray() {
        IoUringSharedIovArray shared = new IoUringSharedIovArray(64);
        try {
            IovArray first = shared.next();
            assertEquals(first.memoryAddress(0), shared.next().memoryAddress(0));
        } finally {
            shared.release();
        }
    }

    @Test
    public void clearReusesAllEntries() {
        IoUringSharedIovArray shared = new IoUringSharedIovArray(64);
        ByteBuf buf = Unpooled.directBuffer().writeZero(1);
        try {
            IovArray first = shared.next();
            assertTrue(first.add(buf, 0, 1));
            IovArray second = shared.next();
            assertTrue(second.add(buf, 0, 1));

            shared.clear();
            assertEquals(first.memoryAddress(0), shared.next().memoryAddress(0));
        } finally {
            buf.release();
            shared.release();
        }
    }
}
