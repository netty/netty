/*
 * Copyright 2016 The Netty Project
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
package io.netty.buffer;

import io.netty.util.IllegalReferenceCountException;
import io.netty.util.ResourceLeakDetector;
import io.netty.util.ResourceLeakDetectorFactory;
import io.netty.util.ResourceLeakTracker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayDeque;
import java.util.Queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class SimpleLeakAwareByteBufTest extends BigEndianHeapByteBufTest {
    private final Class<? extends ByteBuf> clazz = leakClass();
    private final Queue<NoopResourceLeakTracker<ByteBuf>> trackers = new ArrayDeque<NoopResourceLeakTracker<ByteBuf>>();

    @Override
    protected final ByteBuf newBuffer(int capacity, int maxCapacity) {
        return wrap(super.newBuffer(capacity, maxCapacity));
    }

    private ByteBuf wrap(ByteBuf buffer) {
        NoopResourceLeakTracker<ByteBuf> tracker = new NoopResourceLeakTracker<ByteBuf>();
        ByteBuf leakAwareBuf = wrap(buffer, tracker);
        trackers.add(tracker);
        return leakAwareBuf;
    }

    protected SimpleLeakAwareByteBuf wrap(ByteBuf buffer, ResourceLeakTracker<ByteBuf> tracker) {
        return new SimpleLeakAwareByteBuf(buffer, tracker);
    }

    @BeforeEach
    @Override
    public void init() {
        super.init();
        trackers.clear();
    }

    @AfterEach
    @Override
    public void dispose() {
        super.dispose();

        for (;;) {
            NoopResourceLeakTracker<ByteBuf> tracker = trackers.poll();

            if (tracker == null) {
                break;
            }
            assertTrue(tracker.get());
        }
    }

    protected Class<? extends ByteBuf> leakClass() {
        return SimpleLeakAwareByteBuf.class;
    }

    @Test
    public void testWrapSlice() {
        assertWrapped(newBuffer(8).slice());
    }

    @Test
    public void testWrapSlice2() {
        assertWrapped(newBuffer(8).slice(0, 1));
    }

    @Test
    public void testWrapReadSlice() {
        ByteBuf buffer = newBuffer(8);
        if (buffer.isReadable()) {
            assertWrapped(buffer.readSlice(1));
        } else {
            assertTrue(buffer.release());
        }
    }

    @Test
    public void testWrapRetainedSlice() {
        ByteBuf buffer = newBuffer(8);
        assertWrapped(buffer.retainedSlice());
        assertTrue(buffer.release());
    }

    @Test
    public void testWrapRetainedSlice2() {
        ByteBuf buffer = newBuffer(8);
        if (buffer.isReadable()) {
            assertWrapped(buffer.retainedSlice(0, 1));
        }
        assertTrue(buffer.release());
    }

    @Test
    public void testWrapReadRetainedSlice() {
        ByteBuf buffer = newBuffer(8);
        if (buffer.isReadable()) {
            assertWrapped(buffer.readRetainedSlice(1));
        }
        assertTrue(buffer.release());
    }

    @Test
    public void testWrapDuplicate() {
        assertWrapped(newBuffer(8).duplicate());
    }

    @Test
    public void testWrapRetainedDuplicate() {
        ByteBuf buffer = newBuffer(8);
        assertWrapped(buffer.retainedDuplicate());
        assertTrue(buffer.release());
    }

    @Test
    public void testWrapReadOnly() {
        assertWrapped(newBuffer(8).asReadOnly());
    }

    protected final void assertWrapped(ByteBuf buf) {
        try {
            assertSame(clazz, buf.getClass());
        } finally {
            buf.release();
        }
    }

    enum ReleaseMechanism {
        RELEASE, RELEASE_COUNTED
    }

    @ParameterizedTest
    @EnumSource(ReleaseMechanism.class)
    void mustAddStackTraceForOriginalCloseMethodOnDoubleFree(ReleaseMechanism mechanism) throws Exception {
        int trackEveryBuffer = 1;
        ResourceLeakDetector<ByteBuf> detector = ResourceLeakDetectorFactory.instance()
                .newResourceLeakDetector(ByteBuf.class, trackEveryBuffer);
        ByteBuf origin = Unpooled.buffer();
        ByteBuf buf = wrap(origin, detector.track(origin));
        originalCloseCall(buf, mechanism);
        assertFalse(buf.isAccessible());
        IllegalReferenceCountException irce = assertThrows(IllegalReferenceCountException.class, buf::release);
        assertThat(irce).hasStackTraceContaining("originalCloseCall");
    }

    private static void originalCloseCall(ByteBuf buf, ReleaseMechanism mechanism) {
        switch (mechanism) {
            case RELEASE:
                buf.release();
                break;
            case RELEASE_COUNTED:
                buf.release(1);
                break;
            default:
                fail("Unknown release mechanism: " + mechanism);
        }
    }
}
