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
import java.lang.invoke.MethodHandle;
import java.nio.ByteBuffer;
import java.util.Locale;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

public class CleanerJava24Linker implements Cleaner {
    private static final InternalLogger logger;

    private static final MethodHandle INVOKE_MALLOC;
    private static final MethodHandle INVOKE_FREE;

    private static final long RTLD_DEFAULT_UNSUPPORTED = Long.MIN_VALUE;

    static {
        boolean suitableJavaVersion;
        if (System.getProperty("org.graalvm.nativeimage.imagecode") != null) {
            // native image supports this since 25, but we don't use PlatformDependent0 here, since
            // we need to initialize CleanerJava24Linker at build time.
            String v = System.getProperty("java.specification.version");
            try {
                suitableJavaVersion = Integer.parseInt(v) >= 25;
            } catch (NumberFormatException e) {
                suitableJavaVersion = false;
            }
            // also need to prevent initializing the logger at build time
            logger = null;
        } else {
            // Only attempt to use MemorySegments on Java 24 or greater, where warnings about Unsafe
            // memory access operations start to appear.
            // The following JDK bugs do NOT affect our implementation because the memory segments we
            // create are associated with the GLOBAL_SESSION:
            // - https://bugs.openjdk.org/browse/JDK-8357145
            // - https://bugs.openjdk.org/browse/JDK-8357268
            suitableJavaVersion = PlatformDependent0.javaVersion() >= 24;
            logger = InternalLoggerFactory.getInstance(CleanerJava24Linker.class);
        }

        MethodHandle mallocMethod;
        MethodHandle freeMethod;
        Throwable error;

        if (suitableJavaVersion) {
            try {
                // First, we need to check if we have access to "restricted" methods through the Java Module system.
                boolean isNativeAccessEnabled = CleanerJava24Linker.class.getModule().isNativeAccessEnabled();
                if (!isNativeAccessEnabled) {
                    throw new UnsupportedOperationException(
                            "Native access (restricted methods) is not enabled for the io.netty.common module.");
                }

                // Second, we need to check the size of a pointer address. For simplicity, we'd like to assume the size
                // of an address is the same as a Java long. So effectively, we're only enabled on 64-bit platforms.
                if (ADDRESS.byteSize() != Long.BYTES) {
                    throw new UnsupportedOperationException(
                            "Linking to malloc and free is only supported on 64-bit platforms.");
                }

                Linker linker = Linker.nativeLinker();
                SymbolLookup defaultLookup = linker.defaultLookup();
                // The default lookup only searches the JDK's libsyslookup and its dependencies, bypassing
                // allocators interposed with LD_PRELOAD. Search the global scope, as a PLT relocation would,
                // so these buffers use the same allocator as Unsafe.allocateMemory.
                MemorySegment mallocPtr = null;
                MemorySegment freePtr = null;
                long rtldDefault = rtldDefault();
                if (rtldDefault != RTLD_DEFAULT_UNSUPPORTED) {
                    try (Arena arena = Arena.ofConfined()) {
                        MethodHandle dlsym = linker.downcallHandle(defaultLookup.findOrThrow("dlsym"),
                                FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, ADDRESS));
                        long mallocAddr = (long) dlsym.invokeExact(rtldDefault, arena.allocateFrom("malloc"));
                        long freeAddr = (long) dlsym.invokeExact(rtldDefault, arena.allocateFrom("free"));
                        if (mallocAddr != 0 && freeAddr != 0) {
                            mallocPtr = MemorySegment.ofAddress(mallocAddr);
                            freePtr = MemorySegment.ofAddress(freeAddr);
                            if (logger != null) {
                                logger.debug("malloc/free resolved via dlsym(RTLD_DEFAULT)");
                            }
                        } else if (logger != null) {
                            logger.debug("dlsym(RTLD_DEFAULT) did not resolve malloc/free, using default lookup");
                        }
                    } catch (Throwable t) {
                        if (logger != null) {
                            logger.debug("Failed to resolve malloc/free via dlsym, using default lookup", t);
                        }
                    }
                }
                // Resolve both symbols together so an interposed malloc is never paired with libc's free.
                if (mallocPtr == null) {
                    mallocPtr = defaultLookup.findOrThrow("malloc");
                    freePtr = defaultLookup.findOrThrow("free");
                }
                mallocMethod = linker.downcallHandle(mallocPtr, FunctionDescriptor.of(JAVA_LONG, JAVA_LONG));
                freeMethod = linker.downcallHandle(freePtr, FunctionDescriptor.ofVoid(JAVA_LONG));

                error = null;
            } catch (Throwable throwable) {
                mallocMethod = null;
                freeMethod = null;
                error = throwable;
            }
        } else {
            mallocMethod = null;
            freeMethod = null;
            error = new UnsupportedOperationException("java.lang.foreign.MemorySegment unavailable");
        }

        if (logger != null) {
            if (error == null) {
                logger.debug("java.nio.ByteBuffer.cleaner(): available");
            } else {
                logger.debug("java.nio.ByteBuffer.cleaner(): unavailable", error);
            }
        }
        INVOKE_MALLOC = mallocMethod;
        INVOKE_FREE = freeMethod;
    }

    static boolean isSupported() {
        return INVOKE_MALLOC != null;
    }

    /**
     * The platform's RTLD_DEFAULT pseudo-handle, or RTLD_DEFAULT_UNSUPPORTED if unknown.
     * Native images keep the default lookup so addresses are not captured in the image heap at build time.
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

    @Override
    public CleanableDirectBuffer allocate(int capacity) {
        return new CleanableDirectBufferImpl(capacity);
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
        final long addr;
        try {
            // Always allocate at least 1 byte, to avoid relying on non-portable behavior
            // of malloc(3) for zero size allocations.
            addr = (long) INVOKE_MALLOC.invokeExact(Math.max(capacity, 1L));
        } catch (Throwable e) {
            throw new Error(e); // Should not happen.
        }
        if (addr == 0) {
            throw new OutOfMemoryError("malloc(2) failed to allocate " + capacity + " bytes");
        }
        return addr;
    }

    static void free(long memoryAddress) {
        try {
            INVOKE_FREE.invokeExact(memoryAddress);
        } catch (Throwable e) {
            throw new Error(e); // Should not happen.
        }
    }

    private static final class CleanableDirectBufferImpl implements CleanableDirectBuffer {
        private final ByteBuffer buffer;
        private final long memoryAddress;

        private CleanableDirectBufferImpl(int capacity) {
            PlatformDependent.incrementMemoryCounter(capacity);
            long addr;
            try {
                addr = malloc(capacity);
            } catch (Throwable e) {
                PlatformDependent.decrementMemoryCounter(capacity);
                throw e;
            }
            try {
                memoryAddress = addr;
                buffer = MemorySegment.ofAddress(addr).reinterpret(capacity).asByteBuffer();
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
