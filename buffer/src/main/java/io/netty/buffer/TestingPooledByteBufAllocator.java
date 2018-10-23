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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static io.netty.util.internal.StringUtil.NEWLINE;

/**
 * A {@link ByteBufAllocator} for unit testing.  This allocator performs leak tracking
 * on all allocated buffers.  At the end the test, call {@link #close} to assert
 * that all buffers have been freed.
 */
public class TestingPooledByteBufAllocator extends PooledByteBufAllocator implements Closeable {

    private final TestingResourceLeakDetector leakDetector = new TestingResourceLeakDetector();

    public TestingPooledByteBufAllocator() {
    }

    public TestingPooledByteBufAllocator(boolean preferDirect) {
        super(preferDirect);
    }

    @Override
    ByteBuf toLeakAwareBufferInternal(ByteBuf buf) {
        ResourceLeakTracker<ByteBuf> leak = leakDetector.track(buf);
        if (leak != null) {
            buf = new AdvancedLeakAwareByteBuf(buf, leak);
        }
        return buf;
    }

    @Override
    CompositeByteBuf toLeakAwareBufferInternal(CompositeByteBuf buf) {
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
        List<String> allLeaks = leakDetector.getAllLeaks();
        if (!allLeaks.isEmpty()) {
            throwLeakDetected(allLeaks);
        }
    }

    private static void throwLeakDetected(List<String> allLeaks) {
        int expectedBufferSize = 1024;
        for (String leak : allLeaks) {
            expectedBufferSize += leak.length();
        }

        StringBuilder buf = new StringBuilder(expectedBufferSize)
                .append("LEAK: ")
                .append(allLeaks.size())
                .append(" leak(s) detected. ")
                .append("ByteBuf.release() was not called before it's garbage-collected. ")
                .append("See http://netty.io/wiki/reference-counted-objects.html for more information.");
        for (int i = 0; i < allLeaks.size(); i++) {
            String leak = allLeaks.get(i);
            buf.append(NEWLINE)
                    .append("== ByteBuf ")
                    .append(i + 1)
                    .append(": ")
                    .append(leak);
        }
        buf.append(NEWLINE)
                .append("== Leak detection:");
        throw new AssertionError(buf.toString());
    }

    private static class TestingResourceLeakDetector extends ResourceLeakDetector<ByteBuf> {

        private final List<String> leaks = new ArrayList<String>(0);

        public TestingResourceLeakDetector() {
            super(ByteBuf.class);
        }

        @Override
        protected boolean shouldTrack(ByteBuf obj) {
            return true;
        }

        @Override
        protected boolean isLeakReportingEnabled() {
            return true;
        }

        @Override
        protected synchronized void reportTracedLeak(String resourceType, String records) {
            leaks.add(records);
        }

        @Override
        protected synchronized void reportUntracedLeak(String resourceType) {
            leaks.add("Untracked ByteBuf without created at information");
        }

        synchronized List<String> getAllLeaks() {
            disposeAllReferences();
            return Collections.unmodifiableList(new ArrayList<String>(leaks));
        }
    }
}
