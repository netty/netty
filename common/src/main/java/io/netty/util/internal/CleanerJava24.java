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
import java.nio.ByteBuffer;

import static java.lang.invoke.MethodType.methodType;

/**
 * Provide a way to clean direct {@link ByteBuffer} instances on Java 24+,
 * where we don't have {@code Unsafe} available, but we have memory segments.
 */
final class CleanerJava24 implements Cleaner {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(CleanerJava24.class);

    private static final MethodHandle INVOKE_ALLOCATOR;

    static {
        MethodHandle method;
        Throwable error;
        if (PlatformDependent0.javaVersion() >= 24) {
            try {
                // Here we compose and construct a MethodHandle that takes an 'int' capacity argument,
                // and produces a 'CleanableDirectBufferImpl' instance.
                // The method handle will create a new shared Arena instance, allocate a MemorySegment from it,
                // convert the MemorySegment to a ByteBuffer, and then pass both the Arena and the ByteBuffer to
                // the CleanableDirectBufferImpl constructor, returning the resulting object.
                //
                // Effectively, we are recreating the following the Java code through MethodHandles alone:
                //
                //    Arena arena = Arena.ofShared();
                //    return new CleanableDirectBufferImpl(
                //              (AutoCloseable) arena,
                //              arena.allocate(size).asByteBuffer());
                //
                // First, we need the types we'll use to set this all up.
                Class<?> arenaCls = Class.forName("java.lang.foreign.Arena");
                Class<?> memsegCls = Class.forName("java.lang.foreign.MemorySegment");
                Class<CleanableDirectBufferImpl> bufCls = CleanableDirectBufferImpl.class;
                // Acquire the private look up, so we can access the private 'CleanableDirectBufferImpl' constructor.
                MethodHandles.Lookup lookup = MethodHandles.lookup();

                // ofShared.type() = ()Arena
                MethodHandle ofShared = lookup.findStatic(arenaCls, "ofShared", methodType(arenaCls));
                // allocate.type() = (Arena,long)MemorySegment
                MethodHandle allocate = lookup.findVirtual(arenaCls, "allocate", methodType(memsegCls, long.class));
                // asByteBuffer.type() = (MemorySegment)ByteBuffer
                MethodHandle asByteBuffer = lookup.findVirtual(memsegCls, "asByteBuffer", methodType(ByteBuffer.class));
                // bufClsCtor.type() = (AutoCloseable,ByteBuffer)CleanableDirectBufferImpl
                MethodHandle bufClsCtor = lookup.findConstructor(bufCls,
                        methodType(void.class, AutoCloseable.class, ByteBuffer.class));
                // The 'allocate' method takes a 'long' capacity but we'll be providing an 'int'.
                // Explicitly cast the 'long' to 'int' so we can use 'invokeExact'.
                // allocateInt.type() = (Arena,int)MemorySegment
                MethodHandle allocateInt = MethodHandles.explicitCastArguments(allocate,
                        methodType(memsegCls, arenaCls, int.class));
                // Use the 'asByteBuffer' method as a filter, to transform the constructor into a method that takes a
                // MemorySegment argument instead of a ByteBuffer argument.
                // ctorArenaMemseg.type() = (Arena,MemorySegment)CleanableDirectBufferImpl
                MethodHandle ctorArenaMemseg = MethodHandles.explicitCastArguments(
                        MethodHandles.filterArguments(bufClsCtor, 1, asByteBuffer),
                        methodType(bufCls, arenaCls, memsegCls));
                // Use the 'allocateInt' method to transform the last MemorySegment argument of the constructor,
                // into an (Arena,int) argument pair.
                // ctorArenaArenaInt.type() = (Arena,Arena,int)CleanableDirectBufferImpl
                MethodHandle ctorArenaArenaInt = MethodHandles.collectArguments(ctorArenaMemseg, 1, allocateInt);
                // Our method now takes two Arena arguments, but we actually only want to pass one. Specifically, it's
                // very important that it's the same arena we use for both allocation and deallocation.
                // We permute the argument array such that the first Arena argument gets passed to both parameters,
                // and the second parameter value gets ignored.
                // ctorArenaNullInt.type() = (Arena,Arena,int)CleanableDirectBufferImpl
                MethodHandle ctorArenaNullInt = MethodHandles.permuteArguments(ctorArenaArenaInt,
                        methodType(bufCls, arenaCls, arenaCls, int.class), 0, 0, 2);
                // With the second Arena parameter value ignored, we can statically bind it to 'null' to effectively
                // drop it from our parameter list.
                // ctorArenaInt.type() = (Arena,int)CleanableDirectBufferImpl
                MethodHandle ctorArenaInt = MethodHandles.insertArguments(ctorArenaNullInt, 1, new Object[]{null});
                // Now we just need to create our Arena instance. We fold the Arena parameter into the 'ofShared'
                // static method, so we effectively bind the argument to the result of calling that method.
                // Since 'ofShared' takes no further parameters, we effectively eliminate the first parameter.
                // This creates our method handle that takes an 'int' and returns a 'CleanableDirectBufferImpl'.
                // ctorInt.type() = (int)CleanableDirectBufferImpl
                method = MethodHandles.foldArguments(ctorArenaInt, ofShared);
                error = null;
            } catch (Throwable throwable) {
                method = null;
                error = throwable;
            }
        } else {
            method = null;
            error = new UnsupportedOperationException("java.lang.foreign.MemorySegment unavailable");
        }
        if (error == null) {
            logger.debug("java.nio.ByteBuffer.cleaner(): available");
        } else {
            logger.debug("java.nio.ByteBuffer.cleaner(): unavailable", error);
        }
        INVOKE_ALLOCATOR = method;
    }

    static boolean isSupported() {
        return INVOKE_ALLOCATOR != null;
    }

    @SuppressWarnings("OverlyStrongTypeCast") // The cast is needed for 'invokeExact' semantics.
    @Override
    public CleanableDirectBuffer allocate(int capacity) {
        try {
            return (CleanableDirectBufferImpl) INVOKE_ALLOCATOR.invokeExact(capacity);
        } catch (RuntimeException e) {
            throw e; // Propagate the runtime exceptions that the Arena would normally throw.
        } catch (Throwable e) {
            throw new IllegalStateException("Unexpected allocation exception", e);
        }
    }

    @Override
    public void freeDirectBuffer(ByteBuffer buffer) {
        throw new UnsupportedOperationException("Cannot clean arbitrary ByteBuffer instances");
    }

    private static final class CleanableDirectBufferImpl implements CleanableDirectBuffer {
        private final AutoCloseable closeable;
        private final ByteBuffer buffer;

        // NOTE: must be at least package-protected to allow calls from the method handles!
        CleanableDirectBufferImpl(AutoCloseable closeable, ByteBuffer buffer) {
            this.closeable = closeable;
            this.buffer = buffer;
        }

        @Override
        public ByteBuffer buffer() {
            return buffer;
        }

        @Override
        public void clean() {
            try {
                closeable.close();
            } catch (RuntimeException e) {
                throw e; // Propagate the runtime exceptions that Arena would normally throw.
            } catch (Exception e) {
                throw new IllegalStateException("Unexpected close exception", e);
            }
        }
    }
}
