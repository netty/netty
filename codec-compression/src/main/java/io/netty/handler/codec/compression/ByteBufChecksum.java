/*
 * Copyright 2016 The Netty Project
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
import io.netty.util.ByteProcessor;
import io.netty.util.internal.ObjectUtil;

import java.nio.ByteBuffer;
import java.util.zip.Adler32;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

/**
 * {@link Checksum} implementation which can directly act on a {@link ByteBuf}.
 * <p>
 * Implementations may optimize access patterns depending on if the {@link ByteBuf} is backed by a
 * byte array ({@link ByteBuf#hasArray()} is {@code true}) or not.
 */
abstract class ByteBufChecksum implements Checksum {
    private final ByteProcessor updateProcessor = new ByteProcessor() {
        @Override
        public boolean process(byte value) throws Exception {
            update(value);
            return true;
        }
    };

    static ByteBufChecksum wrapChecksum(Checksum checksum) {
        ObjectUtil.checkNotNull(checksum, "checksum");
        if (checksum instanceof ByteBufChecksum) {
            return (ByteBufChecksum) checksum;
        }
        return new JdkByteBufChecksum(checksum);
    }

    /**
     * @see #update(byte[], int, int)
     */
    public void update(ByteBuf b, int off, int len) {
        if (b.hasArray()) {
            update(b.array(), b.arrayOffset() + off, len);
        } else {
            b.forEachByte(off, len, updateProcessor);
        }
    }

    private static class JdkByteBufChecksum extends ByteBufChecksum {
        protected final Checksum checksum;

        JdkByteBufChecksum(Checksum checksum) {
            this.checksum = checksum;
        }

        @Override
        public void update(int b) {
            checksum.update(b);
        }

        @Override
        public void update(ByteBuf b, int off, int len) {
            if (b.hasArray()) {
                update(b.array(), b.arrayOffset() + off, len);
            } else if (checksum instanceof CRC32) {
                ByteBuffer byteBuffer = CompressionUtil.safeNioBuffer(b, off, len);
                ((CRC32) checksum).update(byteBuffer);
            } else if (checksum instanceof Adler32) {
                ByteBuffer byteBuffer = CompressionUtil.safeNioBuffer(b, off, len);
                ((Adler32) checksum).update(byteBuffer);
            } else {
                super.update(b, off, len);
            }
        }

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
    }
}
