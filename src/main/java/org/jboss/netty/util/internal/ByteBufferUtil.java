/*
 * Copyright 2012 The Netty Project
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
package org.jboss.netty.util.internal;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;

/**
 * This is fork of ElasticSearch's ByteBufferAllocator.Cleaner class
 */
public final class ByteBufferUtil {
    private static final boolean CLEAN_SUPPORTED;
    private static final Method directBufferCleaner;
    private static final Method directBufferCleanerClean;

    static {
        Method directBufferCleanerX = null;
        Method directBufferCleanerCleanX = null;
        boolean v;
        try {
            ByteBuffer direct = ByteBuffer.allocateDirect(1);
            directBufferCleanerX = direct.getClass().getMethod("cleaner");
            directBufferCleanerX.setAccessible(true);
            Object cleaner = directBufferCleanerX.invoke(direct);
            try {
                Runnable runnable = (Runnable) cleaner;
                directBufferCleanerCleanX = Runnable.class.getDeclaredMethod("run");
            } catch (ClassCastException ignored) {
                directBufferCleanerCleanX = cleaner.getClass().getMethod("clean");
            }
            directBufferCleanerCleanX.invoke(cleaner);
            v = true;
        } catch (Exception e) {
            v = false;
        }
        CLEAN_SUPPORTED = v;
        directBufferCleaner = directBufferCleanerX;
        directBufferCleanerClean = directBufferCleanerCleanX;
    }

    /**
     * Destroy the given {@link ByteBuffer} if possible
     */
    public static void destroy(ByteBuffer buffer) {
        if (CLEAN_SUPPORTED && buffer.isDirect()) {
            try {
                Object cleaner = directBufferCleaner.invoke(buffer);
                directBufferCleanerClean.invoke(cleaner);
            } catch (Exception e) {
                // silently ignore exception
            }
        }
    }

    private ByteBufferUtil() {
        // Utility class
    }
}
