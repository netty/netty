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
package io.netty.util.internal;

import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;

/**
 * Provide a way to clean direct {@link ByteBuffer} instances on Java 24+,
 * where we don't have {@code Unsafe} available, but we have memory segments.
 * <p>
 * The FFM API is compiled against the {@code netty-ffm-stub} module, and only used from the nested {@link Arenas}
 * class, which is only loaded on Java versions that have the FFM API.
 */
final class CleanerJava25 implements Cleaner {
    private static final InternalLogger logger;

    private static final boolean SUPPORTED;

    static {
        boolean suitableJavaVersion;
        if (System.getProperty("org.graalvm.nativeimage.imagecode") != null) {
            // native image supports this since 25, but we don't use PlatformDependent0 here, since
            // we need to initialize CleanerJava25 at build time.
            String v = System.getProperty("java.specification.version");
            try {
                suitableJavaVersion = Integer.parseInt(v) >= 25;
            } catch (NumberFormatException e) {
                suitableJavaVersion = false;
            }
            // also need to prevent initializing the logger at build time
            logger = null;
        } else {
            // Only attempt to use MemorySegments on Java 25 or greater, because of the following JDK bugs:
            // - https://bugs.openjdk.org/browse/JDK-8357145
            // - https://bugs.openjdk.org/browse/JDK-8357268
            suitableJavaVersion = PlatformDependent0.javaVersion() >= 25;
            logger = InternalLoggerFactory.getInstance(CleanerJava25.class);
        }

        Throwable error;
        if (suitableJavaVersion) {
            try {
                Arenas.checkShared();
                error = null;
            } catch (Throwable throwable) {
                error = throwable;
            }
        } else {
            error = new UnsupportedOperationException("java.lang.foreign.MemorySegment unavailable");
        }
        if (logger != null) {
            if (error == null) {
                logger.debug("java.nio.ByteBuffer.cleaner(): available");
            } else {
                logger.debug("java.nio.ByteBuffer.cleaner(): unavailable", error);
            }
        }
        SUPPORTED = error == null;
    }

    static boolean isSupported() {
        return SUPPORTED;
    }

    @Override
    public CleanableDirectBuffer allocate(int capacity) {
        PlatformDependent.incrementMemoryCounter(capacity);
        try {
            return Arenas.allocate(capacity);
        } catch (RuntimeException e) {
            PlatformDependent.decrementMemoryCounter(capacity);
            throw e; // Propagate the runtime exceptions that the Arena would normally throw.
        } catch (Throwable e) {
            PlatformDependent.decrementMemoryCounter(capacity);
            throw new IllegalStateException("Unexpected allocation exception", e);
        }
    }

    @Override
    public void freeDirectBuffer(ByteBuffer buffer) {
        throw new UnsupportedOperationException("Cannot clean arbitrary ByteBuffer instances");
    }

    @Override
    public boolean hasExpensiveClean() {
        // Closing shared arenas can be fairly expensive if we do it a lot,
        // because it relies on inter-thread handshakes.
        return true;
    }

    private static final class Arenas {
        static void checkShared() {
            // Try to access shared Arena which might fail on GraalVM 25.0.0 if not enabled
            // See https://github.com/netty/netty/issues/15762
            Arena.ofShared().close();
        }

        static CleanableDirectBufferImpl allocate(int capacity) {
            // Each buffer gets its own shared arena, which is closed to free the memory.
            Arena arena = Arena.ofShared();
            MemorySegment segment = arena.allocate(capacity);
            return new CleanableDirectBufferImpl(arena, segment.asByteBuffer(), segment.address());
        }
    }

    private static final class CleanableDirectBufferImpl implements CleanableDirectBuffer {
        private final AutoCloseable closeable;
        private final ByteBuffer buffer;
        private final long memoryAddress;

        CleanableDirectBufferImpl(AutoCloseable closeable, ByteBuffer buffer, long memoryAddress) {
            this.closeable = closeable;
            this.buffer = buffer;
            this.memoryAddress = memoryAddress;
        }

        @Override
        public ByteBuffer buffer() {
            return buffer;
        }

        @Override
        public void clean() {
            int capacity = buffer.capacity();
            try {
                closeable.close();
            } catch (RuntimeException e) {
                throw e; // Propagate the runtime exceptions that Arena would normally throw.
            } catch (Exception e) {
                throw new IllegalStateException("Unexpected close exception", e);
            } finally {
                PlatformDependent.decrementMemoryCounter(capacity);
            }
        }

        @Override
        public boolean hasMemoryAddress() {
            return true;
        }

        @Override
        public long memoryAddress() {
            return memoryAddress;
        }
    }
}
