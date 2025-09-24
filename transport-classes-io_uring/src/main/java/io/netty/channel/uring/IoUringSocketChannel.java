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
import io.netty.channel.Channel;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.SocketChannelConfig;
import io.netty.channel.unix.IovArray;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayDeque;
import java.util.Queue;

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

    // Marker object that is used to mark a batch of buffers that were used with zero-copy write operations.
    private static final Object ZC_BATCH_MARKER = new Object();

    private final class IoUringSocketUnsafe extends IoUringStreamUnsafe {
        /**
         * Queue that holds buffers that we can't release yet as the kernel still holds a reference to these.
         */
        private Queue<Object> zcWriteQueue;

        @Override
        protected int scheduleWriteSingle(Object msg) {
            assert writeId == 0;

            if (IoUring.isSendZcSupported() && msg instanceof ByteBuf) {
                ByteBuf buf = (ByteBuf) msg;
                int length = buf.readableBytes();
                if (((IoUringSocketChannelConfig) config()).shouldWriteZeroCopy(length)) {
                    long address = IoUring.memoryAddress(buf) + buf.readerIndex();
                    IoUringIoOps ops = IoUringIoOps.newSendZc(fd().intValue(), address, length, 0, nextOpsId(), 0);
                    byte opCode = ops.opcode();
                    writeId = registration().submit(ops);
                    writeOpCode = opCode;
                    if (writeId == 0) {
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

            if (IoUring.isSendmsgZcSupported() && (
                    (IoUringSocketChannelConfig) config()).shouldWriteZeroCopy((int) in.totalPendingWriteBytes())) {
                IoUringIoHandler handler = registration().attachment();

                IovArray iovArray = handler.iovArray();
                int offset = iovArray.count();
                // Limit to the maximum number of fragments to ensure we don't get an error when we have too many
                // buffers.
                iovArray.maxCount(Native.MAX_SKB_FRAGS);
                try {
                    in.forEachFlushedMessage(iovArray);
                } catch (Exception e) {
                    // This should never happen, anyway fallback to single write.
                    return scheduleWriteSingle(in.current());
                }
                long iovArrayAddress = iovArray.memoryAddress(offset);
                int iovArrayLength = iovArray.count() - offset;

                MsgHdrMemoryArray msgHdrArray = handler.msgHdrMemoryArray();
                MsgHdrMemory hdr = msgHdrArray.nextHdr();
                assert hdr != null;
                hdr.set(iovArrayAddress, iovArrayLength);
                IoUringIoOps ops = IoUringIoOps.newSendmsgZc(fd().intValue(), (byte) 0, 0, hdr.address(), nextOpsId());
                byte opCode = ops.opcode();
                writeId = registration().submit(ops);
                writeOpCode = opCode;
                if (writeId == 0) {
                    return 0;
                }
                return 1;
            }
            // Should not use sendmsg_zc, just use normal writev.
            return super.scheduleWriteMultiple(in);
        }

        @Override
        boolean writeComplete0(byte op, int res, int flags, short data, int outstanding) {
            ChannelOutboundBuffer channelOutboundBuffer = unsafe().outboundBuffer();
            if (op == Native.IORING_OP_SEND_ZC || op == Native.IORING_OP_SENDMSG_ZC) {
                return handleWriteCompleteZeroCopy(op, channelOutboundBuffer, res, flags);
            }
            return super.writeComplete0(op, res, flags, data, outstanding);
        }

        private boolean handleWriteCompleteZeroCopy(byte op, ChannelOutboundBuffer channelOutboundBuffer,
                                                    int res, int flags) {
            if ((flags & Native.IORING_CQE_F_NOTIF) == 0) {
                // We only want to reset these if IORING_CQE_F_NOTIF is not set.
                // If it's set we know this is only an extra notification for a write but we already handled
                // the write completions before.
                // See https://man7.org/linux/man-pages/man2/io_uring_enter.2.html section: IORING_OP_SEND_ZC
                writeId = 0;
                writeOpCode = 0;

                boolean more = (flags & Native.IORING_CQE_F_MORE) != 0;
                if (more) {
                    // This is the result of send_sz or sendmsg_sc but there will also be another notification
                    // which will let us know that we can release the buffer(s). In this case let's retain the
                    // buffer(s) once and store it in an internal queue. Once we receive the notification we will
                    // call release() on the buffer(s) as it's not used by the kernel anymore.
                    if (zcWriteQueue == null) {
                        zcWriteQueue = new ArrayDeque<>(8);
                    }
                }
                if (res >= 0) {
                    if (more) {

                        // Loop through all the buffers that were part of the operation so we can add them to our
                        // internal queue to release later.
                        do {
                            ByteBuf currentBuffer = (ByteBuf) channelOutboundBuffer.current();
                            assert currentBuffer != null;
                            zcWriteQueue.add(currentBuffer);
                            currentBuffer.retain();
                            int readable = currentBuffer.readableBytes();
                            int skip = Math.min(readable, res);
                            currentBuffer.skipBytes(skip);
                            channelOutboundBuffer.progress(readable);
                            if (readable <= res) {
                                boolean removed = channelOutboundBuffer.remove();
                                assert removed;
                            }
                            res -= readable;
                        } while (res > 0);
                        // Add the marker so we know when we need to stop releasing
                        zcWriteQueue.add(ZC_BATCH_MARKER);
                    } else {
                        // We don't expect any extra notification, just directly let the buffer be released.
                        channelOutboundBuffer.removeBytes(res);
                    }
                    return true;
                } else {
                    if (res == Native.ERRNO_ECANCELED_NEGATIVE) {
                        if (more) {
                            // The send was cancelled but we expect another notification. Just add the marker to the
                            // queue so we don't get into trouble once the final notification for this operation is
                            // received.
                            zcWriteQueue.add(ZC_BATCH_MARKER);
                        }
                        return true;
                    }
                    try {
                        String msg = op == Native.IORING_OP_SEND_ZC ? "io_uring sendzc" : "io_uring sendmsg_zc";
                        int result = ioResult(msg, res);
                        if (more) {
                            try {
                                // We expect another notification so we need to ensure we retain these buffers
                                // so we can release these once we see IORING_CQE_F_NOTIF set.
                                addFlushedToZcWriteQueue(channelOutboundBuffer);
                            } catch (Exception e) {
                                // should never happen but let's handle it anyway.
                                handleWriteError(e);
                            }
                        }
                        if (result == 0) {
                            return false;
                        }
                    } catch (Throwable cause) {
                        if (more) {
                            try {
                                // We expect another notification as handleWriteError(...) will fail all flushed writes
                                // and also release any buffers we need to ensure we retain these buffers
                                // so we can release these once we see IORING_CQE_F_NOTIF set.
                                addFlushedToZcWriteQueue(channelOutboundBuffer);
                            } catch (Exception e) {
                                // should never happen but let's handle it anyway.
                                cause.addSuppressed(e);
                            }
                        }
                        handleWriteError(cause);
                    }
                }
            } else {
                if (zcWriteQueue != null) {
                    for (;;) {
                        Object queued = zcWriteQueue.remove();
                        assert queued != null;
                        if (queued == ZC_BATCH_MARKER) {
                            // Done releasing the buffers of the zero-copy batch.
                            break;
                        }
                        // The buffer can now be released.
                        ((ByteBuf) queued).release();
                    }
                }
            }
            return true;
        }

        private void addFlushedToZcWriteQueue(ChannelOutboundBuffer channelOutboundBuffer) throws Exception {
            // We expect another notification as handleWriteError(...) will fail all flushed writes
            // and also release any buffers we need to ensure we retain these buffers
            // so we can release these once we see IORING_CQE_F_NOTIF set.
            try {
                channelOutboundBuffer.forEachFlushedMessage(m -> {
                    if (!(m instanceof ByteBuf)) {
                        return false;
                    }
                    zcWriteQueue.add(m);
                    ((ByteBuf) m).retain();
                    return true;
                });
            } finally {
                zcWriteQueue.add(ZC_BATCH_MARKER);
            }
        }
        @Override
        protected boolean canCloseNow0() {
            return (zcWriteQueue == null || zcWriteQueue.isEmpty()) && super.canCloseNow0();
        }
    }
}
