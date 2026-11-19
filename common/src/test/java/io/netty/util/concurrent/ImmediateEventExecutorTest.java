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
package io.netty.util.concurrent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class ImmediateEventExecutorTest {

    @Test
    public void testTerminationFutureFailureDoesNotFillInStackTrace() {
        // ImmediateEventExecutor.INSTANCE is a singleton that holds on to the failure of its terminationFuture for
        // the lifetime of its Classloader, so that failure must not populate a (native) backtrace: doing so would
        // pin the Classloader of whatever thread happened to trigger the lazy initialization of INSTANCE
        // (see https://github.com/netty/netty/issues/17128).
        Throwable cause = ImmediateEventExecutor.INSTANCE.terminationFuture().cause();
        assertInstanceOf(UnsupportedOperationException.class, cause);

        StackTraceElement[] before = cause.getStackTrace();
        assertEquals(1, before.length);
        assertEquals(ImmediateEventExecutor.class.getName(), before[0].getClassName());
        assertEquals("terminationFuture", before[0].getMethodName());

        // fillInStackTrace() must be a no-op; otherwise it would repopulate the backtrace with native frames.
        cause.fillInStackTrace();
        assertArrayEquals(before, cause.getStackTrace());
    }
}
