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

import org.jboss.netty.util.ExternalResourceReleasable;
import org.jboss.netty.util.internal.ByteBufferUtil;

import java.nio.ByteBuffer;

final class SocketReceiveBufferAllocator implements ExternalResourceReleasable {

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
            return newBuffer(size);
        }
        if (buf.capacity() < size) {
            return newBuffer(size);
        }
        if (buf.capacity() * percentual / 100 > size) {
            if (++exceedCount == maxExceedCount) {
               return newBuffer(size);
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
        buf = ByteBuffer.allocateDirect(normalizeCapacity(size));
        return buf;
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

    public void releaseExternalResources() {
        if (buf != null) {
            ByteBufferUtil.destroy(buf);
        }
    }
}
