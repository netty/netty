/*
 * Copyright 2015 The Netty Project
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
package io.netty.buffer;

import io.netty.util.internal.PlatformDependent;

import java.nio.ByteOrder;

/**
 * All operations get and set as {@link ByteOrder#BIG_ENDIAN}.
 */
final class UnsafeByteBufUtil {

    private static final boolean NATIVE_ORDER = ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN;
    private static final boolean UNALIGNED = PlatformDependent.isUnaligned();

    static byte getByte(long address) {
        return PlatformDependent.getByte(address);
    }

    static short getShort(long address) {
        if (UNALIGNED) {
            short v = PlatformDependent.getShort(address);
            return NATIVE_ORDER ? v : Short.reverseBytes(v);
        }
        return (short) (PlatformDependent.getByte(address) << 8 | PlatformDependent.getByte(address + 1) & 0xff);
    }

    static int getUnsignedMedium(long address) {
        if (UNALIGNED) {
            if (NATIVE_ORDER) {
                return (PlatformDependent.getByte(address) & 0xff) |
                        (PlatformDependent.getShort(address + 1) & 0xffff) << 8;
            }
            return (Short.reverseBytes(PlatformDependent.getShort(address)) & 0xffff) << 8 |
                    PlatformDependent.getByte(address + 2) & 0xff;
        }
        return (PlatformDependent.getByte(address) & 0xff) << 16 |
               (PlatformDependent.getByte(address + 1) & 0xff) << 8 |
               PlatformDependent.getByte(address + 2) & 0xff;
    }

    static int getInt(long address) {
        if (UNALIGNED) {
            int v = PlatformDependent.getInt(address);
            return NATIVE_ORDER ? v : Integer.reverseBytes(v);
        }
        return PlatformDependent.getByte(address) << 24 |
               (PlatformDependent.getByte(address + 1) & 0xff) << 16 |
               (PlatformDependent.getByte(address + 2) & 0xff) <<  8 |
               PlatformDependent.getByte(address + 3) & 0xff;
    }

    static long getLong(long address) {
        if (UNALIGNED) {
            long v = PlatformDependent.getLong(address);
            return NATIVE_ORDER ? v : Long.reverseBytes(v);
        }
        return (long) PlatformDependent.getByte(address) << 56 |
               ((long) PlatformDependent.getByte(address + 1) & 0xff) << 48 |
               ((long) PlatformDependent.getByte(address + 2) & 0xff) << 40 |
               ((long) PlatformDependent.getByte(address + 3) & 0xff) << 32 |
               ((long) PlatformDependent.getByte(address + 4) & 0xff) << 24 |
               ((long) PlatformDependent.getByte(address + 5) & 0xff) << 16 |
               ((long) PlatformDependent.getByte(address + 6) & 0xff) <<  8 |
               (long) PlatformDependent.getByte(address + 7) & 0xff;
    }

    static void setByte(long address, int value) {
        PlatformDependent.putByte(address, (byte) value);
    }

    static void setShort(long address, int value) {
        if (UNALIGNED) {
            PlatformDependent.putShort(address, NATIVE_ORDER ? (short) value : Short.reverseBytes((short) value));
        } else {
            PlatformDependent.putByte(address, (byte) (value >>> 8));
            PlatformDependent.putByte(address + 1, (byte) value);
        }
    }

    static void setMedium(long address, int value) {
        if (UNALIGNED) {
            if (NATIVE_ORDER) {
                PlatformDependent.putByte(address, (byte) value);
                PlatformDependent.putShort(address + 1, (short) (value >>> 8));
            } else {
                PlatformDependent.putShort(address, Short.reverseBytes((short) (value >>> 8)));
                PlatformDependent.putByte(address + 2, (byte) value);
            }
        } else {
            PlatformDependent.putByte(address, (byte) (value >>> 16));
            PlatformDependent.putByte(address + 1, (byte) (value >>> 8));
            PlatformDependent.putByte(address + 2, (byte) value);
        }
    }

    static void setInt(long address, int value) {
        if (UNALIGNED) {
            PlatformDependent.putInt(address, NATIVE_ORDER ? value : Integer.reverseBytes(value));
        } else {
            PlatformDependent.putByte(address, (byte) (value >>> 24));
            PlatformDependent.putByte(address + 1, (byte) (value >>> 16));
            PlatformDependent.putByte(address + 2, (byte) (value >>> 8));
            PlatformDependent.putByte(address + 3, (byte) value);
        }
    }

    static void setLong(long address, long value) {
        if (UNALIGNED) {
            PlatformDependent.putLong(address, NATIVE_ORDER ? value : Long.reverseBytes(value));
        } else {
            PlatformDependent.putByte(address, (byte) (value >>> 56));
            PlatformDependent.putByte(address + 1, (byte) (value >>> 48));
            PlatformDependent.putByte(address + 2, (byte) (value >>> 40));
            PlatformDependent.putByte(address + 3, (byte) (value >>> 32));
            PlatformDependent.putByte(address + 4, (byte) (value >>> 24));
            PlatformDependent.putByte(address + 5, (byte) (value >>> 16));
            PlatformDependent.putByte(address + 6, (byte) (value >>> 8));
            PlatformDependent.putByte(address + 7, (byte) value);
        }
    }

    private UnsafeByteBufUtil() { }
}
