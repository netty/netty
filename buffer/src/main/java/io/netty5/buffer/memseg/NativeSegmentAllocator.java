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
 *
 */

package io.netty5.buffer.memseg;

import io.netty5.util.internal.SystemPropertyUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.NoSuchElementException;

final class NativeSegmentAllocator implements DirectSegmentAllocator {
    private static final Logger logger = LoggerFactory.getLogger(NativeSegmentAllocator.class);

    private static final boolean AVAILABLE;
    private static final MethodHandle MALLOC;
    private static final MethodHandle FREE;

    static {
        final boolean explicitlyDisabled = explicitlyDisabled();
        if (explicitlyDisabled) {
            AVAILABLE = false;
            MALLOC = null;
            FREE = null;
        } else {
            boolean available = false;
            MethodHandle malloc = null;
            MethodHandle free = null;

            try {
                final Linker linker = Linker.nativeLinker();
                final SymbolLookup symbolLookup = linker.defaultLookup();

                final MemorySegment mallocAddress = symbolLookup.find("malloc").orElseThrow(
                        () -> new NoSuchElementException("malloc symbol not found"));
                malloc = linker.downcallHandle(mallocAddress,
                                               FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

                final MemorySegment freeAddress = symbolLookup.find("free").orElseThrow(
                        () -> new NoSuchElementException("free symbol not found"));
                free = linker.downcallHandle(freeAddress, FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

                available = true;
                logger.debug("direct malloc/free: available");
            } catch (Throwable t) {
                available = false;
                if (logger.isTraceEnabled()) {
                    logger.debug("direct malloc/free: unavailable", t);
                } else {
                    logger.debug("direct malloc/free: unavailable: {}", t.getMessage());
                }
            }

            AVAILABLE = available;
            MALLOC = malloc;
            FREE = free;
        }
    }

    /**
     * Check if native methods are explicitly disabled, either by providing a system property or by
     * disabling native access for the module.
     * @return If native segment allocatiuon is explicitly disabled.
     */
    private static boolean explicitlyDisabled() {
        final boolean noMalloc = SystemPropertyUtil.getBoolean("io.netty5.memseg.noMalloc", false);
        logger.debug("-Dio.netty5.memseg.noMalloc: {}", noMalloc);
        if (noMalloc) {
            logger.debug("direct malloc/free: unavailable (-Dio.netty5.memseg.noMalloc)");
            return true;
        }

        final Module module = NativeSegmentAllocator.class.getModule();
        final boolean nativeAccessEnabled = module.isNativeAccessEnabled();
        logger.debug("--enable-native-access for {}: {}", module, nativeAccessEnabled);
        if (!nativeAccessEnabled) {
            logger.debug("direct malloc/free: unavailable (native access not enabled for {})", module);
            return true;
        }

        return false;
    }

    /**
     * Returns {@code true} if this allocation method is available.
     */
    public static boolean isAvailable() {
        return AVAILABLE;
    }

    @Override
    public SegmentHolder allocate(long byteSize) {
        final MemorySegment segment;
        try {
            segment = (MemorySegment) MALLOC.invokeExact(byteSize);
        } catch (Throwable throwable) {
            throw new Error(throwable); // Should not happen
        }
        if (segment.address() == 0L) {
            throw new OutOfMemoryError("malloc(2) failed to allocate " + byteSize + " bytes");
        }
        final MemorySegment scaledSegment = segment.reinterpret(byteSize);
        return new NativeSegmentHolder(scaledSegment);
    }

    private record NativeSegmentHolder(MemorySegment segment) implements SegmentHolder {
        @Override
        public void free() {
            try {
                FREE.invokeExact(segment);
            } catch (Throwable throwable) {
                throw new Error(throwable); // Should not happen
            }
        }
    }
}
