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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.util.Locale;

import static java.lang.invoke.MethodType.methodType;

public class CleanerJava24Linker implements Cleaner {
    private static final InternalLogger logger;

    private static final MethodHandle INVOKE_MALLOC;
    private static final MethodHandle INVOKE_CREATE_BYTEBUFFER;
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
        MethodHandle wrapMethod;
        MethodHandle freeMethod;
        Throwable error;

        if (suitableJavaVersion) {
            try {
                // First, we need to check if we have access to "restricted" methods through the Java Module system.
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
                Class<?> memoryLayoutCls = Class.forName("java.lang.foreign.MemoryLayout");
                Class<?> memoryLayoutArrayCls = Class.forName("[Ljava.lang.foreign.MemoryLayout;");
                Class<?> valueLayoutCls = Class.forName("java.lang.foreign.ValueLayout");
                Class<?> valueLayoutAddressCls = Class.forName("java.lang.foreign.AddressLayout");
                MethodHandle addressLayoutGetter = lookup.findStaticGetter(
                        valueLayoutCls, "ADDRESS", valueLayoutAddressCls);
                MethodHandle byteSize = lookup.findVirtual(valueLayoutAddressCls, "byteSize", methodType(long.class));
                MethodHandle byteSizeOfAddress = MethodHandles.foldArguments(byteSize, addressLayoutGetter);
                long addressSize = (long) byteSizeOfAddress.invokeExact();
                if (addressSize != Long.BYTES) {
                    throw new UnsupportedOperationException(
                            "Linking to malloc and free is only supported on 64-bit platforms.");
                }

                // Finally, we create three method handles, for malloc, free, and for wrapping an address in a
                // ByteBuffer. Effectively, we need the equivalent of these three code snippets:
                //
                //        MemorySegment mallocPtr = dlsym(RTLD_DEFAULT, "malloc"); // see 'dlsym' below
                //        MethodHandle malloc = Linker.nativeLinker().downcallHandle(
                //                mallocPtr, FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));
                //
                //        MemorySegment freePtr = dlsym(RTLD_DEFAULT, "free"); // see 'dlsym' below
                //        MethodHandle free = Linker.nativeLinker().downcallHandle(
                //                freePtr, FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
                //
                //        ByteBuffer byteBuffer = MemorySegment.ofAddress(addr).asByteBuffer();
                Class<?> ofLongValueLayoutCls = Class.forName("java.lang.foreign.ValueLayout$OfLong");
                Class<?> linkerCls = Class.forName("java.lang.foreign.Linker");
                Class<?> linkerOptionCls = Class.forName("java.lang.foreign.Linker$Option");
                Class<?> linkerOptionArrayCls = Class.forName("[Ljava.lang.foreign.Linker$Option;");
                Class<?> symbolLookupCls = Class.forName("java.lang.foreign.SymbolLookup");
                Class<?> memSegCls = Class.forName("java.lang.foreign.MemorySegment");
                Class<?> funcDescCls = Class.forName("java.lang.foreign.FunctionDescriptor");

                Object linker = lookup.findStatic(linkerCls, "nativeLinker", methodType(linkerCls)).invoke();
                Object defaultLookup = lookup.findVirtual(linkerCls, "defaultLookup", methodType(symbolLookupCls))
                        .invoke(linker);
                MethodHandle downcallHandle = lookup.findVirtual(linkerCls, "downcallHandle",
                                methodType(MethodHandle.class, memSegCls, funcDescCls, linkerOptionArrayCls))
                        .bindTo(linker);
                MethodHandle findOrThrow = lookup.findVirtual(symbolLookupCls, "findOrThrow",
                        methodType(memSegCls, String.class)).bindTo(defaultLookup);
                MethodHandle ofAddress = lookup.findStatic(memSegCls, "ofAddress", methodType(memSegCls, long.class));
                Object noOptions = Array.newInstance(linkerOptionCls, 0);

                Object longLayout = lookup.findStaticGetter(valueLayoutCls, "JAVA_LONG", ofLongValueLayoutCls).invoke();
                // These are varargs methods; use fixed arity so that invoke() passes our layout arrays through
                // instead of collecting them into a new array.
                MethodHandle funcDescOf = lookup.findStatic(funcDescCls, "of",
                        methodType(funcDescCls, memoryLayoutCls, memoryLayoutArrayCls)).asFixedArity();
                MethodHandle funcDescOfVoid = lookup.findStatic(funcDescCls, "ofVoid",
                        methodType(funcDescCls, memoryLayoutArrayCls)).asFixedArity();

                // The default lookup resolves symbols from the JDK's own libsyslookup and its direct dependencies,
                // so on Linux and macOS it returns the libc implementation of malloc and free even when the
                // process has interposed another allocator (e.g. jemalloc via LD_PRELOAD). Every other way Netty
                // allocates native memory, Unsafe.allocateMemory included, reaches malloc through the dynamic
                // linker and does honor the interposition. To stay consistent with them, resolve the symbols
                // through dlsym(RTLD_DEFAULT, ...), which searches the global scope in load order, exactly like a
                // PLT relocation would. If anything about that fails, fall back to the default lookup.
                Object mallocPtr = null;
                Object freePtr = null;
                long rtldDefault = rtldDefault();
                if (rtldDefault != RTLD_DEFAULT_UNSUPPORTED) {
                    try {
                        // MethodHandle dlsym = linker.downcallHandle(defaultLookup.findOrThrow("dlsym"),
                        //         FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, ADDRESS));
                        Object dlsymLayouts = Array.newInstance(memoryLayoutCls, 2);
                        Array.set(dlsymLayouts, 0, longLayout);
                        Array.set(dlsymLayouts, 1, addressLayoutGetter.invoke());
                        MethodHandle dlsym = (MethodHandle) downcallHandle.invoke(
                                findOrThrow.invoke("dlsym"), funcDescOf.invoke(longLayout, dlsymLayouts), noOptions);

                        // The symbol names must be passed as C strings. An automatic arena keeps them alive for as
                        // long as the segments are reachable, which is just the duration of the two calls.
                        Class<?> arenaCls = Class.forName("java.lang.foreign.Arena");
                        Object arena = lookup.findStatic(arenaCls, "ofAuto", methodType(arenaCls)).invoke();
                        MethodHandle allocateFrom = lookup.findVirtual(arenaCls, "allocateFrom",
                                methodType(memSegCls, String.class)).bindTo(arena);

                        long mallocAddr = (long) dlsym.invoke(rtldDefault, allocateFrom.invoke("malloc"));
                        long freeAddr = (long) dlsym.invoke(rtldDefault, allocateFrom.invoke("free"));
                        if (mallocAddr != 0 && freeAddr != 0) {
                            mallocPtr = ofAddress.invoke(mallocAddr);
                            freePtr = ofAddress.invoke(freeAddr);
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
                if (mallocPtr == null) {
                    mallocPtr = findOrThrow.invoke("malloc");
                    freePtr = findOrThrow.invoke("free");
                }

                // Constructing the malloc (long)long handle
                Object layoutArray = Array.newInstance(memoryLayoutCls, 1);
                Array.set(layoutArray, 0, longLayout);
                mallocMethod = (MethodHandle) downcallHandle.invoke(
                        mallocPtr, funcDescOf.invoke(longLayout, layoutArray), noOptions);

                // Constructing the free (long)void handle
                freeMethod = (MethodHandle) downcallHandle.invoke(
                        freePtr, funcDescOfVoid.invoke(layoutArray), noOptions);

                // Constructing the wrapper (long, long)ByteBuffer handle
                MethodHandle reinterpret = lookup.findVirtual(memSegCls, "reinterpret",
                        methodType(memSegCls, long.class));
                MethodHandle asByteBuffer = lookup.findVirtual(memSegCls, "asByteBuffer", methodType(ByteBuffer.class));
                wrapMethod = MethodHandles.filterReturnValue(
                        MethodHandles.filterArguments(reinterpret, 0, ofAddress),
                        asByteBuffer);

                error = null;
            } catch (Throwable throwable) {
                mallocMethod = null;
                wrapMethod = null;
                freeMethod = null;
                error = throwable;
            }
        } else {
            mallocMethod = null;
            wrapMethod = null;
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
        INVOKE_CREATE_BYTEBUFFER = wrapMethod;
        INVOKE_FREE = freeMethod;
    }

    static boolean isSupported() {
        return INVOKE_MALLOC != null;
    }

    /**
     * The value of the {@code RTLD_DEFAULT} pseudo-handle for {@code dlsym(3)} on this platform, or
     * {@link #RTLD_DEFAULT_UNSUPPORTED} if we do not know it. The value is not standardised: glibc and musl use
     * {@code (void*) 0}, while macOS and the BSDs use {@code (void*) -2}. Windows has no {@code dlsym}, and a native
     * image must keep using the default lookup because the resolved addresses would otherwise be captured in the
     * image heap at build time.
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
                buffer = (ByteBuffer) INVOKE_CREATE_BYTEBUFFER.invokeExact(addr, (long) capacity);
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
