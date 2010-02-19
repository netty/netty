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
 * @version $Rev: 2174 $, $Date: 2010-02-19 09:57:23 +0900 (Fri, 19 Feb 2010) $
 */
final class SocketSendBufferPool {

    private static final int DEFAULT_PREALLOCATION_SIZE = 65536;
    private static final int ALIGN_SHIFT = 4;
    private static final int ALIGN_MASK = 15;

    static {
        assert (DEFAULT_PREALLOCATION_SIZE & ALIGN_MASK) == 0;
    }

    private PreallocationRef poolHead = null;
    private Preallocation current = new Preallocation(DEFAULT_PREALLOCATION_SIZE);

    SocketSendBufferPool() {
        super();
    }

    final SendBuffer acquire(ChannelBuffer src) {
        if (src.isDirect()) {
            return new SendBuffer(null, src.toByteBuffer());
        }
        if (src.readableBytes() > DEFAULT_PREALLOCATION_SIZE) {
            return new SendBuffer(null, src.toByteBuffer());
        }

        SendBuffer dst = acquire(src.readableBytes());
        ByteBuffer dstbuf = dst.buffer;
        dstbuf.mark();
        src.getBytes(src.readerIndex(), dstbuf);
        dstbuf.reset();
        return dst;
    }

    private final SendBuffer acquire(int size) {
        assert size <= DEFAULT_PREALLOCATION_SIZE;
        Preallocation current = this.current;
        ByteBuffer buffer = current.buffer;
        int remaining = buffer.remaining();

        if (size < remaining) {
            int nextPos = buffer.position() + size;
            ByteBuffer slice = buffer.duplicate();
            buffer.position(align(nextPos));
            slice.limit(nextPos);
            current.refCnt ++;
            return new SendBuffer(current, slice);
        } else if (size > remaining) {
            this.current = current = getPreallocation();
            buffer = current.buffer;
            ByteBuffer slice = buffer.duplicate();
            buffer.position(align(size));
            slice.limit(size);
            current.refCnt ++;
            return new SendBuffer(current, slice);
        } else { // size == remaining
            current.refCnt ++;
            this.current = getPreallocation0();
            return new SendBuffer(current, current.buffer);
        }
    }

    private final Preallocation getPreallocation() {
        Preallocation current = this.current;
        if (current.refCnt == 0) {
            current.buffer.clear();
            return current;
        }

        return getPreallocation0();
    }

    private final Preallocation getPreallocation0() {
        PreallocationRef ref = poolHead;
        if (ref != null) {
            do {
                Preallocation p = ref.get();
                ref = ref.next;

                if (p != null) {
                    poolHead = ref;
                    return p;
                }
            } while (ref != null);

            poolHead = ref;
        }

        return new Preallocation(DEFAULT_PREALLOCATION_SIZE);
    }

    private static final int align(int pos) {
        int q = pos >>> ALIGN_SHIFT;
        int r = pos & ALIGN_MASK;
        if (r != 0) {
            q ++;
        }
        return q << ALIGN_SHIFT;
    }

    private final class Preallocation {
        final ByteBuffer buffer;
        int refCnt;

        Preallocation(int capacity) {
            buffer = ByteBuffer.allocateDirect(capacity);
        }
    }

    private final class PreallocationRef extends SoftReference<Preallocation> {
        final PreallocationRef next;

        PreallocationRef(Preallocation prealloation, PreallocationRef next) {
            super(prealloation);
            this.next = next;
        }
    }

    final class SendBuffer {
        private final Preallocation parent;
        final ByteBuffer buffer;

        SendBuffer(Preallocation parent, ByteBuffer buffer) {
            this.parent = parent;
            this.buffer = buffer;
        }

        void release() {
            final Preallocation parent = this.parent;
            if (parent != null && -- parent.refCnt == 0) {
                parent.buffer.clear();
                if (parent != current) {
                    poolHead = new PreallocationRef(parent, poolHead);
                }
            }
        }
    }
}