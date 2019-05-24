/*
 * Copyright 2019 The Netty Project
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
package io.netty.util;

import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.UnstableApi;

import java.nio.ByteBuffer;

@UnstableApi
public final class DirectCleaner {

    private static boolean ALLOW_CUSTOM_CLEANER = SystemPropertyUtil.getBoolean("io.netty.allowCustomCleaner", false);
    private static volatile CustomCleaner customCleaner;

    /**
     * Try to deallocate the specified direct {@link ByteBuffer}. Please note this method does nothing if
     * the current platform does not support this operation and no custom cleaner is provided or if the
     * specified buffer is not a direct buffer.
     */
    public static void freeDirectBuffer(ByteBuffer buffer) {
        // Copied locally to prevent race
        CustomCleaner localCustomCleaner;
        if (!ALLOW_CUSTOM_CLEANER || (localCustomCleaner = DirectCleaner.customCleaner) == null) {
            PlatformDependent.freeDirectBuffer(buffer);
        } else {
            localCustomCleaner.freeDirectMemory(buffer);
        }
    }

    /**
     * Set a {@link CustomCleaner} that will be used to deallocate direct byte buffers. This custom cleaner
     * can only be used if io.netty.allowCustomCleaner is set to true and Netty is not configured to use
     * direct byte buffers without cleaners.
     */
    public static void setCustomCleaner(CustomCleaner cleaner) {
        if (!ALLOW_CUSTOM_CLEANER) {
            throw new UnsupportedOperationException("Cannot set CustomCleaner, io.netty.allowCustomCleaner set to " +
                    "false.");
        }
        if (PlatformDependent.useDirectBufferNoCleaner()) {
            throw new UnsupportedOperationException("Cannot set CustomCleaner because Netty is configured to " +
                    "allocate direct byte buffers without use of Cleaner.");
        }
        customCleaner = cleaner;
    }

    private DirectCleaner() {
    }

    public interface CustomCleaner {

        void freeDirectMemory(ByteBuffer byteBuffer);
    }
}
