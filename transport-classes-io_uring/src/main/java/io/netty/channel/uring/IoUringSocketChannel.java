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
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelShutdownType;
import io.netty.channel.DefaultFileRegion;
import io.netty.channel.EventLoop;
import io.netty.channel.IoRegistration;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.SocketProtocolFamily;
import io.netty.channel.unix.DomainSocketReadMode;
import io.netty.channel.unix.Errors;
import io.netty.channel.unix.FileDescriptor;
import io.netty.channel.unix.IovArray;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.NotYetConnectedException;
import java.util.ArrayDeque;
import java.util.Queue;

import static io.netty.channel.unix.Errors.ioResult;

public final class IoUringSocketChannel extends AbstractIoUringChannel implements SocketChannel {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(IoUringSocketChannel.class);
    private final IoUringSocketChannelConfig config;

    // Marker object that is used to mark a batch of buffers that were used with zero-copy write operations.
    private static final Object ZC_BATCH_MARKER = new Object();

    private MsgHdrMemory writeMsgHdrMemory;
    private MsgHdrMemory readMsgHdrMemory;

    // Store the opCode so we know if we used WRITE or WRITEV.
    byte writeOpCode;
    // Keep track of the ids used for write and read so we can cancel these when needed.
    long writeId;
    byte readOpCode;
    long readId;

    private ByteBuf readBuffer;

    // The configured buffer ring if any
    private IoUringBufferRing bufferRing;

    /**
     * Queue that holds buffers that we can't release yet as the kernel still holds a reference to these.
     */
    private Queue<Object> zcWriteQueue;

    public IoUringSocketChannel(EventLoop eventLoop) {
       this(eventLoop, null);
    }

    public IoUringSocketChannel(EventLoop eventLoop, SocketProtocolFamily family) {
        super(eventLoop, null, LinuxSocket.newSocket(family), false, false);
        this.config = new IoUringSocketChannelConfig(this);
    }

    IoUringSocketChannel(EventLoop eventLoop, Channel parent, LinuxSocket fd) {
        super(eventLoop, parent, fd, true, false);
        this.config = new IoUringSocketChannelConfig(this);
    }

    IoUringSocketChannel(EventLoop eventLoop, Channel parent, LinuxSocket fd, SocketAddress remote) {
        super(eventLoop, parent, fd, remote, false);
        this.config = new IoUringSocketChannelConfig(this);
    }

    @Override
    public ServerSocketChannel parent() {
        return (ServerSocketChannel) super.parent();
    }

    @Override
    protected void doRegister(Promise<Void> promise) {
        Promise<Void> registerPromise = this.newPromise();
        // Ensure that the buffer group is properly set before channel::read
        registerPromise.addListener(f -> {
            if (f.isSuccess()) {
                try {
                    short bgid = ((IoUringSocketChannelConfig) config()).getBufferGroupId();
                    if (bgid >= 0) {
                        final IoUringIoHandler ioUringIoHandler = registration().attachment();
                        bufferRing = ioUringIoHandler.findBufferRing(bgid);
                    }
                    if (active) {
                        // Register for POLLRDHUP if this channel is already considered active.
                        schedulePollRdHup();
                    }
                } finally {
                    promise.setSuccess(null);
                }
            } else {
                promise.setFailure(f.cause());
            }
        });

        super.doRegister(registerPromise);
    }

    @Override
    protected void doShutdown(ChannelShutdownType type, Promise<Void> promise) {
        if (type.data() != null) {
            promise.setFailure(new IllegalArgumentException("ChannelShutdownType with data is not supported: " + type));
            return;
        }
        boolean read = false;
        boolean write = false;
        switch (type.direction()) {
            case Outbound:
                write = true;
                break;
            case Inbound:
                read = true;
                break;
        }
        try {
            socket.shutdown(read, write);
        } catch (NotYetConnectedException ex) {
            // We attempted to shutdown and failed, which means the input has already effectively been
            // shutdown.
        } catch (Throwable cause) {
            promise.setFailure(cause);
            return;
        }
        promise.setSuccess(null);
    }

    @Override
    protected Object filterOutboundMessage(Object msg) {
        // Since we cannot use synchronous sendfile,
        // the channel can only support DefaultFileRegion instead of FileRegion.
        if (IoUring.isSpliceSupported() && msg instanceof DefaultFileRegion) {
            return new IoUringFileRegion((DefaultFileRegion) msg);
        }
        if (socket.protocolFamily() == SocketProtocolFamily.UNIX && msg instanceof FileDescriptor) {
            return msg;
        }
        return super.filterOutboundMessage(msg);
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

        int fd = fd().intValue();
        IoRegistration registration = registration();
        IoUringIoHandler handler = registration.attachment();
        IovArray iovArray = handler.iovArray();
        int offset = iovArray.count();

        try {
            in.forEachFlushedMessage(iovArray);
        } catch (Exception e) {
            // This should never happen, anyway fallback to single write.
            return scheduleWriteSingle(in.current());
        }
        long iovArrayAddress = iovArray.memoryAddress(offset);
        int iovArrayLength = iovArray.count() - offset;
        // Should not use sendmsg_zc, just use normal writev.
        IoUringIoOps ops = IoUringIoOps.newWritev(fd, (byte) 0, 0, iovArrayAddress, iovArrayLength, nextOpsId());

        byte opCode = ops.opcode();
        writeId = registration.submit(ops);
        writeOpCode = opCode;
        if (writeId == 0) {
            return 0;
        }
        return 1;
    }

    @Override
    protected int scheduleWriteSingle(Object msg) {
        assert writeId == 0;

        if (socket.protocolFamily() == SocketProtocolFamily.UNIX) {
            if (msg instanceof FileDescriptor) {
                // we can reuse the same memory for any fd
                // because we never have more than a single outstanding write.
                if (writeMsgHdrMemory == null) {
                    writeMsgHdrMemory = new MsgHdrMemory();
                }
                IoRegistration registration = registration();
                IoUringIoOps ioUringIoOps = prepSendFdIoOps((FileDescriptor) msg, writeMsgHdrMemory);
                writeId = registration.submit(ioUringIoOps);
                writeOpCode = Native.IORING_OP_SENDMSG;
                if (writeId == 0) {
                    MsgHdrMemory memory = writeMsgHdrMemory;
                    writeMsgHdrMemory = null;
                    memory.release();
                    return 0;
                }
                return 1;
            }
        } else if (IoUring.isSendZcSupported() && msg instanceof ByteBuf) {
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

        int fd = fd().intValue();
        IoRegistration registration = registration();
        final IoUringIoOps ops;
        if (msg instanceof IoUringFileRegion) {
            IoUringFileRegion fileRegion = (IoUringFileRegion) msg;
            try {
                fileRegion.open();
            } catch (IOException e) {
                this.handleWriteError(e);
                return 0;
            }
            ops = fileRegion.splice(fd);
        } else {
            ByteBuf buf = (ByteBuf) msg;
            long address = IoUring.memoryAddress(buf) + buf.readerIndex();
            int length = buf.readableBytes();
            short opsid = nextOpsId();

            ops = IoUringIoOps.newSend(fd, (byte) 0, 0, address, length, opsid);
        }
        byte opCode = ops.opcode();
        writeId = registration.submit(ops);
        writeOpCode = opCode;
        if (writeId == 0) {
            return 0;
        }
        return 1;
    }

    private int calculateRecvFlags(boolean first) {
        // Depending on if this is the first read or not we will use Native.MSG_DONTWAIT.
        // The idea is that if the socket is blocking we can do the first read in a blocking fashion
        // and so not need to also register POLLIN. As we can not 100 % sure if reads after the first will
        // be possible directly we schedule these with Native.MSG_DONTWAIT. This allows us to still be
        // able to signal the fireChannelReadComplete() in a timely manner and be consistent with other
        // transports.
        if (first) {
            return 0;
        }
        return Native.MSG_DONTWAIT;
    }

    private short calculateRecvIoPrio(boolean first, boolean socketIsEmpty) {
        // Depending on if socketIsEmpty is true we will arm the poll upfront and skip the initial transfer
        // attempt.
        // See https://github.com/axboe/liburing/wiki/io_uring-and-networking-in-2023#socket-state
        if (first) {
            // IORING_RECVSEND_POLL_FIRST and IORING_CQE_F_SOCK_NONEMPTY were added in the same release (5.19).
            // We need to check if it's supported as otherwise providing these would result in an -EINVAL.
            return socketIsEmpty && IoUring.isCqeFSockNonEmptySupported() ?
                    Native.IORING_RECVSEND_POLL_FIRST : 0;
        }
        return 0;
    }

    @Override
    protected int scheduleRead0(boolean first, boolean socketIsEmpty) {
        if (socket.protocolFamily() == SocketProtocolFamily.UNIX &&
                config.getReadMode() == DomainSocketReadMode.FILE_DESCRIPTORS) {
            return scheduleRecvReadFd();
        }
        assert readBuffer == null;
        assert readId == 0 : readId;
        final IoUringRecvByteAllocatorHandle allocHandle = (IoUringRecvByteAllocatorHandle) recvBufAllocHandle();

        if (bufferRing != null && bufferRing.isUsable()) {
            return scheduleReadProviderBuffer(bufferRing, first, socketIsEmpty);
        }

        // We either have no buffer ring configured or we force a recv without using a buffer ring.
        ByteBuf byteBuf = allocHandle.allocate(alloc());
        try {
            int fd = fd().intValue();
            IoRegistration registration = registration();
            short ioPrio = calculateRecvIoPrio(first, socketIsEmpty);
            int recvFlags = calculateRecvFlags(first);

            IoUringIoOps ops = IoUringIoOps.newRecv(fd, (byte) 0, ioPrio, recvFlags,
                    IoUring.memoryAddress(byteBuf) + byteBuf.writerIndex(), byteBuf.writableBytes(), nextOpsId());
            readId = registration.submit(ops);
            readOpCode = Native.IORING_OP_RECV;
            if (readId == 0) {
                return 0;
            }
            readBuffer = byteBuf;
            byteBuf = null;
            return 1;
        } finally {
            if (byteBuf != null) {
                byteBuf.release();
            }
        }
    }

    private int scheduleReadProviderBuffer(IoUringBufferRing bufferRing, boolean first, boolean socketIsEmpty) {
        short bgId = bufferRing.bufferGroupId();
        try {
            boolean multishot = IoUring.isRecvMultishotEnabled();
            byte flags = (byte) Native.IOSQE_BUFFER_SELECT;
            short ioPrio;
            final int recvFlags;
            if (multishot) {
                ioPrio = Native.IORING_RECV_MULTISHOT;
                recvFlags = 0;
            } else {
                // We should only use the calculate*() methods if this is not a multishot recv, as otherwise
                // the would be applied until the multishot will be re-armed.
                ioPrio = calculateRecvIoPrio(first, socketIsEmpty);
                recvFlags = calculateRecvFlags(first);
            }
            if (IoUring.isRecvsendBundleEnabled()) {
                // See https://github.com/axboe/liburing/wiki/
                // What's-new-with-io_uring-in-6.10#add-support-for-sendrecv-bundles
                ioPrio |= Native.IORING_RECVSEND_BUNDLE;
            }
            IoRegistration registration = registration();
            int fd = fd().intValue();
            IoUringIoOps ops = IoUringIoOps.newRecv(
                    fd, flags, ioPrio, recvFlags, 0,
                    0, nextOpsId(), bgId
            );
            readId = registration.submit(ops);
            readOpCode = Native.IORING_OP_RECV;
            if (readId == 0) {
                return 0;
            }
            if (multishot) {
                // Return -1 to signal we used multishot and so expect multiple recvComplete(...) calls.
                return -1;
            }
            return 1;
        } catch (IllegalArgumentException illegalArgumentException) {
            this.handleReadException(pipeline(), null, illegalArgumentException, false,
                    (IoUringRecvByteAllocatorHandle) recvBufAllocHandle());
            return 0;
        }
    }

    @Override
    protected void readComplete0(byte op, int res, int flags, short data, int outstanding) {
        if (op == Native.IORING_OP_RECVMSG) {
            readId = 0;
            if (res == Native.ERRNO_ECANCELED_NEGATIVE) {
                return;
            }
            final IoUringRecvByteAllocatorHandle allocHandle = (IoUringRecvByteAllocatorHandle) recvBufAllocHandle();
            final ChannelPipeline pipeline = pipeline();
            try {
                int nativeCallResult = res >= 0 ? res : Errors.ioResult("io_uring recvmsg", res);
                int nativeFd = readMsgHdrMemory.getScmRightsFd();
                allocHandle.lastBytesRead(nativeFd);
                allocHandle.incMessagesRead(1);
                pipeline.fireChannelRead(new FileDescriptor(nativeFd));
            } catch (Throwable throwable) {
                handleReadException(pipeline, null, throwable, false, allocHandle);
            } finally {
                allocHandle.readComplete();
                pipeline.fireChannelReadComplete();
            }
            return;
        }

        ByteBuf byteBuf = readBuffer;
        readBuffer = null;
        if (res == Native.ERRNO_ECANCELED_NEGATIVE) {
            readId = 0;
            // In case of cancellation we should reset the last used buffer ring to null as we will select a new one
            // when calling scheduleRead(..)
            if (byteBuf != null) {
                //recv without buffer ring
                byteBuf.release();
            }
            return;
        }
        boolean rearm = (flags & Native.IORING_CQE_F_MORE) == 0;
        boolean useBufferRing = (flags & Native.IORING_CQE_F_BUFFER) != 0;
        short bid = (short) (flags >> Native.IORING_CQE_BUFFER_SHIFT);
        boolean more = (flags & Native.IORING_CQE_F_BUF_MORE) != 0;

        boolean empty = socketIsEmpty(flags);
        if (rearm) {
            // Only reset if we don't use multi-shot or we need to re-arm because the multi-shot was cancelled.
            readId = 0;
        }

        boolean allDataRead = false;

        final IoUringRecvByteAllocatorHandle allocHandle = (IoUringRecvByteAllocatorHandle) recvBufAllocHandle();
        final ChannelPipeline pipeline = pipeline();

        try {
            if (res < 0) {
                if (res == Native.ERRNO_NOBUFS_NEGATIVE) {
                    // try to expand the buffer ring by adding more buffers to it if there is any space left.
                    if (!bufferRing.expand()) {
                        // We couldn't expand the ring anymore so notify the user that we did run out of buffers
                        // without the ability to expand it.
                        // If this happens to often the user should most likely increase the buffer ring size.
                        pipeline.fireUserEventTriggered(bufferRing.getExhaustedEvent());
                    }

                    // Let's trigger a read again without consulting the RecvByteBufAllocator.Handle as
                    // we can't count this as a "real" read operation.
                    // Because of how our BufferRing works we should have it filled again.
                    scheduleRead(allocHandle.isFirstRead());
                    return;
                }

                // If res is negative we should pass it to ioResult(...) which will either throw
                // or convert it to 0 if we could not read because the socket was not readable.
                allocHandle.lastBytesRead(ioResult("io_uring read", res));
            } else if (res > 0) {
                if (useBufferRing) {
                    // If RECVSEND_BUNDLE is used we need to do a bit more work here.
                    // In this case we might need to obtain multiple buffers out of the buffer ring as
                    // multiple of them might have been filled for one recv operation.
                    // See https://github.com/axboe/liburing/wiki/
                    // What's-new-with-io_uring-in-6.10#add-support-for-sendrecv-bundles
                    int read = res;
                    for (;;) {
                        int attemptedBytesRead = bufferRing.attemptedBytesRead(bid);
                        byteBuf = bufferRing.useBuffer(bid, read, more);
                        read -= byteBuf.readableBytes();
                        allocHandle.attemptedBytesRead(attemptedBytesRead);
                        allocHandle.lastBytesRead(byteBuf.readableBytes());

                        assert read >= 0;
                        if (read == 0) {
                            // Just break here, we will handle the byteBuf below and also fill the bufferRing
                            // if needed later.
                            break;
                        }
                        allocHandle.incMessagesRead(1);
                        pipeline.fireChannelRead(byteBuf);
                        byteBuf = null;
                        bid = bufferRing.nextBid(bid);
                        if (!allocHandle.continueReading()) {
                            // We should call fireChannelReadComplete() to mimic a normal read loop.
                            allocHandle.readComplete();
                            pipeline.fireChannelReadComplete();
                            allocHandle.reset(config());
                        }
                    }
                } else {
                    int attemptedBytesRead = byteBuf.writableBytes();
                    byteBuf.writerIndex(byteBuf.writerIndex() + res);
                    allocHandle.attemptedBytesRead(attemptedBytesRead);
                    allocHandle.lastBytesRead(res);
                }
            } else {
                // EOF which we signal with -1.
                allocHandle.lastBytesRead(-1);
            }
            if (allocHandle.lastBytesRead() <= 0) {
                // byteBuf might be null if we used a buffer ring.
                if (byteBuf != null) {
                    // nothing was read, release the buffer.
                    byteBuf.release();
                    byteBuf = null;
                }
                allDataRead = allocHandle.lastBytesRead() < 0;
                if (allDataRead) {
                    // There is nothing left to read as we received an EOF.
                    shutdownInput(true);
                }
                allocHandle.readComplete();
                pipeline.fireChannelReadComplete();
                return;
            }

            allocHandle.incMessagesRead(1);
            pipeline.fireChannelRead(byteBuf);
            byteBuf = null;
            scheduleNextRead(pipeline, allocHandle, rearm, empty);
        } catch (Throwable t) {
            handleReadException(pipeline, byteBuf, t, allDataRead, allocHandle);
        }
    }

    private void scheduleNextRead(ChannelPipeline pipeline, IoUringRecvByteAllocatorHandle allocHandle,
                                  boolean rearm, boolean empty) {
        if (allocHandle.continueReading() && !empty) {
            if (rearm) {
                // We only should schedule another read if we need to rearm.
                // See https://github.com/axboe/liburing/wiki/io_uring-and-networking-in-2023#multi-shot
                scheduleRead(false);
            }
        } else {
            // We did not fill the whole ByteBuf so we should break the "read loop" and try again later.
            allocHandle.readComplete();
            pipeline.fireChannelReadComplete();
        }
    }

    private void handleReadException(ChannelPipeline pipeline, ByteBuf byteBuf,
                                             Throwable cause, boolean allDataRead,
                                             IoUringRecvByteAllocatorHandle allocHandle) {
        if (byteBuf != null) {
            if (byteBuf.isReadable()) {
                pipeline.fireChannelRead(byteBuf);
            } else {
                byteBuf.release();
            }
        }
        allocHandle.readComplete();
        pipeline.fireChannelReadComplete();
        pipeline.fireExceptionCaught(cause);
        if (allDataRead || cause instanceof IOException) {
            shutdownInput(true);
        }
    }

    private boolean handleWriteCompleteFileRegion(ChannelOutboundBuffer channelOutboundBuffer,
                                                  IoUringFileRegion fileRegion, int res, short data) {
        try {
            if (res == Native.ERRNO_ECANCELED_NEGATIVE) {
                return true;
            }
            int result = res >= 0 ? res : ioResult("io_uring splice", res);
            if (result == 0 && fileRegion.count() > 0) {
                validateFileRegion(fileRegion.fileRegion, fileRegion.transfered());
                return false;
            }
            int progress = fileRegion.handleResult(result, data);
            if (progress == -1) {
                // Done with writing
                channelOutboundBuffer.remove();
            }
        } catch (Throwable cause) {
            handleWriteError(cause);
        }
        return true;
    }

    @Override
    boolean writeComplete0(byte op, int res, int flags, short data, int outstanding) {
        if ((flags & Native.IORING_CQE_F_NOTIF) == 0) {
            // We only want to reset these if IORING_CQE_F_NOTIF is not set.
            // If it's set we know this is only an extra notification for a write but we already handled
            // the write completions before.
            // See https://man7.org/linux/man-pages/man2/io_uring_enter.2.html section: IORING_OP_SEND_ZC
            writeId = 0;
            writeOpCode = 0;
        }
        ChannelOutboundBuffer channelOutboundBuffer = outboundBuffer();
        if (op == Native.IORING_OP_SEND_ZC || op == Native.IORING_OP_SENDMSG_ZC) {
            return handleWriteCompleteZeroCopy(op, channelOutboundBuffer, res, flags);
        }
        if (op == Native.IORING_OP_SENDMSG) {
            if (res == Native.ERRNO_ECANCELED_NEGATIVE) {
                return true;
            }
            try {
                int nativeCallResult = res >= 0 ? res : Errors.ioResult("io_uring sendmsg", res);
                if (nativeCallResult >= 0) {
                    channelOutboundBuffer.remove();
                }
            } catch (Throwable throwable) {
                handleWriteError(throwable);
            }
            return true;
        }

        Object current = channelOutboundBuffer.current();
        if (current instanceof IoUringFileRegion) {
            IoUringFileRegion fileRegion = (IoUringFileRegion) current;
            return handleWriteCompleteFileRegion(channelOutboundBuffer, fileRegion, res, data);
        }

        if (res >= 0) {
            channelOutboundBuffer.removeBytes(res);
        } else if (res == Native.ERRNO_ECANCELED_NEGATIVE) {
            return true;
        } else {
            try {
                if (ioResult("io_uring write", res) == 0) {
                    return false;
                }
            } catch (Throwable cause) {
                handleWriteError(cause);
            }
        }
        return true;
    }

    @Override
    protected void cancelOutstandingReads(IoRegistration registration, int numOutstandingReads) {
        if (readId != 0) {
            // Let's try to cancel outstanding reads as these might be submitted and waiting for data (via fastpoll).
            assert numOutstandingReads == 1 || numOutstandingReads == -1;
            IoUringIoOps ops = IoUringIoOps.newAsyncCancel((byte) 0, readId, readOpCode);
            long id = registration.submit(ops);
            assert id != 0;
            readId = 0;
        }
    }

    @Override
    protected void cancelOutstandingWrites(IoRegistration registration, int numOutstandingWrites) {
        if (writeId != 0) {
            // Let's try to cancel outstanding writes as these might be submitted and waiting to finish writing
            // (via fastpoll).
            assert numOutstandingWrites == 1;
            assert writeOpCode != 0;
            long id = registration.submit(IoUringIoOps.newAsyncCancel((byte) 0, writeId, writeOpCode));
            assert id != 0;
            writeId = 0;
        }
    }

    @Override
    protected boolean socketIsEmpty(int flags) {
        return IoUring.isCqeFSockNonEmptySupported() && (flags & Native.IORING_CQE_F_SOCK_NONEMPTY) == 0;
    }

    @Override
    protected boolean allowMultiShotPollIn() {
        if (socket.protocolFamily() == SocketProtocolFamily.UNIX) {
            // UNIX domain sockets do not support IORING_CQE_F_SOCK_NONEMPTY and POLL_ADD_MULTI is edge-triggered
            // so we should disable it
            return false;
        }
        return super.allowMultiShotPollIn();
    }

    @Override
    public ChannelConfig config() {
        return config;
    }

    private IoUringIoOps prepSendFdIoOps(FileDescriptor fileDescriptor, MsgHdrMemory msgHdrMemory) {
        msgHdrMemory.setScmRightsFd(fileDescriptor.intValue());
        return IoUringIoOps.newSendmsg(
                fd().intValue(), (byte) 0, 0, msgHdrMemory.address(), msgHdrMemory.idx());
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

    private int scheduleRecvReadFd() {
        // we can reuse the same memory for any fd
        // because we only submit one outstanding read
        if (readMsgHdrMemory == null) {
            readMsgHdrMemory = new MsgHdrMemory();
        }
        readMsgHdrMemory.prepRecvReadFd();
        IoRegistration registration = registration();
        IoUringIoOps ioUringIoOps = IoUringIoOps.newRecvmsg(
                fd().intValue(), (byte) 0, 0, readMsgHdrMemory.address(), readMsgHdrMemory.idx());
        readId = registration.submit(ioUringIoOps);
        readOpCode = Native.IORING_OP_RECVMSG;
        if (readId == 0) {
            MsgHdrMemory memory = readMsgHdrMemory;
            readMsgHdrMemory = null;
            memory.release();
            return 0;
        }
        return 1;
    }

    @Override
    protected void unregistered() {
        super.unregistered();
        if (readMsgHdrMemory != null) {
            readMsgHdrMemory.release();
            readMsgHdrMemory = null;
        }
        if (writeMsgHdrMemory != null) {
            writeMsgHdrMemory.release();
            writeMsgHdrMemory = null;
        }
    }

    @Override
    boolean isPollInFirst() {
        if (socket.protocolFamily() == SocketProtocolFamily.UNIX &&
                config.getReadMode() == DomainSocketReadMode.FILE_DESCRIPTORS) {
            return false;
        }
        return bufferRing == null || !bufferRing.isUsable();
    }

    @Override
    protected boolean isAllowHalfClosure() {
        return config.isAllowHalfClosure();
    }
}
