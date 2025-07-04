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

import static java.lang.invoke.MethodType.methodType;

public class CleanerJava24Linker implements Cleaner {
    private static final InternalLogger logger;

    private static final MethodHandle INVOKE_MALLOC;
    private static final MethodHandle INVOKE_CREATE_BYTEBUFFER;
    private static final MethodHandle INVOKE_FREE;

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
                //        MemorySegment mallocPtr = Linker.nativeLinker().defaultLookup().find("malloc").get();
                //        MethodHandle malloc = Linker.nativeLinker().downcallHandle(
                //                mallocPtr, FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));
                //
                //        MemorySegment freePtr = Linker.nativeLinker().defaultLookup().find("free").get();
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

                MethodHandle nativeLinker = lookup.findStatic(linkerCls, "nativeLinker", methodType(linkerCls));
                MethodHandle defaultLookupStatic = MethodHandles.foldArguments(
                        lookup.findVirtual(linkerCls, "defaultLookup", methodType(symbolLookupCls)),
                        nativeLinker);
                MethodHandle downcallHandleStatic = MethodHandles.foldArguments(
                        lookup.findVirtual(linkerCls, "downcallHandle",
                                methodType(MethodHandle.class, memSegCls, funcDescCls, linkerOptionArrayCls)),
                        nativeLinker);
                MethodHandle findSymbol = MethodHandles.foldArguments(
                        lookup.findVirtual(symbolLookupCls, "findOrThrow", methodType(memSegCls, String.class)),
                        defaultLookupStatic);

                // Constructing the malloc (long)long handle
                Object longLayout = lookup.findStaticGetter(valueLayoutCls, "JAVA_LONG", ofLongValueLayoutCls).invoke();
                Object layoutArray = Array.newInstance(memoryLayoutCls, 1);
                Array.set(layoutArray, 0, longLayout);
                MethodHandle mallocFuncDesc = MethodHandles.insertArguments(
                        lookup.findStatic(funcDescCls, "of",
                                methodType(funcDescCls, memoryLayoutCls, memoryLayoutArrayCls)),
                        0, longLayout, layoutArray);
                MethodHandle mallocLinker = MethodHandles.foldArguments(
                        MethodHandles.foldArguments(downcallHandleStatic,
                                MethodHandles.foldArguments(findSymbol,
                                        MethodHandles.constant(String.class, "malloc"))),
                        mallocFuncDesc);
                mallocMethod = (MethodHandle) mallocLinker.invoke(Array.newInstance(linkerOptionCls, 0));

                // Constructing the free (long)void handle
                MethodHandle freeFuncDesc = MethodHandles.insertArguments(
                        lookup.findStatic(funcDescCls, "ofVoid",
                                methodType(funcDescCls, memoryLayoutArrayCls)),
                        0, layoutArray);
                MethodHandle freeLinker = MethodHandles.foldArguments(
                        MethodHandles.foldArguments(downcallHandleStatic,
                                MethodHandles.foldArguments(findSymbol,
                                        MethodHandles.constant(String.class, "free"))),
                        freeFuncDesc);
                freeMethod = (MethodHandle) freeLinker.invoke(Array.newInstance(linkerOptionCls, 0));

                // Constructing the wrapper (long, long)ByteBuffer handle
                MethodHandle ofAddress = lookup.findStatic(memSegCls, "ofAddress", methodType(memSegCls, long.class));
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

    @Override
    public CleanableDirectBuffer allocate(int capacity) {
        return new CleanableDirectBufferImpl(capacity);
    }

    @Override
    public void freeDirectBuffer(ByteBuffer buffer) {
        throw new UnsupportedOperationException("Cannot clean arbitrary ByteBuffer instances");
    }

    static long malloc(int capacity) {
        final long addr;
        try {
            addr = (long) INVOKE_MALLOC.invokeExact((long) capacity);
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
            long addr = malloc(capacity);
            try {
                memoryAddress = addr;
                buffer = (ByteBuffer) INVOKE_CREATE_BYTEBUFFER.invokeExact(addr, (long) capacity);
            } catch (Throwable throwable) {
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
            free(memoryAddress);
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
