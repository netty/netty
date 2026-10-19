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
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.nio.ByteBuffer;
import java.util.Locale;

import static java.lang.invoke.MethodType.methodType;

/**
 * Allocates direct buffers with malloc and free from libc, linked through the Foreign Function &amp; Memory API.
 * <p>
 * The FFM API is compiled against the {@code netty-ffm-stub} module. It and the signature-polymorphic
 * {@link MethodHandle} calls are only used from the nested classes, which are only loaded on Java versions that
 * have the FFM API, so this class still loads and verifies where it is missing (e.g. Android).
 */
public class CleanerJava24Linker implements Cleaner {
    private static final InternalLogger logger;
    private static final boolean SUPPORTED;

    static {
        Throwable error;
        if (System.getProperty("org.graalvm.nativeimage.imagecode") != null) {
            // A native image can only link the downcalls that were registered when the image was built, and
            // linking on first use is too late to fall back to another cleaner, so a native image never uses
            // this one. We don't use PlatformDependent0 or the logger here, since this class may be initialized
            // at build time.
            logger = null;
            error = new UnsupportedOperationException(
                    "Linking to malloc and free is not supported in a native image.");
        } else {
            logger = InternalLoggerFactory.getInstance(CleanerJava24Linker.class);
            // Only attempt to use MemorySegments on Java 24 or greater, where warnings about Unsafe
            // memory access operations start to appear.
            // The following JDK bugs do NOT affect our implementation because the memory segments we
            // create are associated with the GLOBAL_SESSION:
            // - https://bugs.openjdk.org/browse/JDK-8357145
            // - https://bugs.openjdk.org/browse/JDK-8357268
            if (PlatformDependent0.javaVersion() >= 24) {
                try {
                    error = Symbols.UNAVAILABILITY_CAUSE;
                } catch (Throwable throwable) {
                    error = throwable;
                }
            } else {
                error = new UnsupportedOperationException("java.lang.foreign.MemorySegment unavailable");
            }
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
        return CleanableDirectBufferImpl.allocate(capacity);
    }

    @Override
    public void freeDirectBuffer(ByteBuffer buffer) {
        throw new UnsupportedOperationException("Cannot clean arbitrary ByteBuffer instances");
    }

    @Override
    public boolean hasExpensiveClean() {
        return false;
    }

    static long malloc(int capacity) {
        // Always allocate at least 1 byte, to avoid relying on non-portable behavior
        // of malloc(3) for zero size allocations.
        long addr = Holder.malloc(Math.max(capacity, 1L));
        if (addr == 0) {
            throw new OutOfMemoryError("malloc(2) failed to allocate " + capacity + " bytes");
        }
        return addr;
    }

    static void free(long memoryAddress) {
        Holder.free(memoryAddress);
    }

    /**
     * Checks that native access is enabled for this module, and that this platform has a 64-bit native linker
     * that finds malloc and free.
     */
    private static final class Symbols {
        private static final long RTLD_DEFAULT_UNSUPPORTED = Long.MIN_VALUE;
        static final MemorySegment MALLOC;
        static final MemorySegment FREE;
        static final Throwable UNAVAILABILITY_CAUSE;

        static {
            MemorySegment malloc = null;
            MemorySegment free = null;
            Throwable error = null;
            try {
                // First, we need to check if we have access to "restricted" methods through the Java Module system.
                // java.lang.Module is not part of the Java 8 API we compile against, so we look it up.
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                Class<?> moduleCls = Class.forName("java.lang.Module");
                MethodHandle getModule = lookup.findVirtual(
                        Class.class, "getModule", methodType(moduleCls));
                MethodHandle isNativeAccessEnabledModule = lookup.findVirtual(
                        moduleCls, "isNativeAccessEnabled", methodType(boolean.class));
                MethodHandle isNativeAccessEnabledForClass = MethodHandles.filterArguments(
                        isNativeAccessEnabledModule, 0, getModule);
                boolean isNativeAccessEnabled =
                        (boolean) isNativeAccessEnabledForClass.invokeExact(CleanerJava24Linker.class);
                if (!isNativeAccessEnabled) {
                    throw new UnsupportedOperationException(
                            "Native access (restricted methods) is not enabled for the io.netty.common module.");
                }

                // Second, we need to check the size of a pointer address. For simplicity, we'd like to assume the size
                // of an address is the same as a Java long. So effectively, we're only enabled on 64-bit platforms.
                if (ValueLayout.ADDRESS.byteSize() != Long.BYTES) {
                    throw new UnsupportedOperationException(
                            "Linking to malloc and free is only supported on 64-bit platforms.");
                }

                // Finally, we check that this platform has a native linker, and that it finds malloc and free.
                Linker linker = Linker.nativeLinker();
                long rtldDefault = rtldDefault();
                SymbolLookup symbols = linker.defaultLookup();
                if (rtldDefault != RTLD_DEFAULT_UNSUPPORTED) {
                    MethodHandle dlsym = linker.downcallHandle(linker.defaultLookup().findOrThrow("dlsym"),
                        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
                    try (Arena arena = Arena.ofConfined()) {
                        long mallocAddr = (long) dlsym.invoke(rtldDefault, arena.allocateFrom("malloc"));
                        long freeAddr = (long) dlsym.invoke(rtldDefault, arena.allocateFrom("free"));
                        if (mallocAddr != 0 && freeAddr != 0) {
                            malloc = MemorySegment.ofAddress(mallocAddr);
                            free = MemorySegment.ofAddress(freeAddr);
                        }
                    }
                }
                if (malloc == null || malloc == MemorySegment.NULL || free == null || free == MemorySegment.NULL) {
                    if (logger != null) {
                        logger.debug("dlsym(RTLD_DEFAULT) did not resolve malloc/free, using default lookup");
                    }
                    malloc = symbols.findOrThrow("malloc");
                    free = symbols.findOrThrow("free");
                }
            } catch (Throwable throwable) {
                malloc = null;
                free = null;
                error = throwable;
            }
            MALLOC = malloc;
            FREE = free;
            UNAVAILABILITY_CAUSE = error;
        }

        /**
         * The value of the {@code RTLD_DEFAULT} pseudo-handle for {@code dlsym(3)} on this platform, or
         * {@link #RTLD_DEFAULT_UNSUPPORTED} if we do not know it. The value is not standardised: glibc and musl use
         * {@code (void*) 0}, while macOS and the BSDs use {@code (void*) -2}. Windows has no {@code dlsym},
         * and a native image must keep using the default lookup because the resolved addresses would otherwise be
         * captured in the image heap at build time.
         */
        private static long rtldDefault() {
            if (System.getProperty("org.graalvm.nativeimage.imagecode") != null) {
                return RTLD_DEFAULT_UNSUPPORTED;
            }
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            if (os.contains("linux")) {
                return 0L;
            }
            if (os.contains("mac") || os.contains("darwin") || os.contains("bsd")) {
                return -2L;
            }
            return RTLD_DEFAULT_UNSUPPORTED;
        }
    }

    /**
     * Links malloc and free on first use rather than when {@link PlatformDependent} is initialized,
     * because creating the downcall handles is a startup cost for applications that may never allocate
     * a direct buffer.
     */
    private static final class Holder {
        private static final MethodHandle INVOKE_MALLOC;
        private static final MethodHandle INVOKE_FREE;

        static {
            Linker linker = Linker.nativeLinker();
            INVOKE_MALLOC = linker.downcallHandle(Symbols.MALLOC,
                FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));
            INVOKE_FREE = linker.downcallHandle(Symbols.FREE,
                FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
        }

        static long malloc(long size) {
            try {
                return (long) INVOKE_MALLOC.invokeExact(size);
            } catch (Throwable e) {
                throw new Error(e); // Should not happen.
            }
        }

        static void free(long memoryAddress) {
            try {
                INVOKE_FREE.invokeExact(memoryAddress);
            } catch (Throwable e) {
                throw new Error(e); // Should not happen.
            }
        }
    }

    private static final class CleanableDirectBufferImpl implements CleanableDirectBuffer {
        private final ByteBuffer buffer;
        private final long memoryAddress;

        CleanableDirectBufferImpl(ByteBuffer buffer, long memoryAddress) {
            this.buffer = buffer;
            this.memoryAddress = memoryAddress;
        }

        // Creates the ByteBuffer close to the caller: C2 only scalar-replaces it when the whole (deep)
        // DirectByteBuffer construction fits within MaxInlineLevel of the caller.
        static CleanableDirectBuffer allocate(int capacity) {
            PlatformDependent.incrementMemoryCounter(capacity);
            long addr;
            try {
                addr = malloc(capacity);
            } catch (Throwable e) {
                PlatformDependent.decrementMemoryCounter(capacity);
                throw e;
            }
            try {
                return new CleanableDirectBufferImpl(
                        MemorySegment.ofAddress(addr).reinterpret(capacity).asByteBuffer(), addr);
            } catch (Throwable throwable) {
                PlatformDependent.decrementMemoryCounter(capacity);
                Error error = new Error(throwable);
                try {
                    free(addr);
                } catch (Throwable e) {
                    error.addSuppressed(e);
                }
                throw error;
            }
        }

        @Override
        public ByteBuffer buffer() {
            return buffer;
        }

        @Override
        public void clean() {
            int capacity = buffer.capacity();
            free(memoryAddress);
            PlatformDependent.decrementMemoryCounter(capacity);
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
