/*
* Copyright 2014 The Netty Project
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
package io.netty.util.internal;

import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;


/**
 * Allows to free direct {@link ByteBuffer} by using Cleaner. This is encapsulated in an extra class to be able
 * to use {@link PlatformDependent0} on Android without problems.
 *
 * For more details see <a href="https://github.com/netty/netty/issues/2604">#2604</a>.
 */
final class Cleaner0 {
    private static final long CLEANER_FIELD_OFFSET;
    private static final Method CLEAN_METHOD;
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(Cleaner0.class);

    static {
        ByteBuffer direct = ByteBuffer.allocateDirect(1);
        Field cleanerField;
        long fieldOffset = -1;
        Method clean = null;
        if (PlatformDependent0.hasUnsafe()) {
            try {
                cleanerField = direct.getClass().getDeclaredField("cleaner");
                cleanerField.setAccessible(true);
                fieldOffset = PlatformDependent0.objectFieldOffset(cleanerField);
                Object cleaner = cleanerField.get(direct);
                try {
                    // Cleaner implements Runnable from JDK9 onwards.
                    Runnable runnable = (Runnable) cleaner;
                    clean = Runnable.class.getDeclaredMethod("run");
                } catch (ClassCastException ignored) {
                    clean = cleaner.getClass().getDeclaredMethod("clean");
                }
                clean.invoke(cleaner);
            } catch (Throwable t) {
                // We don't have ByteBuffer.cleaner().
                fieldOffset = -1;
                clean = null;
            }
        }
        logger.debug("java.nio.ByteBuffer.cleaner(): {}", fieldOffset != -1? "available" : "unavailable");
        CLEANER_FIELD_OFFSET = fieldOffset;
        CLEAN_METHOD = clean;

        // free buffer if possible
        freeDirectBuffer(direct);
    }

    static void freeDirectBuffer(ByteBuffer buffer) {
        if (CLEANER_FIELD_OFFSET == -1 || !buffer.isDirect()) {
            return;
        }
        assert CLEAN_METHOD != null : "CLEANER_FIELD_OFFSET != -1 implies CLEAN_METHOD != null";
        try {
            Object cleaner = PlatformDependent0.getObject(buffer, CLEANER_FIELD_OFFSET);
            if (cleaner != null) {
                CLEAN_METHOD.invoke(cleaner);
            }
        } catch (Throwable t) {
            // Nothing we can do here.
        }
    }

    private Cleaner0() { }
}
