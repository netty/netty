/*
 * Copyright 2025 The Netty Project
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

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class CompletionBufferTest {

    @Test
    public void testProcess() {
        CompletionBuffer buffer = new CompletionBuffer(8, 0);
        buffer.add(8, 5, 1);
        buffer.add(10, 0, 2);
        buffer.add(10, 0, 3);
        assertProcessOneNow(buffer, 8, 5, 1);
        assertProcessOneNow(buffer, 10, 0, 2);

        assertFalse(buffer.processOneNow((res, flags, udata) -> {
            fail();
            return true;
        }, 4));

        AtomicInteger called = new AtomicInteger();
        buffer.processNow((res, flags, udata) -> {
            called.incrementAndGet();
            assertEquals(3, udata);
            return true;
        });
        assertEquals(1, called.get());
    }

    @Test
    public void testProcessStops() {
        CompletionBuffer buffer = new CompletionBuffer(8, 0);
        buffer.add(8, 5, 1);
        buffer.add(10, 0, 2);
        buffer.add(10, 0, 3);

        AtomicInteger called = new AtomicInteger();
        buffer.processNow((res, flags, udata) -> {
            called.incrementAndGet();
            return false;
        });
        assertEquals(1, called.get());
    }

    private static void assertProcessOneNow(
            CompletionBuffer buffer, int expectedRes, int expectedFlags, long expectedUdata) {
        assertTrue(buffer.processOneNow((res, flags, udata) -> {
            assertEquals(expectedRes, res);
            assertEquals(expectedFlags, flags);
            assertEquals(expectedUdata, udata);
            return true;
        }, expectedUdata));
    }
}
