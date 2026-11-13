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
package io.netty.handler.codec.compression;

import io.netty.buffer.ByteBuf;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.zip.Checksum;

/**
 * A {@link ByteBufChecksum} implementation that wraps the JDK's CRC32C
 * ({@code java.util.zip.CRC32C}) using the {@link Checksum} interface.
 * <p>
 * This class includes a workaround for
 * <a href="https://bugs.openjdk.org/browse/JDK-8357145">JDK-8357145</a>,
 * which affects JDK 22 through 24 when updating from a direct {@link ByteBuffer}.
 * Use this class only when running on JDK versions where the workaround is
 * required.
 * <p>
 * <strong>Note:</strong> This class uses reflection to avoid a compile-time
 * dependency on {@code java.util.zip.CRC32C}, which is only available since Java 9.
 * It must only be loaded on Java 9 or later.
 */
final class JdkCrc32cByteBufChecksumWithWorkaround extends ByteBufChecksum {

    private static final Constructor<?> CRC32C_CTOR;
    private static final Method UPDATE_BB;

    static {
        try {
            Class<?> crc32cClass = Class.forName("java.util.zip.CRC32C");
            CRC32C_CTOR = crc32cClass.getConstructor();
            UPDATE_BB = crc32cClass.getMethod("update", ByteBuffer.class);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final Checksum checksum;
    private byte[] scratchBuffer;

    JdkCrc32cByteBufChecksumWithWorkaround() {
        try {
            checksum = (Checksum) CRC32C_CTOR.newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create CRC32C instance", e);
        }
    }

    @Override
    public void update(int b) {
        checksum.update(b);
    }

    @Override
    public void update(byte[] b, int off, int len) {
        checksum.update(b, off, len);
    }

    @Override
    public void update(ByteBuf buf, int off, int len) {
        if (buf.hasArray()) {
            update(buf.array(), buf.arrayOffset() + off, len);
        } else {
            ByteBuffer byteBuffer = CompressionUtil.safeNioBuffer(buf, off, len);
            if (byteBuffer.isDirect()) {
                // Work-around for https://bugs.openjdk.org/browse/JDK-8357145
                if (scratchBuffer == null || scratchBuffer.length < len) {
                    scratchBuffer = new byte[len];
                }
                ByteBuffer copy = ByteBuffer.wrap(scratchBuffer, 0, len);
                copy.put(byteBuffer).flip();
                invokeUpdateBb(copy);
                return;
            }
            invokeUpdateBb(byteBuffer);
        }
    }

    private void invokeUpdateBb(ByteBuffer buf) {
        try {
            UPDATE_BB.invoke(checksum, buf);
        } catch (Exception e) {
            throw new RuntimeException("CRC32C.update(ByteBuffer) failed", e);
        }
    }

    @Override
    public long getValue() {
        return checksum.getValue();
    }

    @Override
    public void reset() {
        checksum.reset();
    }
}
