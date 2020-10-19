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
package io.netty.channel.unix;

import io.netty.buffer.ByteBufAllocator;
import io.netty.util.internal.ObjectUtil;
import java.nio.channels.WritableByteChannel;

public abstract class SocketWritableByteChannel implements WritableByteChannel {
    private final FileDescriptor fd;

    protected SocketWritableByteChannel(FileDescriptor fd) {
        this.fd = ObjectUtil.checkNotNull(fd, "fd");
    }

    @Override
    public final int write(java.nio.ByteBuffer src) throws java.io.IOException {
        final int written;
        int position = src.position();
        int limit = src.limit();
        if (src.isDirect()) {
            written = fd.write(src, position, src.limit());
        } else {
            final int readableBytes = limit - position;
            io.netty.buffer.ByteBuf buffer = null;
            try {
                if (readableBytes == 0) {
                    buffer = io.netty.buffer.Unpooled.EMPTY_BUFFER;
                } else {
                    final ByteBufAllocator alloc = alloc();
                    if (alloc.isDirectBufferPooled()) {
                        buffer = alloc.directBuffer(readableBytes);
                    } else {
                        buffer = io.netty.buffer.ByteBufUtil.threadLocalDirectBuffer();
                        if (buffer == null) {
                            buffer = io.netty.buffer.Unpooled.directBuffer(readableBytes);
                        }
                    }
                }
                buffer.writeBytes(src.duplicate());
                java.nio.ByteBuffer nioBuffer = buffer.internalNioBuffer(buffer.readerIndex(), readableBytes);
                written = fd.write(nioBuffer, nioBuffer.position(), nioBuffer.limit());
            } finally {
                if (buffer != null) {
                    buffer.release();
                }
            }
        }
        if (written > 0) {
            src.position(position + written);
        }
        return written;
    }

    @Override
    public final boolean isOpen() {
        return fd.isOpen();
    }

    @Override
    public final void close() throws java.io.IOException {
        fd.close();
    }

    protected abstract ByteBufAllocator alloc();
}
