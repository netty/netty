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
package io.netty5.channel.unix;

import io.netty5.buffer.BufferUtil;
import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.buffer.api.DefaultBufferAllocators;
import io.netty5.buffer.api.Resource;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

import static java.util.Objects.requireNonNull;

public abstract class SocketWritableByteChannel implements WritableByteChannel {
    private final FileDescriptor fd;

    protected SocketWritableByteChannel(FileDescriptor fd) {
        this.fd = requireNonNull(fd, "fd");
    }

    @Override
    public final int write(ByteBuffer src) throws IOException {
        final int written;
        int position = src.position();
        int limit = src.limit();
        if (src.isDirect()) {
            written = fd.write(src, position, src.limit());
        } else {
            final int readableBytes = limit - position;
            final BufferAllocator alloc = alloc();
            Buffer buffer = null;
            try {
                if (alloc.isPooling() && alloc.getAllocationType().isDirect()) {
                    buffer = alloc.allocate(readableBytes);
                } else {
                    buffer = BufferUtil.threadLocalDirectBuffer();
                    if (buffer == null) {
                        buffer = DefaultBufferAllocators.offHeapAllocator().allocate(readableBytes);
                    }
                }
                buffer.writeBytes(src.duplicate());
                try (var iterator = buffer.forEachReadable()) {
                    var component = iterator.first();
                    ByteBuffer nioBuffer = component.readableBuffer();
                    written = fd.write(nioBuffer, nioBuffer.position(), nioBuffer.limit());
                    assert component.next() == null;
                }
            } finally {
                Resource.dispose(buffer);
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
    public final void close() throws IOException {
        fd.close();
    }

    protected abstract BufferAllocator alloc();
}
