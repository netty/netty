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

import java.nio.ByteBuffer;

import org.jboss.netty.buffer.ChannelBuffer;

/**
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @version $Rev$, $Date$
 */
final class DirectBufferPool {

    private static final int preallocatedBufferCapacity = 128 * 1024;

    private ByteBuffer preallocatedBuffer;

    DirectBufferPool() {
        super();
    }

    final ByteBuffer acquire(ChannelBuffer src) {
        ByteBuffer dst = acquire(src.readableBytes());
        dst.mark();
        src.getBytes(src.readerIndex(), dst);
        dst.reset();
        return dst;
    }

    final ByteBuffer acquire(int size) {
        ByteBuffer preallocatedBuffer = this.preallocatedBuffer;
        if (preallocatedBuffer == null) {
            if (size < preallocatedBufferCapacity) {
                return preallocateAndAcquire(size);
            } else {
                return ByteBuffer.allocateDirect(size);
            }
        }

        if (preallocatedBuffer.remaining() < size) {
            if (size > preallocatedBufferCapacity) {
                return ByteBuffer.allocateDirect(size);
            } else {
                return preallocateAndAcquire(size);
            }
        } else {
            int nextPos = preallocatedBuffer.position() + size;
            ByteBuffer x = preallocatedBuffer.duplicate();
            preallocatedBuffer.position(nextPos);
            x.limit(nextPos);
            return x;
        }
    }

    private final ByteBuffer preallocateAndAcquire(int size) {
        ByteBuffer preallocatedBuffer = this.preallocatedBuffer =
            ByteBuffer.allocateDirect(preallocatedBufferCapacity);
        ByteBuffer x = preallocatedBuffer.duplicate();
        x.limit(size);
        preallocatedBuffer.position(size);
        return x;
    }
}
