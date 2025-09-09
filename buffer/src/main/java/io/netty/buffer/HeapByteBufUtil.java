/*
 * Copyright 2015 The Netty Project
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
package io.netty.buffer;

import io.netty.util.internal.PlatformDependent;

import java.lang.invoke.VarHandle;

/**
 * Utility class for heap buffers.
 */
final class HeapByteBufUtil {

    static byte getByte(byte[] memory, int index) {
        return memory[index];
    }

    static short getShort(byte[] memory, int index) {
        VarHandle shortBeArrayView = PlatformDependent.shortBeArrayView();
        if (shortBeArrayView != null) {
            return (short) shortBeArrayView.get(memory, index);
        }
        return getShort0(memory, index);
    }

    private static short getShort0(byte[] memory, int index) {
        return (short) (memory[index] << 8 | memory[index + 1] & 0xFF);
    }

    static short getShortLE(byte[] memory, int index) {
        VarHandle shortLeArrayView = PlatformDependent.shortLeArrayView();
        if (shortLeArrayView != null) {
            return (short) shortLeArrayView.get(memory, index);
        }
        return (short) (memory[index] & 0xff | memory[index + 1] << 8);
    }

    static int getUnsignedMedium(byte[] memory, int index) {
        return  (memory[index]     & 0xff) << 16 |
                (memory[index + 1] & 0xff) <<  8 |
                memory[index + 2] & 0xff;
    }

    static int getUnsignedMediumLE(byte[] memory, int index) {
        return  memory[index]     & 0xff         |
                (memory[index + 1] & 0xff) <<  8 |
                (memory[index + 2] & 0xff) << 16;
    }

    static int getInt(byte[] memory, int index) {
        VarHandle intBeArrayView  = PlatformDependent.intBeArrayView();
        if (intBeArrayView != null) {
            return (int) intBeArrayView.get(memory, index);
        }
        return getInt0(memory, index);
    }

    private static int getInt0(byte[] memory, int index) {
        return (memory[index] & 0xFF) << 24 |
               (memory[index + 1] & 0xFF) << 16 |
               (memory[index + 2] & 0xFF) << 8 |
               (memory[index + 3] & 0xFF);
    }

    static int getIntLE(byte[] memory, int index) {
        VarHandle intLeArrayView = PlatformDependent.intLeArrayView();
        if (intLeArrayView != null) {
            return (int) intLeArrayView.get(memory, index);
        }
        return getIntLE0(memory, index);
    }

    private static int getIntLE0(byte[] memory, int index) {
        return (memory[index] & 0xFF) |
               (memory[index + 1] & 0xFF) << 8 |
               (memory[index + 2] & 0xFF) << 16 |
               (memory[index + 3] & 0xFF) << 24;
    }

    static long getLong(byte[] memory, int index) {
        VarHandle longBeArrayView = PlatformDependent.longBeArrayView();
        if (longBeArrayView != null) {
            return (long) longBeArrayView.get(memory, index);
        }
        return getLong0(memory, index);
    }

    private static long getLong0(byte[] memory, int index) {
        return ((long) memory[index] & 0xFF) << 56 |
               ((long) memory[index + 1] & 0xFF) << 48 |
               ((long) memory[index + 2] & 0xFF) << 40 |
               ((long) memory[index + 3] & 0xFF) << 32 |
               ((long) memory[index + 4] & 0xFF) << 24 |
               ((long) memory[index + 5] & 0xFF) << 16 |
               ((long) memory[index + 6] & 0xFF) << 8 |
               ((long) memory[index + 7] & 0xFF);
    }

    static long getLongLE(byte[] memory, int index) {
        VarHandle longLeArrayView = PlatformDependent.longLeArrayView();
        if (longLeArrayView != null) {
            return (long) longLeArrayView.get(memory, index);
        }
        return getLongLE0(memory, index);
    }

    private static long getLongLE0(byte[] memory, int index) {
        return ((long) memory[index] & 0xFF) |
               ((long) memory[index + 1] & 0xFF) << 8 |
               ((long) memory[index + 2] & 0xFF) << 16 |
               ((long) memory[index + 3] & 0xFF) << 24 |
               ((long) memory[index + 4] & 0xFF) << 32 |
               ((long) memory[index + 5] & 0xFF) << 40 |
               ((long) memory[index + 6] & 0xFF) << 48 |
               ((long) memory[index + 7] & 0xFF) << 56;
    }

    static void setByte(byte[] memory, int index, int value) {
        memory[index] = (byte) (value & 0xFF);
    }

    static void setShort(byte[] memory, int index, int value) {
        VarHandle shortBeArrayView = PlatformDependent.shortBeArrayView();
        if (shortBeArrayView != null) {
            shortBeArrayView.set(memory, index, (short) value);
            return;
        }
        memory[index]     = (byte) (value >>> 8);
        memory[index + 1] = (byte) value;
    }

    static void setShortLE(byte[] memory, int index, int value) {
        VarHandle shortLeArrayView = PlatformDependent.shortLeArrayView();
        if (shortLeArrayView != null) {
            shortLeArrayView.set(memory, index, (short) value);
            return;
        }
        memory[index]     = (byte) value;
        memory[index + 1] = (byte) (value >>> 8);
    }

    static void setMedium(byte[] memory, int index, int value) {
        memory[index]     = (byte) (value >>> 16);
        memory[index + 1] = (byte) (value >>> 8);
        memory[index + 2] = (byte) value;
    }

    static void setMediumLE(byte[] memory, int index, int value) {
        memory[index]     = (byte) value;
        memory[index + 1] = (byte) (value >>> 8);
        memory[index + 2] = (byte) (value >>> 16);
    }

    static void setInt(byte[] memory, int index, int value) {
        VarHandle intBeArrayView = PlatformDependent.intBeArrayView();
        if (intBeArrayView != null) {
            intBeArrayView.set(memory, index, value);
            return;
        }
        setInt0(memory, index, value);
    }

    private static void setInt0(byte[] memory, int index, int value) {
        memory[index]     = (byte) (value >>> 24);
        memory[index + 1] = (byte) (value >>> 16);
        memory[index + 2] = (byte) (value >>> 8);
        memory[index + 3] = (byte) value;
    }

    static void setIntLE(byte[] memory, int index, int value) {
        VarHandle intLeArrayView = PlatformDependent.intLeArrayView();
        if (intLeArrayView != null) {
            intLeArrayView.set(memory, index, value);
            return;
        }
        setIntLE0(memory, index, value);
    }

    private static void setIntLE0(byte[] memory, int index, int value) {
        memory[index]     = (byte) value;
        memory[index + 1] = (byte) (value >>> 8);
        memory[index + 2] = (byte) (value >>> 16);
        memory[index + 3] = (byte) (value >>> 24);
    }

    static void setLong(byte[] memory, int index, long value) {
        VarHandle longBeArrayView = PlatformDependent.longBeArrayView();
        if (longBeArrayView != null) {
            longBeArrayView.set(memory, index, value);
            return;
        }
        setLong0(memory, index, value);
    }

    private static void setLong0(byte[] memory, int index, long value) {
        memory[index]     = (byte) (value >>> 56);
        memory[index + 1] = (byte) (value >>> 48);
        memory[index + 2] = (byte) (value >>> 40);
        memory[index + 3] = (byte) (value >>> 32);
        memory[index + 4] = (byte) (value >>> 24);
        memory[index + 5] = (byte) (value >>> 16);
        memory[index + 6] = (byte) (value >>> 8);
        memory[index + 7] = (byte) value;
    }

    static void setLongLE(byte[] memory, int index, long value) {
        VarHandle longLeArrayView = PlatformDependent.longLeArrayView();
        if (longLeArrayView != null) {
            longLeArrayView.set(memory, index, value);
            return;
        }
        setLongLE0(memory, index, value);
    }

    private static void setLongLE0(byte[] memory, int index, long value) {
        memory[index]     = (byte) value;
        memory[index + 1] = (byte) (value >>> 8);
        memory[index + 2] = (byte) (value >>> 16);
        memory[index + 3] = (byte) (value >>> 24);
        memory[index + 4] = (byte) (value >>> 32);
        memory[index + 5] = (byte) (value >>> 40);
        memory[index + 6] = (byte) (value >>> 48);
        memory[index + 7] = (byte) (value >>> 56);
    }

    private HeapByteBufUtil() { }
}
