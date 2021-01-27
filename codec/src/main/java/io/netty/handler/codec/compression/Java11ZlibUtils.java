/*
 * Copyright 2021 The Netty Project
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
package io.netty.handler.codec.compression;

import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.SuppressJava6Requirement;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.ByteBuffer;
import java.util.zip.Inflater;

@SuppressJava6Requirement(reason = "Guarded by version check")
final class Java11ZlibUtils {

    private static final MethodHandle INFLATER_SET_INPUT;
    private static final MethodHandle INFLATER_INFLATE;

    static {
        MethodHandle inflaterSetInput = null;
        MethodHandle inflaterInflate = null;
        try {
            inflaterSetInput = MethodHandles.lookup().findVirtual(Inflater.class, "setInput",
                    MethodType.methodType(void.class, ByteBuffer.class));
            inflaterInflate = MethodHandles.lookup().findVirtual(Inflater.class, "inflate",
                    MethodType.methodType(int.class, ByteBuffer.class));
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
            inflaterSetInput = null;
            inflaterInflate = null;
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            inflaterSetInput = null;
            inflaterInflate = null;
        }

        INFLATER_SET_INPUT = inflaterSetInput;
        INFLATER_INFLATE = inflaterInflate;
    }

    static boolean isSupported() {
        return INFLATER_SET_INPUT != null && INFLATER_INFLATE != null;
    }

    static void setInput(Inflater inflater, ByteBuffer input) {
        try {
            // Use invokeWithArguments as we compile with -target 6
            INFLATER_SET_INPUT.invokeWithArguments(inflater, input);
        } catch (Throwable cause) {
            PlatformDependent.throwException(cause);
        }
    }

    static int inflate(Inflater inflater, ByteBuffer input) {
        try {
            // Use invokeWithArguments as we compile with -target 6
            return (Integer) INFLATER_INFLATE.invokeWithArguments(inflater, input);
        } catch (Throwable cause) {
            PlatformDependent.throwException(cause);
        }
        return -1;
    }

    private Java11ZlibUtils() { }
}
