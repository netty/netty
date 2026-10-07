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
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.SocketChannelConfig;
import io.netty.channel.unix.IovArray;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
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
        @Override
        protected int scheduleWriteSingle(Object msg) {
            assert writeId == 0;

            if (IoUring.isSendZcSupported() && msg instanceof ByteBuf) {
                ByteBuf buf = (ByteBuf) msg;
                int length = buf.readableBytes();
                if (((IoUringSocketChannelConfig) config()).shouldWriteZeroCopy(length)) {
                    long address = IoUring.memoryAddress(buf) + buf.readerIndex();
                    long opsId = writeTracker.nextZeroCopyId();
                    IoUringIoOps ops = IoUringIoOps.newSendZc(fd().intValue(), address, length, 0, opsId, 0);
                    byte opCode = ops.opcode();
                    writeTracker.record(opsId, opCode, buf);
                    writeId = registration().submit(ops);
                    writeOpCode = opCode;
                    if (writeId == 0) {
                        writeTracker.abandon(opsId, opCode);
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
            // At least one buffer in the batch must exceed `IO_URING_WRITE_ZERO_COPY_THRESHOLD`. Looking at the
            // whole batch instead of only `in.current()` matters: an HTTP response starts with a small header,
            // so deciding on the first buffer alone would send that header with a writev and then the body with
            // a sendmsg_zc in a following event loop iteration, splitting one flush into two submissions.
            if (IoUring.isSendmsgZcSupported() && hasWriteZeroCopyMessage(in, ioUringSocketChannelConfig)) {
                IoUringIoHandler handler = registration().attachment();

                IovArray iovArray = handler.iovArray();
                int offset = iovArray.count();
                IovArrayReferenceCollector collector = handler.iovArrayReferenceCollector();
                try {
                    // Limit to the maximum number of fragments to ensure we don't get an error when we have too
                    // many buffers.
                    iovArray.maxCount(Native.MAX_SKB_FRAGS);
                    try {
                        // Gather the whole batch, including the buffers below the threshold, so that a small
                        // header and the buffers behind it go out in a single SQE. IovArray.processMessage(...)
                        // stops at the first non-ByteBuf message and once the fragment limit is reached.
                        in.forEachFlushedMessage(collector);
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
                    long opsId = writeTracker.nextZeroCopyId();
                    IoUringIoOps ops = IoUringIoOps.newSendmsgZc(
                            fd().intValue(), (byte) 0, 0, hdr.address(), opsId);
                    byte opCode = ops.opcode();
                    writeTracker.record(opsId, opCode, collector.referencesArray(), collector.referencesCount());
                    writeId = registration().submit(ops);
                    writeOpCode = opCode;
                    if (writeId == 0) {
                        writeTracker.abandon(opsId, opCode);
                        return 0;
                    }
                    return 1;
                } finally {
                    // The slot copied the references it needs, and an exception must not leave the event loop's
                    // shared collector holding this write's buffers.
                    collector.reset();
                }
            }
            // Should not use sendmsg_zc, just use normal writev.
            return super.scheduleWriteMultiple(in);
        }

        // Reused across writes: scheduleWriteMultiple(...) runs on the event loop and is never re-entered.
        private final ZeroCopyScanner zeroCopyScanner = new ZeroCopyScanner();

        /**
         * Returns {@code true} if any of the flushed messages is a {@link ByteBuf} that reaches the configured
         * zero-copy threshold.
         */
        private boolean hasWriteZeroCopyMessage(ChannelOutboundBuffer in,
                                                IoUringSocketChannelConfig ioUringSocketChannelConfig) {
            ZeroCopyScanner scanner = zeroCopyScanner;
            scanner.ioUringSocketChannelConfig = ioUringSocketChannelConfig;
            scanner.detected = false;
            try {
                in.forEachFlushedMessage(scanner);
            } catch (Exception e) {
                // The scanner itself never throws. Be conservative and let the writev path handle the batch.
                return false;
            }
            return scanner.detected;
        }

        private final class ZeroCopyScanner implements ChannelOutboundBuffer.MessageProcessor {
            private IoUringSocketChannelConfig ioUringSocketChannelConfig;
            private boolean detected;

            @Override
            public boolean processMessage(Object msg) {
                if (!(msg instanceof ByteBuf)) {
                    // The gather stops at the first non-ByteBuf message (for example a FileRegion), so the
                    // decision must stop there too: scanning past it could select the zero-copy path for buffers
                    // that are never gathered by the sendmsg_zc.
                    return false;
                }
                if (ioUringSocketChannelConfig.shouldWriteZeroCopy(((ByteBuf) msg).readableBytes())) {
                    detected = true;
                    // Stop the scan: one qualifying buffer is enough to select the zero-copy path.
                    return false;
                }
                return true;
            }
        }

        @Override
        boolean writeComplete0(byte op, int res, int flags, long data, int outstanding) {
            if (op == Native.IORING_OP_SEND_ZC || op == Native.IORING_OP_SENDMSG_ZC) {
                return handleWriteCompleteZeroCopy(op, res, flags, data);
            }
            return super.writeComplete0(op, res, flags, data, outstanding);
        }

        private boolean handleWriteCompleteZeroCopy(byte op, int res, int flags, long data) {
            if ((flags & Native.IORING_CQE_F_NOTIF) != 0) {
                return true;
            }
            writeId = 0;
            writeOpCode = 0;
            if ((flags & Native.IORING_CQE_F_MORE) != 0) {
                // Even errored requests may generate a notification, so the kernel still owns the memory
                // until the follow-up IORING_CQE_F_NOTIF arrives. Retain before any release below.
                // See https://man7.org/linux/man-pages/man2/io_uring_enter.2.html section: IORING_OP_SEND_ZC
                writeTracker.retainReferences(data, op);
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
}
