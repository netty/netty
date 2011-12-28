/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel.sctp;

import com.sun.nio.sctp.MessageInfo;
import com.sun.nio.sctp.SctpChannel;
import io.netty.buffer.ChannelBuffer;

import java.io.IOException;
import java.lang.ref.SoftReference;
import java.nio.ByteBuffer;

/**
 */
final class SctpSendBufferPool {

    private static final SendBuffer EMPTY_BUFFER = new EmptySendBuffer();

    private static final int DEFAULT_PREALLOCATION_SIZE = 65536;
    private static final int ALIGN_SHIFT = 4;
    private static final int ALIGN_MASK = 15;

    PreallocationRef poolHead = null;
    Preallocation current = new Preallocation(DEFAULT_PREALLOCATION_SIZE);

    SctpSendBufferPool() {
        super();
    }

    final SendBuffer acquire(Object message) {
        if (message instanceof SctpPayload) {
            return acquire((SctpPayload) message);
        } else {
            throw new IllegalArgumentException(
                    "unsupported message type: " + message.getClass());
        }
    }

    private final SendBuffer acquire(SctpPayload message) {
        final ChannelBuffer src = message.getPayloadBuffer();
        final int streamNo = message.getStreamIdentifier();
        final int protocolId = message.getProtocolIdentifier();

        final int size = src.readableBytes();
        if (size == 0) {
            return EMPTY_BUFFER;
        }

        if (src.isDirect()) {
            return new UnpooledSendBuffer(streamNo, protocolId, src.toByteBuffer());
        }
        if (src.readableBytes() > DEFAULT_PREALLOCATION_SIZE) {
            return new UnpooledSendBuffer(streamNo, protocolId, src.toByteBuffer());
        }

        Preallocation current = this.current;
        ByteBuffer buffer = current.buffer;
        int remaining = buffer.remaining();
        PooledSendBuffer dst;

        if (size < remaining) {
            int nextPos = buffer.position() + size;
            ByteBuffer slice = buffer.duplicate();
            buffer.position(align(nextPos));
            slice.limit(nextPos);
            current.refCnt++;
            dst = new PooledSendBuffer(streamNo, protocolId, current, slice);
        } else if (size > remaining) {
            this.current = current = getPreallocation();
            buffer = current.buffer;
            ByteBuffer slice = buffer.duplicate();
            buffer.position(align(size));
            slice.limit(size);
            current.refCnt++;
            dst = new PooledSendBuffer(streamNo, protocolId, current, slice);
        } else { // size == remaining
            current.refCnt++;
            this.current = getPreallocation0();
            dst = new PooledSendBuffer(streamNo, protocolId, current, current.buffer);
        }

        ByteBuffer dstbuf = dst.buffer;
        dstbuf.mark();
        src.getBytes(src.readerIndex(), dstbuf);
        dstbuf.reset();
        return dst;
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
            q++;
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

    interface SendBuffer {
        boolean finished();

        long writtenBytes();

        long totalBytes();

        long transferTo(SctpChannel ch) throws IOException;

        void release();
    }

    class UnpooledSendBuffer implements SendBuffer {

        final ByteBuffer buffer;
        final int initialPos;
        final int streamNo;
        final int protocolId;

        UnpooledSendBuffer(int streamNo, int protocolId, ByteBuffer buffer) {
            this.streamNo = streamNo;
            this.protocolId = protocolId;
            this.buffer = buffer;
            initialPos = buffer.position();
        }

        @Override
        public final boolean finished() {
            return !buffer.hasRemaining();
        }

        @Override
        public final long writtenBytes() {
            return buffer.position() - initialPos;
        }

        @Override
        public final long totalBytes() {
            return buffer.limit() - initialPos;
        }

        @Override
        public long transferTo(SctpChannel ch) throws IOException {
            final MessageInfo messageInfo = MessageInfo.createOutgoing(ch.association(), null, streamNo);
            messageInfo.payloadProtocolID(protocolId);
            messageInfo.streamNumber(streamNo);
            ch.send(buffer, messageInfo);
            return writtenBytes();
        }

        @Override
        public void release() {
            // Unpooled.
        }
    }

    final class PooledSendBuffer implements SendBuffer {

        private final Preallocation parent;
        final ByteBuffer buffer;
        final int initialPos;
        final int streamNo;
        final int protocolId;

        PooledSendBuffer(int streamNo, int protocolId, Preallocation parent, ByteBuffer buffer) {
            this.streamNo = streamNo;
            this.protocolId = protocolId;
            this.parent = parent;
            this.buffer = buffer;
            initialPos = buffer.position();
        }

        @Override
        public boolean finished() {
            return !buffer.hasRemaining();
        }

        @Override
        public long writtenBytes() {
            return buffer.position() - initialPos;
        }

        @Override
        public long totalBytes() {
            return buffer.limit() - initialPos;
        }

        @Override
        public long transferTo(SctpChannel ch) throws IOException {
            final MessageInfo messageInfo = MessageInfo.createOutgoing(ch.association(), null, streamNo);
            messageInfo.payloadProtocolID(protocolId);
            messageInfo.streamNumber(streamNo);
            ch.send(buffer, messageInfo);
            return writtenBytes();
        }

        @Override
        public void release() {
            final Preallocation parent = this.parent;
            if (--parent.refCnt == 0) {
                parent.buffer.clear();
                if (parent != current) {
                    poolHead = new PreallocationRef(parent, poolHead);
                }
            }
        }
    }

    static final class EmptySendBuffer implements SendBuffer {

        EmptySendBuffer() {
            super();
        }

        @Override
        public final boolean finished() {
            return true;
        }

        @Override
        public final long writtenBytes() {
            return 0;
        }

        @Override
        public final long totalBytes() {
            return 0;
        }

        @Override
        public long transferTo(SctpChannel ch) throws IOException {
            return 0;
        }

        @Override
        public void release() {
            // Unpooled.
        }
    }
}
