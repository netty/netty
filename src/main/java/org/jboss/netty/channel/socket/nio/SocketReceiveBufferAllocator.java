/*
 * Copyright 2012 The Netty Project
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
package org.jboss.netty.channel.socket.nio;

import java.nio.ByteBuffer;

import org.jboss.netty.util.internal.ByteBufferUtil;

final class SocketReceiveBufferAllocator {

    private ByteBuffer buf;
    private int exceedCount;
    private final int maxExceedCount;
    private final int percentual;

    SocketReceiveBufferAllocator() {
        this(16, 80);
    }

    SocketReceiveBufferAllocator(int maxExceedCount, int percentual) {
        this.maxExceedCount = maxExceedCount;
        this.percentual = percentual;
    }


    ByteBuffer get(int size) {
        if (buf == null) {
            buf = newBuffer(size);
        } else if (buf.capacity() < size) {
            buf = newBuffer(size);
        } else if (((buf.capacity() / 100) * percentual) > size) {
            if (++exceedCount == maxExceedCount) {
                buf = newBuffer(size);
            } else {
                buf.clear();
            }
        } else {
            exceedCount = 0;
            buf.clear();
        }
        return buf;
    }

    private ByteBuffer newBuffer(int size) {
        if (buf != null) {
            exceedCount = 0;
            ByteBufferUtil.destroy(buf);
        }
        return ByteBuffer.allocateDirect(normalizeCapacity(size));
    }

    private static int normalizeCapacity(int capacity) {
        // Normalize to multiple of 1024
        int q = capacity >>> 10;
        int r = capacity & 1023;
        if (r != 0) {
            q ++;
        }
        return q << 10;
    }
}
