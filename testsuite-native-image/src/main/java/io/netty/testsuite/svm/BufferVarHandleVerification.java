/*
 * Copyright 2026 The Netty Project
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
package io.netty.testsuite.svm;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.util.internal.PlatformDependent;

/**
 * Verifies that VarHandle-based buffer access works correctly in native-image environments.
 * This exercises the multi-byte get/set paths (short, int, long in both BE and LE)
 * on heap and direct buffers, SWAR-optimized indexOf, and reference counting.
 */
final class BufferVarHandleVerification {

    private BufferVarHandleVerification() {
    }

    static void verify(ByteBufAllocator alloc) {
        System.out.println("VarHandle verification: hasVarHandle=" + PlatformDependent.hasVarHandle());

        verifyHeapBuffer(alloc);
        verifyDirectBuffer(alloc);
        verifySwarIndexOf();
        verifyRefCnt(alloc);

        System.out.println("VarHandle verification: all checks passed");
    }

    private static void verifyHeapBuffer(ByteBufAllocator alloc) {
        ByteBuf buf = alloc.heapBuffer(64);
        try {
            verifyMultiByteAccess(buf, "heap");
        } finally {
            buf.release();
        }
    }

    private static void verifyDirectBuffer(ByteBufAllocator alloc) {
        ByteBuf buf = alloc.directBuffer(64);
        try {
            verifyMultiByteAccess(buf, "direct");
        } finally {
            buf.release();
        }
    }

    private static void verifyMultiByteAccess(ByteBuf buf, String type) {
        // Short BE
        buf.clear();
        buf.writeShort(0x1234);
        check(buf.getShort(0) == 0x1234, type + " short BE");

        // Short LE
        buf.clear();
        buf.writeShortLE(0x1234);
        check(buf.getShortLE(0) == 0x1234, type + " short LE");

        // Int BE
        buf.clear();
        buf.writeInt(0x12345678);
        check(buf.getInt(0) == 0x12345678, type + " int BE");

        // Int LE
        buf.clear();
        buf.writeIntLE(0x12345678);
        check(buf.getIntLE(0) == 0x12345678, type + " int LE");

        // Long BE
        buf.clear();
        buf.writeLong(0x123456789ABCDEF0L);
        check(buf.getLong(0) == 0x123456789ABCDEF0L, type + " long BE");

        // Long LE
        buf.clear();
        buf.writeLongLE(0x123456789ABCDEF0L);
        check(buf.getLongLE(0) == 0x123456789ABCDEF0L, type + " long LE");
    }

    private static void verifySwarIndexOf() {
        ByteBuf buf = Unpooled.copiedBuffer(new byte[] {
                'H', 'e', 'l', 'l', 'o', ',', ' ', 'W', 'o', 'r', 'l', 'd', '!'
        });
        try {
            int idx = ByteBufUtil.indexOf(buf, buf.readerIndex(), buf.writerIndex(), (byte) 'W');
            check(idx == 7, "SWAR indexOf");

            int notFound = ByteBufUtil.indexOf(buf, buf.readerIndex(), buf.writerIndex(), (byte) 'Z');
            check(notFound == -1, "SWAR indexOf not found");
        } finally {
            buf.release();
        }
    }

    private static void verifyRefCnt(ByteBufAllocator alloc) {
        ByteBuf buf = alloc.buffer(16);
        check(buf.refCnt() == 1, "refCnt initial");

        buf.retain();
        check(buf.refCnt() == 2, "refCnt after retain");

        buf.release();
        check(buf.refCnt() == 1, "refCnt after release");

        buf.release();
        check(buf.refCnt() == 0, "refCnt after final release");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("VarHandle verification failed: " + message);
        }
    }
}
