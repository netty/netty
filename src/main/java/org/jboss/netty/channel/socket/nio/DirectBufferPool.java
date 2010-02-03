/*
 * Copyright 2010 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.channel.socket.nio;

import java.lang.ref.SoftReference;
import java.nio.ByteBuffer;

import org.jboss.netty.buffer.ChannelBuffer;

/**
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @version $Rev$, $Date$
 */
final class DirectBufferPool {

    private static final int POOL_SIZE = 4;

    @SuppressWarnings("unchecked")
    private final SoftReference<ByteBuffer>[] pool = new SoftReference[POOL_SIZE];

    DirectBufferPool() {
        super();
    }

    final ByteBuffer acquire(ChannelBuffer src) {
        ByteBuffer dst = acquire(src.readableBytes());
        src.getBytes(src.readerIndex(), dst);
        dst.rewind();
        return dst;
    }

    final ByteBuffer acquire(int size) {
        for (int i = 0; i < POOL_SIZE; i ++) {
            SoftReference<ByteBuffer> ref = pool[i];
            if (ref == null) {
                continue;
            }

            ByteBuffer buf = ref.get();
            if (buf == null) {
                pool[i] = null;
                continue;
            }

            if (buf.capacity() < size) {
                continue;
            }

            pool[i] = null;

            buf.rewind();
            buf.limit(size);
            return buf;
        }

        ByteBuffer buf = ByteBuffer.allocateDirect(normalizeCapacity(size));
        buf.limit(size);
        return buf;
    }

    final void release(ByteBuffer buffer) {
        for (int i = 0; i < POOL_SIZE; i ++) {
            SoftReference<ByteBuffer> ref = pool[i];
            if (ref == null || ref.get() == null) {
                pool[i] = new SoftReference<ByteBuffer>(buffer);
                return;
            }
        }

        // pool is full - replace one
        final int capacity = buffer.capacity();
        for (int i = 0; i< POOL_SIZE; i ++) {
            SoftReference<ByteBuffer> ref = pool[i];
            ByteBuffer pooled = ref.get();
            if (pooled == null) {
                pool[i] = null;
                continue;
            }

            if (pooled.capacity() < capacity) {
                pool[i] = new SoftReference<ByteBuffer>(buffer);
                return;
            }
        }
    }

    private static final int normalizeCapacity(int capacity) {
        // Normalize to multiple of 4096.
        // Strictly speaking, 4096 should be normalized to 4096,
        // but it becomes 8192 to keep the calculation simplistic.
        return (capacity & 0xfffff000) + 0x1000;
    }
}
