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

import io.netty.util.ResourceLeakDetector;
import io.netty.util.ResourceLeakTracker;

import java.io.Closeable;

/**
 * A {@link ByteBufAllocator} for unit testing. This allocator maintains a private, non-static leak detector
 * and performs leak tracking on all allocated buffers. At the end the test, call {@link #close} to assert
 * that all buffers have been freed.
 */
public class TestingPooledByteBufAllocator extends PooledByteBufAllocator implements Closeable {

    private final ResourceLeakDetector leakDetector = new ResourceLeakDetector(AbstractByteBuf.class, 1);

    {
        // While we might try to set the level manually, or try other hacks,
        // this check maintains generality should ResourceLeakDetector change in the future.
        // Don't run your tests with leak detection disabled.
        if (!ResourceLeakDetector.isEnabled()) {
            throw new AssertionError("ResourceLeakDetector.isEnabled() returns false. Cannot continue.");
        }
    }

    public TestingPooledByteBufAllocator() {
    }

    public TestingPooledByteBufAllocator(boolean preferDirect) {
        super(preferDirect);
    }

    @Override
    public ByteBuf toLeakAwareBuffer(ByteBuf buf) {
        ResourceLeakTracker<ByteBuf> leak = leakDetector.track(buf);
        if (leak != null) {
            buf = new AdvancedLeakAwareByteBuf(buf, leak);
        }
        return buf;
    }

    @Override
    public CompositeByteBuf toLeakAwareBuffer(CompositeByteBuf buf) {
        ResourceLeakTracker<ByteBuf> leak = leakDetector.track(buf);
        if (leak != null) {
            buf = new AdvancedLeakAwareCompositeByteBuf(buf, leak);
        }
        return buf;
    }

    /**
     * Throws an exception if all allocated buffers have been not freed.
     * @throws AssertionError if buffers have been leaked.
     */
    @Override
    public void close() {
        leakDetector.assertAllResourcesDisposed();
    }
}
