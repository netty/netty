/*
 * Copyright 2024 The Netty Project
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
package io.netty.channel.uring;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.SocketChannelConfig;
import io.netty.channel.unix.IovArray;
import io.netty.util.ReferenceCounted;
import io.netty.util.internal.SystemPropertyUtil;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static io.netty.channel.unix.Errors.ioResult;

public final class IoUringSocketChannel extends AbstractIoUringStreamChannel implements SocketChannel {
    private final IoUringSocketChannelConfig config;

    public IoUringSocketChannel() {
       super(null, LinuxSocket.newSocketStream(), false);
       this.config = new IoUringSocketChannelConfig(this);
    }

    IoUringSocketChannel(Channel parent, LinuxSocket fd) {
        super(parent, fd, true);
        this.config = new IoUringSocketChannelConfig(this);
    }

    IoUringSocketChannel(Channel parent, LinuxSocket fd, SocketAddress remote) {
        super(parent, fd, remote);
        this.config = new IoUringSocketChannelConfig(this);
    }

    /**
     * Returns the {@code TCP_INFO} for the current socket.
     * See <a href="https://linux.die.net//man/7/tcp">man 7 tcp</a>.
     */
    public IoUringTcpInfo tcpInfo() {
        return tcpInfo(new IoUringTcpInfo());
    }

    /**
     * Updates and returns the {@code TCP_INFO} for the current socket.
     * See <a href="https://linux.die.net//man/7/tcp">man 7 tcp</a>.
     */
    public IoUringTcpInfo tcpInfo(IoUringTcpInfo info) {
        try {
            socket.getTcpInfo(info);
            return info;
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public ServerSocketChannel parent() {
        return (ServerSocketChannel) super.parent();
    }

    @Override
    public SocketChannelConfig config() {
        return config;
    }

    @Override
    public InetSocketAddress remoteAddress() {
        return (InetSocketAddress) super.remoteAddress();
    }

    @Override
    public InetSocketAddress localAddress() {
        return (InetSocketAddress) super.localAddress();
    }

    @Override
    protected AbstractUringUnsafe newUnsafe() {
        return new IoUringSocketUnsafe();
    }

    private final class IoUringSocketUnsafe extends IoUringStreamUnsafe {
        /**
         * Holds buffers that we can't release yet as the kernel still holds a reference to these.
         */
        private PendingZeroCopyWrites pendingZeroCopyWrites;

        private ZeroCopyBatch zeroCopyBatch;

        @Override
        protected int scheduleWriteSingle(Object msg) {
            assert writeId == 0;

            if (IoUring.isSendZcSupported() && msg instanceof ByteBuf) {
                ByteBuf buf = (ByteBuf) msg;
                int length = buf.readableBytes();
                if (((IoUringSocketChannelConfig) config()).shouldWriteZeroCopy(length)) {
                    long address = IoUring.memoryAddress(buf) + buf.readerIndex();
                    long opsId = nextZeroCopyUserData();
                    IoUringIoOps ops = IoUringIoOps.newSendZc(fd().intValue(), address, length, 0, opsId, 0);
                    byte opCode = ops.opcode();
                    writeId = submitWrite(ops);
                    writeOpCode = opCode;
                    if (writeId == 0) {
                        pendingZeroCopyWrites.recycle(opsId);
                        return 0;
                    }
                    return 1;
                }
                // Should not use send_zc, just use normal write.
            }
            return super.scheduleWriteSingle(msg);
        }

        @Override
        protected int scheduleWriteMultiple(ChannelOutboundBuffer in) {
            assert writeId == 0;

            IoUringSocketChannelConfig ioUringSocketChannelConfig = (IoUringSocketChannelConfig) config();
            int threshold = ioUringSocketChannelConfig.getWriteZeroCopyThreshold();
            if (!IoUring.isSendmsgZcSupported() || threshold == IoUringSocketChannelConfig.DISABLE_WRITE_ZERO_COPY) {
                return super.scheduleWriteMultiple(in);
            }
            IoUringIoHandler handler = registration().attachment();

            IovArray iovArray = handler.iovArray();
            int offset = iovArray.count();
            if (zeroCopyBatch == null) {
                zeroCopyBatch = new ZeroCopyBatch();
            }
            try {
                // The iovArray is filled for a normal writev, the batch only watches what is added to it.
                in.forEachFlushedMessage(zeroCopyBatch.scan(threshold, iovArray));
            } catch (Exception e) {
                // This should never happen, anyway fallback to single write.
                return scheduleWriteSingle(in.current());
            }
            // Only use zero copy if collection can reach a large buffer.
            if (zeroCopyBatch.scannedLarge() && zeroCopyBatch.allocateMerged(alloc())) {
                try {
                    zeroCopyBatch.collect(in, iovArray, offset);
                } catch (Exception e) {
                    // This should never happen, anyway fallback to single write.
                    zeroCopyBatch.release();
                    return scheduleWriteSingle(in.current());
                }
                long iovArrayAddress = iovArray.memoryAddress(offset);
                int iovArrayLength = iovArray.count() - offset;

                MsgHdrMemory hdr = writeMsgHdr();
                hdr.set(iovArrayAddress, iovArrayLength);
                long opsId = nextZeroCopyUserData();
                IoUringIoOps ops = IoUringIoOps.newSendmsgZc(
                        fd().intValue(), (byte) 0, 0, hdr.address(), opsId);
                byte opCode = ops.opcode();
                writeId = submitWrite(ops);
                writeOpCode = opCode;
                if (writeId == 0) {
                    pendingZeroCopyWrites.recycle(opsId);
                    zeroCopyBatch.release();
                    return 0;
                }
                return 1;
            }
            // Should not use sendmsg_zc, just use normal writev.
            IoUringIoOps ops = IoUringIoOps.newWritev(fd().intValue(), (byte) 0, 0,
                    iovArray.memoryAddress(offset), iovArray.count() - offset, nextOpsId());
            byte opCode = ops.opcode();
            writeId = submitWrite(ops);
            writeOpCode = opCode;
            return writeId == 0 ? 0 : 1;
        }

        private long nextZeroCopyUserData() {
            PendingZeroCopyWrites pendingWrites = pendingZeroCopyWrites;
            if (pendingWrites == null) {
                pendingWrites = new PendingZeroCopyWrites();
                pendingZeroCopyWrites = pendingWrites;
            }
            return pendingWrites.nextUserData();
        }

        @Override
        List<ReferenceCounted> retainWriteBuffers(ChannelOutboundBuffer buffer) {
            if (currentWrite.opcode() == Native.IORING_OP_SENDMSG_ZC) {
                return zeroCopyBatch.retain();
            }
            return super.retainWriteBuffers(buffer);
        }

        @Override
        boolean writeComplete0(byte op, int res, int flags, long data, int outstanding) {
            if (op == Native.IORING_OP_SEND_ZC || op == Native.IORING_OP_SENDMSG_ZC) {
                return handleWriteCompleteZeroCopy(op, res, flags, data);
            }
            return super.writeComplete0(op, res, flags, data, outstanding);
        }

        private void takeSendZCBuffers(long data) {
            PendingZeroCopyWrites.PendingWrite write = pendingZeroCopyWrites.register(data);
            if (retainedWriteBuffers == null) {
                if (currentWrite.opcode() == Native.IORING_OP_SENDMSG_ZC) {
                    zeroCopyBatch.transferTo(write);
                } else {
                    write.add(((ByteBuf) outboundBuffer().current()).retain());
                }
            } else {
                // Shutdown may have cleared the outbound buffer before the primary CQE; use the retained buffers.
                for (int i = 0; i < retainedWriteBuffers.size(); i++) {
                    write.add(retainedWriteBuffers.get(i));
                }
                retainedWriteBuffers = Collections.emptyList();
            }
        }

        private boolean handleWriteCompleteZeroCopy(byte op, int res, int flags, long data) {
            if ((flags & Native.IORING_CQE_F_NOTIF) != 0) {
                pendingZeroCopyWrites.release(data);
                return true;
            }
            writeId = 0;
            writeOpCode = 0;
            if ((flags & Native.IORING_CQE_F_MORE) != 0) {
                // Establish notification ownership before removeBytes() can notify a write listener.
                takeSendZCBuffers(data);
            } else {
                pendingZeroCopyWrites.recycle(data);
                if (op == Native.IORING_OP_SENDMSG_ZC) {
                    zeroCopyBatch.release();
                }
            }
            ChannelOutboundBuffer channelOutboundBuffer = outboundBuffer();
            if (channelOutboundBuffer == null) {
                return true;
            }
            if (res >= 0) {
                channelOutboundBuffer.removeBytes(res);
                return true;
            }
            if (res == Native.ERRNO_ECANCELED_NEGATIVE) {
                return true;
            }
            try {
                return ioResult(op == Native.IORING_OP_SEND_ZC ? "io_uring sendzc" : "io_uring sendmsg_zc", res) != 0;
            } catch (Throwable cause) {
                handleWriteError(cause);
                return true;
            }
        }
    }

    /**
     * The buffers of the {@code SENDMSG_ZC} that is in flight.
     * <p>
     * A message that reaches the zero-copy threshold is sent from its own memory. Smaller messages are not worth
     * pinning their pages, so every run of adjacent small messages is copied into one buffer and only takes one
     * iovec:
     * <pre>
     * messages: small small LARGE small LARGE LARGE small small
     * iovecs:   [  merged  ] LARGE [mer.] LARGE LARGE [  merged  ]
     * </pre>
     */
    private static final class ZeroCopyBatch implements ChannelOutboundBuffer.MessageProcessor {
        // The pooled allocator pools up to 4MiB, the adaptive allocator only up to 1MiB.
        private static final int MERGED_MAX_SIZE = Math.min(4 * 1024 * 1024,
                Math.max(1, SystemPropertyUtil.getInt("io.netty.iouring.zeroCopyMergedMaxSize", 64 * 1024)));

        // The messages that are sent from their own memory, these are not retained yet.
        private final List<ByteBuf> largeBuffers = new ArrayList<>(4);
        // Holds the bytes of the small messages, the batch owns the reference it was allocated with.
        private ByteBuf merged;
        // Start of the run of small messages in merged that was not added to the iovArray yet.
        private int runStart;
        private IovArray iovArray;
        private int threshold;
        private long smallBytes;
        private boolean hasLarge;
        private boolean scanning;

        /**
         * Returns a {@link ChannelOutboundBuffer.MessageProcessor} that adds the messages to {@code iovArray} like
         * the {@code iovArray} itself would do for a writev. This also tells which messages fit and how many bytes
         * need to be copied, so one allocation is enough.
         */
        ChannelOutboundBuffer.MessageProcessor scan(int threshold, IovArray iovArray) {
            this.iovArray = iovArray;
            this.threshold = threshold;
            smallBytes = 0;
            hasLarge = false;
            scanning = true;
            return this;
        }

        /**
         * Returns {@code true} if a scanned iovec reaches the threshold and the preceding small iovecs fit in the
         * merged buffer. The result is only valid once.
         */
        boolean scannedLarge() {
            boolean large = hasLarge;
            hasLarge = false;
            scanning = false;
            return large;
        }

        /**
         * Allocates the buffer for the scanned small messages. Returns {@code false} if there is no memory for it,
         * the iovArray still holds the entries of the scan then, so the messages can be written with a writev. This
         * must not throw as it also runs while handling a completion, where an {@link Error} ends the event loop.
         */
        boolean allocateMerged(ByteBufAllocator allocator) {
            if (smallBytes != 0) {
                // What does not fit is written later.
                int capacity = (int) Math.min(smallBytes, MERGED_MAX_SIZE);
                try {
                    merged = allocator.directBuffer(capacity, capacity);
                } catch (Exception | OutOfMemoryError e) {
                    return false;
                }
                runStart = 0;
            }
            return true;
        }

        /**
         * Replaces the entries that were added to the iovArray during the scan.
         */
        void collect(ChannelOutboundBuffer in, IovArray iovArray, int offset) throws Exception {
            iovArray.truncate(offset);
            in.forEachFlushedMessage(this);
            addMergedSmallBuffers();
        }

        @Override
        public boolean processMessage(Object msg) throws Exception {
            if (scanning) {
                int count = iovArray.count();
                boolean added = iovArray.processMessage(msg);
                // Look at the entries and not at the message, a message may consist of multiple buffers.
                for (int i = count; i < iovArray.count(); i++) {
                    long length = iovArray.length(i);
                    if (length < threshold) {
                        smallBytes += length;
                    } else if (smallBytes <= MERGED_MAX_SIZE) {
                        hasLarge = true;
                    }
                }
                return added;
            }
            if (!(msg instanceof ByteBuf)) {
                return false;
            }
            ByteBuf buf = (ByteBuf) msg;
            int index = buf.readerIndex();
            int readableBytes = buf.readableBytes();
            // All buffers of a message that is smaller than the threshold are smaller too, so copy it at once.
            if (readableBytes < threshold || buf.nioBufferCount() == 1) {
                return add(buf, index, readableBytes);
            }
            for (ByteBuffer nioBuffer : buf.nioBuffers(index, readableBytes)) {
                int length = nioBuffer.remaining();
                if (!add(buf, index, length)) {
                    return false;
                }
                index += length;
            }
            return true;
        }

        private boolean add(ByteBuf buf, int index, int length) {
            if (length < threshold) {
                if (length == 0) {
                    return true;
                }
                if (merged == null) {
                    return false;
                }
                // Copy what fits, the rest of the buffer is written later.
                int copied = Math.min(length, merged.writableBytes());
                merged.writeBytes(buf, index, copied);
                return copied == length;
            }
            if (!addMergedSmallBuffers()) {
                return false;
            }
            int count = iovArray.count();
            boolean added = iovArray.add(buf, index, length);
            // IovArray.add(...) may return false although the entry was added, so check the count.
            if (iovArray.count() != count
                    && (largeBuffers.isEmpty() || largeBuffers.get(largeBuffers.size() - 1) != buf)) {
                // A message that consists of CompositeByteBuf has one reference count for
                // all of them, and its buffers are added one after another. So it is enough to track it once.
                largeBuffers.add(buf);
            }
            return added;
        }

        private boolean addMergedSmallBuffers() {
            if (merged == null || merged.writerIndex() == runStart) {
                return true;
            }
            if (!iovArray.add(merged, runStart, merged.writerIndex() - runStart)) {
                return false;
            }
            runStart = merged.writerIndex();
            return true;
        }

        void transferTo(PendingZeroCopyWrites.PendingWrite write) {
            for (int i = 0; i < largeBuffers.size(); i++) {
                write.add(largeBuffers.get(i).retain());
            }
            if (merged != null) {
                write.add(merged);
            }
            largeBuffers.clear();
            merged = null;
        }

        /**
         * Same as {@link #transferTo(PendingZeroCopyWrites.PendingWrite)}, but for the case that the outbound
         * messages are released before the write completes.
         */
        List<ReferenceCounted> retain() {
            List<ReferenceCounted> buffers = new ArrayList<>(largeBuffers.size() + 1);
            for (int i = 0; i < largeBuffers.size(); i++) {
                buffers.add(largeBuffers.get(i).retain());
            }
            if (merged != null) {
                buffers.add(merged);
            }
            largeBuffers.clear();
            merged = null;
            return buffers;
        }

        /**
         * Must be called if the kernel doesn't reference the buffers (anymore).
         */
        void release() {
            if (merged != null) {
                merged.release();
                merged = null;
            }
            largeBuffers.clear();
        }
    }
}
