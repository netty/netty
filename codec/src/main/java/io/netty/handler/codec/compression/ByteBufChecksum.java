/*
 * Copyright 2016 The Netty Project
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
package io.netty.handler.codec.compression;

import io.netty.buffer.ByteBuf;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.PlatformDependent;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.zip.Adler32;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

/**
 * {@link Checksum} implementation which can directly act on a {@link ByteBuf}.
 *
 * Implementations may optimize access patterns depending on if the {@link ByteBuf} is backed by a
 * byte array ({@link ByteBuf#hasArray()} is {@code true}) or not.
 */
abstract class ByteBufChecksum implements Checksum {
    private static final Method ADLER32_UPDATE_METHOD;
    private static final Method CRC32_UPDATE_METHOD;

    static {
        // See if we can use fast-path when using ByteBuf that is not heap based as Adler32 and CRC32 added support
        // for update(ByteBuffer) in JDK8.
        ADLER32_UPDATE_METHOD = updateByteBuffer(new Adler32());
        CRC32_UPDATE_METHOD = updateByteBuffer(new CRC32());
    }

    private static Method updateByteBuffer(Checksum checksum) {
        if (PlatformDependent.javaVersion() >= 8) {
            try {
                Method method = checksum.getClass().getDeclaredMethod("update", ByteBuffer.class);
                method.invoke(method, ByteBuffer.allocate(1));
                return method;
            } catch (Throwable ignore) {
                return null;
            }
        }
        return null;
    }

    protected Checksum checksum;

    private ByteBufChecksum(Checksum checksum) {
        this.checksum = checksum;
    }

    @Override
    public void update(int b) {
        checksum.update(b);
    }

    /**
     * @see {@link #update(byte[], int, int)}.
     */
    abstract void update(ByteBuf b, int off, int len);

    /**
     * Returns {@code true} if {@link ByteBuffer} is supported without memory copy.
     */
    abstract boolean isSupportingByteBuffer();

    @Override
    public void update(byte[] b, int off, int len) {
        checksum.update(b, off, len);
    }

    @Override
    public long getValue() {
        return checksum.getValue();
    }

    @Override
    public void reset() {
        checksum.reset();
    }

    static ByteBufChecksum wrapChecksum(Checksum checksum) {
        ObjectUtil.checkNotNull(checksum, "checksum");
        if (checksum instanceof Adler32 && ADLER32_UPDATE_METHOD != null) {
            return new ReflectiveByteBufChecksum(checksum, ADLER32_UPDATE_METHOD);
        }
        if (checksum instanceof CRC32 && CRC32_UPDATE_METHOD != null) {
            return new ReflectiveByteBufChecksum(checksum, CRC32_UPDATE_METHOD);
        }
        return new SlowByteBufChecksum(checksum);
    }

    private static final class ReflectiveByteBufChecksum extends ByteBufChecksum {
        private final Method method;

        ReflectiveByteBufChecksum(Checksum checksum, Method method) {
            super(checksum);
            this.method = method;
        }

        @Override
        void update(ByteBuf b, int off, int len) {
            if (b.hasArray()) {
                update(b.array(), b.arrayOffset() + off, len);
            } else {
                try {
                    method.invoke(checksum, CompressionUtil.safeNioBuffer(b));
                } catch (Throwable cause) {
                    throw new Error();
                }
            }
        }

        @Override
        boolean isSupportingByteBuffer() {
            return true;
        }
    }

    private static final class SlowByteBufChecksum extends ByteBufChecksum {

        SlowByteBufChecksum(Checksum checksum) {
            super(checksum);
        }

        @Override
        void update(ByteBuf b, int off, int len) {
            if (b.hasArray()) {
                update(b.array(), b.arrayOffset() + off, len);
            } else {
                ByteBuf heapBuffer = b.alloc().heapBuffer(len);
                try {
                    heapBuffer.writeBytes(b, off, len);
                    update(heapBuffer.array(), heapBuffer.arrayOffset(), len);
                } finally {
                    heapBuffer.release();
                }
            }
        }

        @Override
        boolean isSupportingByteBuffer() {
            return false;
        }
    }
}
