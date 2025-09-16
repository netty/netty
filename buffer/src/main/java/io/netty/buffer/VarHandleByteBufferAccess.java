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
package io.netty.buffer;

import io.netty.util.internal.PlatformDependent;

import java.nio.ByteBuffer;

/**
 * Centralizes all ByteBuffer VarHandle get/set calls so classes like UnpooledDirectByteBuf
 * don't directly reference signature-polymorphic methods. This allows avoiding class verification
 * failures on older Android runtimes by not loading this class when VarHandle is disabled.
 *
 * Methods here must only be called when PlatformDependent.hasVarHandle() is true.
 */
final class VarHandleByteBufferAccess {

    private VarHandleByteBufferAccess() {
    }

    // short (big endian)
    static short getShortBE(ByteBuffer buffer, int index) {
        //noinspection DataFlowIssue
        return (short) PlatformDependent.shortBeByteBufferView().get(buffer, index);
    }

    static void setShortBE(ByteBuffer buffer, int index, int value) {
        //noinspection DataFlowIssue
        PlatformDependent.shortBeByteBufferView().set(buffer, index, (short) value);
    }

    // short (little endian)
    static short getShortLE(ByteBuffer buffer, int index) {
        //noinspection DataFlowIssue
        return (short) PlatformDependent.shortLeByteBufferView().get(buffer, index);
    }

    static void setShortLE(ByteBuffer buffer, int index, int value) {
        //noinspection DataFlowIssue
        PlatformDependent.shortLeByteBufferView().set(buffer, index, (short) value);
    }

    // int (big endian)
    static int getIntBE(ByteBuffer buffer, int index) {
        //noinspection DataFlowIssue
        return (int) PlatformDependent.intBeByteBufferView().get(buffer, index);
    }

    static void setIntBE(ByteBuffer buffer, int index, int value) {
        //noinspection DataFlowIssue
        PlatformDependent.intBeByteBufferView().set(buffer, index, value);
    }

    // int (little endian)
    static int getIntLE(ByteBuffer buffer, int index) {
        //noinspection DataFlowIssue
        return (int) PlatformDependent.intLeByteBufferView().get(buffer, index);
    }

    static void setIntLE(ByteBuffer buffer, int index, int value) {
        //noinspection DataFlowIssue
        PlatformDependent.intLeByteBufferView().set(buffer, index, value);
    }

    // long (big endian)
    static long getLongBE(ByteBuffer buffer, int index) {
        //noinspection DataFlowIssue
        return (long) PlatformDependent.longBeByteBufferView().get(buffer, index);
    }

    static void setLongBE(ByteBuffer buffer, int index, long value) {
        //noinspection DataFlowIssue
        PlatformDependent.longBeByteBufferView().set(buffer, index, value);
    }

    // long (little endian)
    static long getLongLE(ByteBuffer buffer, int index) {
        //noinspection DataFlowIssue
        return (long) PlatformDependent.longLeByteBufferView().get(buffer, index);
    }

    static void setLongLE(ByteBuffer buffer, int index, long value) {
        //noinspection DataFlowIssue
        PlatformDependent.longLeByteBufferView().set(buffer, index, value);
    }
}
