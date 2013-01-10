/*
 * Copyright 2013 The Netty Project
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

import sun.misc.Cleaner;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;

// This resist in common because otherwise we would produce a cycle dependency between common and buffer.
public final class DirectByteBufUtil {

    private static final Field CLEANER_FIELD;

    static {
        ByteBuffer direct = ByteBuffer.allocateDirect(1);
        Field cleanerField;
        try {
            cleanerField = direct.getClass().getDeclaredField("cleaner");
            cleanerField.setAccessible(true);
            Cleaner cleaner = (Cleaner) cleanerField.get(direct);
            cleaner.clean();
        } catch (Throwable t) {
            cleanerField = null;
        }
        CLEANER_FIELD = cleanerField;
    }

    /**
     * Try to clean a direct {@link ByteBuffer}.
     *
     * Only try to access this method when {@link DetectionUtil#canFreeDirectBuffer()} returns
     * {@code true}
     *
     */
    public static void freeDirect(ByteBuffer buffer) {
        if (!buffer.isDirect()) {
            return;
        }
        if (CLEANER_FIELD == null) {
            // Doomed to wait for GC.
            return;
        }

        Cleaner cleaner;
        try {
            cleaner = (Cleaner) CLEANER_FIELD.get(buffer);
            cleaner.clean();
        } catch (Throwable t) {
            // Nothing we can do here.
        }
    }

    static boolean canFreeDirect() {
        return CLEANER_FIELD != null;
    }

    private DirectByteBufUtil() {
    }
}
