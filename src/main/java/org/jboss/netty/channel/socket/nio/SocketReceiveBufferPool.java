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

/**
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @version $Rev: 2198 $, $Date: 2010-02-23 09:56:04 +0900 (Tue, 23 Feb 2010) $
 */
final class SocketReceiveBufferPool {

    private static final int POOL_SIZE = 8;

    @SuppressWarnings("unchecked")
    private final SoftReference<ByteBuffer>[] pool = new SoftReference[POOL_SIZE];

    SocketReceiveBufferPool() {
        super();
    }

    final ByteBuffer acquire(int size) {
        final SoftReference<ByteBuffer>[] pool = this.pool;
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

            buf.clear();
            return buf;
        }

        ByteBuffer buf = ByteBuffer.allocateDirect(normalizeCapacity(size));
        buf.clear();
        return buf;
    }

    final void release(ByteBuffer buffer) {
        final SoftReference<ByteBuffer>[] pool = this.pool;
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
        // Normalize to multiple of 1024
        int q = capacity >>> 10;
        int r = capacity & 1023;
        if (r != 0) {
            q ++;
        }
        return q << 10;
    }
}