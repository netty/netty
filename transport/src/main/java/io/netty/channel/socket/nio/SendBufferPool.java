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
package io.netty.channel.socket.nio;

import java.io.IOException;
import java.lang.ref.SoftReference;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.WritableByteChannel;

import io.netty.buffer.ChannelBuffer;
import io.netty.buffer.CompositeChannelBuffer;
import io.netty.channel.FileRegion;
import io.netty.util.internal.DetectionUtil;

public class SendBufferPool {

    private static final SendBuffer EMPTY_BUFFER = new EmptySendBuffer();

    public static final int DEFAULT_PREALLOCATION_SIZE = 65536;
    public static final int ALIGN_SHIFT = 4;
    public static final int ALIGN_MASK = 15;

    protected PreallocationRef poolHead;
    protected Preallocation current = new Preallocation(DEFAULT_PREALLOCATION_SIZE);

    public SendBufferPool() {
    }

    
    public SendBuffer acquire(Object message) {
        if (message instanceof ChannelBuffer) {
            return acquire((ChannelBuffer) message);
        } else if (message instanceof FileRegion) {
            return acquire((FileRegion) message);
        }

        throw new IllegalArgumentException(
                "unsupported message type: " + message.getClass());
    }

    protected SendBuffer acquire(FileRegion src) {
        if (src.getCount() == 0) {
            return EMPTY_BUFFER;
        }
        return new FileSendBuffer(src);
    }

    private SendBuffer acquire(ChannelBuffer src) {
        final int size = src.readableBytes();
        if (size == 0) {
            return EMPTY_BUFFER;
        }

        if (src.isDirect()) {
            return new UnpooledSendBuffer(src.toByteBuffer());
        }
        
        if (src instanceof CompositeChannelBuffer && DetectionUtil.javaVersion() >= 7) {
            return new GatheringSendBuffer(src.toByteBuffers());
        }
        
        if (src.readableBytes() > DEFAULT_PREALLOCATION_SIZE) {
            return new UnpooledSendBuffer(src.toByteBuffer());
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
            current.refCnt ++;
            dst = new PooledSendBuffer(current, slice);
        } else if (size > remaining) {
            this.current = current = getPreallocation();
            buffer = current.buffer;
            ByteBuffer slice = buffer.duplicate();
            buffer.position(align(size));
            slice.limit(size);
            current.refCnt ++;
            dst = new PooledSendBuffer(current, slice);
        } else { // size == remaining
            current.refCnt ++;
            this.current = getPreallocation0();
            dst = new PooledSendBuffer(current, current.buffer);
        }

        ByteBuffer dstbuf = dst.buffer;
        dstbuf.mark();
        src.getBytes(src.readerIndex(), dstbuf);
        dstbuf.reset();
        return dst;
    }

    protected Preallocation getPreallocation() {
        Preallocation current = this.current;
        if (current.refCnt == 0) {
            current.buffer.clear();
            return current;
        }

        return getPreallocation0();
    }
    
    protected Preallocation getPreallocation0() {
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

    protected static int align(int pos) {
        int q = pos >>> ALIGN_SHIFT;
        int r = pos & ALIGN_MASK;
        if (r != 0) {
            q ++;
        }
        return q << ALIGN_SHIFT;
    }

    public static final class Preallocation {
        public final ByteBuffer buffer;
        public int refCnt;

        public Preallocation(int capacity) {
            buffer = ByteBuffer.allocateDirect(capacity);
        }
    }

    public final class PreallocationRef extends SoftReference<Preallocation> {
        final PreallocationRef next;

        public PreallocationRef(Preallocation prealloation, PreallocationRef next) {
            super(prealloation);
            this.next = next;
        }
    }

   public interface SendBuffer {
        boolean finished();
        long writtenBytes();
        long totalBytes();

        long transferTo(WritableByteChannel ch) throws IOException;
        long transferTo(DatagramChannel ch, SocketAddress raddr) throws IOException;

        void release();
    }

    public class UnpooledSendBuffer implements SendBuffer {

        protected final ByteBuffer buffer;
        final int initialPos;

        public UnpooledSendBuffer(ByteBuffer buffer) {
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
        public final long transferTo(WritableByteChannel ch) throws IOException {
            return ch.write(buffer);
        }

        @Override
        public final long transferTo(DatagramChannel ch, SocketAddress raddr) throws IOException {
            return ch.send(buffer, raddr);
        }

        @Override
        public void release() {
            // Unpooled.
        }
    }

    public class PooledSendBuffer implements SendBuffer {

        protected final Preallocation parent;
        public final ByteBuffer buffer;
        final int initialPos;

        public PooledSendBuffer(Preallocation parent, ByteBuffer buffer) {
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
        public long transferTo(WritableByteChannel ch) throws IOException {
            return ch.write(buffer);
        }

        @Override
        public long transferTo(DatagramChannel ch, SocketAddress raddr) throws IOException {
            return ch.send(buffer, raddr);
        }

        @Override
        public void release() {
            final Preallocation parent = this.parent;
            if (-- parent.refCnt == 0) {
                parent.buffer.clear();
                if (parent != current) {
                    poolHead = new PreallocationRef(parent, poolHead);
                }
            }
        }
    }

    class GatheringSendBuffer implements SendBuffer {

        private final ByteBuffer[] buffers;
        private final int last;
        private long written;
        private final int total;

        GatheringSendBuffer(ByteBuffer[] buffers) {
            this.buffers = buffers;
            this.last = buffers.length - 1;
            int total = 0;
            for (ByteBuffer buf: buffers) {
                total += buf.remaining();
            }
            this.total = total;
        }
        
        @Override
        public boolean finished() {
            return !buffers[last].hasRemaining();
        }

        @Override
        public long writtenBytes() {
            return written;
        }

        @Override
        public long totalBytes() {
            return total;
        }

        @Override
        public long transferTo(WritableByteChannel ch) throws IOException {
            if (ch instanceof GatheringByteChannel) {
                 long w = ((GatheringByteChannel) ch).write(buffers);
                 written += w;
                 return w;
            } else {
                int send = 0;
                for (ByteBuffer buf: buffers) {
                    if (buf.hasRemaining()) {
                        int w = ch.write(buf);
                        if (w == 0) {
                            break;
                        } else {
                            send += w;
                        }
                    }
                }
                written += send;
                return send;
            }
        }

        @Override
        public long transferTo(DatagramChannel ch, SocketAddress raddr) throws IOException {
            int send = 0;
            for (ByteBuffer buf: buffers) {
                if (buf.hasRemaining()) {
                    int w = ch.send(buf, raddr);
                    if (w == 0) {
                        break;
                    } else {
                        send += w;
                    }
                }
            }
            written += send;

            return send;
        }

        @Override
        public void release() {
            // nothing todo
        }
        
    }
    
    static final class FileSendBuffer implements SendBuffer {

        private final FileRegion file;
        private long writtenBytes;


        FileSendBuffer(FileRegion file) {
            this.file = file;
        }

        @Override
        public boolean finished() {
            return writtenBytes >= file.getCount();
        }

        @Override
        public long writtenBytes() {
            return writtenBytes;
        }

        @Override
        public long totalBytes() {
            return file.getCount();
        }

        @Override
        public long transferTo(WritableByteChannel ch) throws IOException {
            long localWrittenBytes = file.transferTo(ch, writtenBytes);
            writtenBytes += localWrittenBytes;
            return localWrittenBytes;
        }

        @Override
        public long transferTo(DatagramChannel ch, SocketAddress raddr)
                throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void release() {
            if (file.releaseAfterTransfer()) {
                // Make sure the FileRegion resource are released otherwise it may cause a FD leak or something similar
                file.releaseExternalResources(); 
            }
        }
    }

    static final class EmptySendBuffer implements SendBuffer {

        EmptySendBuffer() {
        }

        @Override
        public boolean finished() {
            return true;
        }

        @Override
        public long writtenBytes() {
            return 0;
        }

        @Override
        public long totalBytes() {
            return 0;
        }

        @Override
        public long transferTo(WritableByteChannel ch) throws IOException {
            return 0;
        }

        @Override
        public long transferTo(DatagramChannel ch, SocketAddress raddr) throws IOException {
            return 0;
        }

        @Override
        public void release() {
            // Unpooled.
        }
    }
}
