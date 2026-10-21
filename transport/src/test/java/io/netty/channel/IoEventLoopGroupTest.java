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
package io.netty.channel;

import io.netty.channel.local.LocalIoHandle;
import io.netty.channel.local.LocalIoHandler;
import io.netty.channel.nio.NioIoHandle;
import io.netty.channel.nio.NioIoHandler;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class IoEventLoopGroupTest {

    private static final int THREADS = 4;

    @Test
    void testIsIoTypeDoesNotAdvanceTheChooser() {
        assertChooserNotAdvancedBy(group -> {
            assertTrue(group.isIoType(LocalIoHandler.class));
            assertFalse(group.isIoType(NioIoHandler.class));
        });
    }

    @Test
    void testIsCompatibleDoesNotAdvanceTheChooser() {
        assertChooserNotAdvancedBy(group -> {
            assertTrue(group.isCompatible(LocalIoHandle.class));
            assertFalse(group.isCompatible(NioIoHandle.class));
        });
    }

    /**
     * Asserts that the given action does not change which {@link IoEventLoop} is returned by the next
     * {@link IoEventLoopGroup#next()} call.
     */
    private static void assertChooserNotAdvancedBy(Consumer<IoEventLoopGroup> action) {
        IoEventLoopGroup group = new MultiThreadIoEventLoopGroup(THREADS, LocalIoHandler.newFactory());
        try {
            // Record the round-robin order of the group first, so we know what next() must return afterwards.
            IoEventLoop[] roundRobin = new IoEventLoop[THREADS];
            for (int i = 0; i < THREADS; i++) {
                roundRobin[i] = group.next();
            }
            for (int i = 0; i < THREADS; i++) {
                action.accept(group);
                assertSame(roundRobin[i], group.next(),
                        "querying the IO type of the group must not advance the chooser used by next()");
            }
        } finally {
            group.shutdownGracefully();
        }
    }
}
