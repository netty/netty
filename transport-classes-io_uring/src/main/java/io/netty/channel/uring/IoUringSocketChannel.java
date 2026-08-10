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

    private final class IoUringSocketUnsafe extends IoUringStreamUnsafe {
        @Override
        protected int scheduleWriteSingle(Object msg) {
            assert writeId == 0;

            if (IoUring.isSendZcSupported() && msg instanceof ByteBuf) {
                ByteBuf buf = (ByteBuf) msg;
                int length = buf.readableBytes();
                if (((IoUringSocketChannelConfig) config()).shouldWriteZeroCopy(length)) {
                    long address = IoUring.memoryAddress(buf) + buf.readerIndex();
                    short opsId = nextWriteOperationId();
                    if (opsId == 0) {
                        return 0;
                    }
                    IoUringIoOps ops = IoUringIoOps.newSendZc(fd().intValue(), address, length, 0, opsId, 0);
                    byte opCode = ops.opcode();
                    recordWriteOperation(opsId, opCode, buf);
                    writeId = registration().submit(ops);
                    writeOpCode = opCode;
                    if (writeId == 0) {
                        rollbackWriteOperation(opsId, opCode);
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
            //at least one buffer in the batch exceeds `IO_URING_WRITE_ZERO_COPY_THRESHOLD`.
            if (IoUring.isSendmsgZcSupported()
                    && (ioUringSocketChannelConfig.shouldWriteZeroCopy(((ByteBuf) in.current()).readableBytes()))) {
                IoUringIoHandler handler = registration().attachment();

                IovArray iovArray = handler.iovArray();
                int offset = iovArray.count();
                IovArrayReferenceCollector collector = iovArrayReferenceCollector();
                collector.reset(iovArray);
                // Limit to the maximum number of fragments to ensure we don't get an error when we have too many
                // buffers.
                iovArray.maxCount(Native.MAX_SKB_FRAGS);
                try {
                    in.forEachFlushedMessage(new ChannelOutboundBuffer.MessageProcessor() {
                        @Override
                        public boolean processMessage(Object msg) throws Exception {
                            if (msg instanceof ByteBuf) {
                                ByteBuf buf = (ByteBuf) msg;
                                int length = buf.readableBytes();
                                if (ioUringSocketChannelConfig.shouldWriteZeroCopy(length)) {
                                    return collector.processMessage(msg);
                                }
                            }
                            return false;
                        }
                    });
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
                short opsId = nextWriteOperationId();
                if (opsId == 0) {
                    return 0;
                }
                IoUringIoOps ops = IoUringIoOps.newSendmsgZc(fd().intValue(), (byte) 0, 0, hdr.address(), opsId);
                byte opCode = ops.opcode();
                recordWriteOperation(opsId, opCode, collector.referencesArray(), collector.referencesCount());
                writeId = registration().submit(ops);
                writeOpCode = opCode;
                if (writeId == 0) {
                    rollbackWriteOperation(opsId, opCode);
                    return 0;
                }
                return 1;
            }
            // Should not use sendmsg_zc, just use normal writev.
            return super.scheduleWriteMultiple(in);
        }

        @Override
        protected ChannelOutboundBuffer.MessageProcessor filterWriteMultiple(IovArrayReferenceCollector collector) {
            if (!IoUring.isSendmsgZcSupported()) {
                return super.filterWriteMultiple(collector);
            }
            IoUringSocketChannelConfig ioUringSocketChannelConfig = (IoUringSocketChannelConfig) config();
            return new ChannelOutboundBuffer.MessageProcessor() {
                @Override
                public boolean processMessage(Object msg) throws Exception {
                    if (msg instanceof ByteBuf) {
                        ByteBuf buf = (ByteBuf) msg;
                        int length = buf.readableBytes();
                        if (ioUringSocketChannelConfig.shouldWriteZeroCopy(length)) {
                            return false;
                        }
                    }
                    return collector.processMessage(msg);
                }
            };
        }

        @Override
        boolean writeComplete0(byte op, int res, int flags, short data, int outstanding) {
            if (op == Native.IORING_OP_SEND_ZC || op == Native.IORING_OP_SENDMSG_ZC) {
                return handleWriteCompleteZeroCopy(op, res, flags);
            }
            return super.writeComplete0(op, res, flags, data, outstanding);
        }

        private boolean handleWriteCompleteZeroCopy(byte op, int res, int flags) {
            if ((flags & Native.IORING_CQE_F_NOTIF) != 0) {
                return true;
            }
            writeId = 0;
            writeOpCode = 0;
            ChannelOutboundBuffer channelOutboundBuffer = outboundBuffer();
            if (channelOutboundBuffer == null) {
                return true;
            }
            if (res >= 0) {
                // The kernel may still own the memory when IORING_CQE_F_MORE is set, but the retained
                // WriteOperation holds it alive until the notification, so the buffers can be removed here
                // either way. A partial write is reported as a partial write.
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
