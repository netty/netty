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
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultFileRegion;
import io.netty.channel.EventLoop;
import io.netty.channel.socket.DuplexChannel;
import io.netty.channel.unix.IovArray;
import io.netty.channel.unix.Limits;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.IOException;
import java.net.SocketAddress;

import static io.netty.channel.unix.Errors.ioResult;

abstract class AbstractIoUringStreamChannel extends AbstractIoUringChannel implements DuplexChannel {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(AbstractIoUringStreamChannel.class);
    private static final ChannelMetadata METADATA = new ChannelMetadata(false, 16);

    // Store the opCode so we know if we used WRITE or WRITEV.
    private byte writeOpCode;

    // Keep track of the ids used for write and read so we can cancel these when needed.
    private long writeId;
    private long readId;

    AbstractIoUringStreamChannel(Channel parent, LinuxSocket socket, boolean active) {
        // Use a blocking fd, we can make use of fastpoll.
        super(parent, LinuxSocket.makeBlocking(socket), active);
    }

    AbstractIoUringStreamChannel(Channel parent, LinuxSocket socket, SocketAddress remote) {
        // Use a blocking fd, we can make use of fastpoll.
        super(parent, LinuxSocket.makeBlocking(socket), remote);
    }

    @Override
    public ChannelMetadata metadata() {
        return METADATA;
    }

    @Override
    protected final AbstractUringUnsafe newUnsafe() {
        return new IoUringStreamUnsafe();
    }

    @Override
    public final ChannelFuture shutdown() {
        return shutdown(newPromise());
    }

    @Override
    public final ChannelFuture shutdown(final ChannelPromise promise) {
        ChannelFuture shutdownOutputFuture = shutdownOutput();
        if (shutdownOutputFuture.isDone()) {
            shutdownOutputDone(shutdownOutputFuture, promise);
        } else {
            shutdownOutputFuture.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(final ChannelFuture shutdownOutputFuture) throws Exception {
                    shutdownOutputDone(shutdownOutputFuture, promise);
                }
            });
        }
        return promise;
    }

    @Override
    protected final void doShutdownOutput() throws Exception {
        socket.shutdown(false, true);
    }

    private void shutdownInput0(final ChannelPromise promise) {
        try {
            socket.shutdown(true, false);
            promise.setSuccess();
        } catch (Throwable cause) {
            promise.setFailure(cause);
        }
    }

    @Override
    public final boolean isOutputShutdown() {
        return socket.isOutputShutdown();
    }

    @Override
    public final boolean isInputShutdown() {
        return socket.isInputShutdown();
    }

    @Override
    public final boolean isShutdown() {
        return socket.isShutdown();
    }

    @Override
    public final ChannelFuture shutdownOutput() {
        return shutdownOutput(newPromise());
    }

    @Override
    public final ChannelFuture shutdownOutput(final ChannelPromise promise) {
        EventLoop loop = eventLoop();
        if (loop.inEventLoop()) {
            ((AbstractUnsafe) unsafe()).shutdownOutput(promise);
        } else {
            loop.execute(new Runnable() {
                @Override
                public void run() {
                    ((AbstractUnsafe) unsafe()).shutdownOutput(promise);
                }
            });
        }

        return promise;
    }

    @Override
    public final ChannelFuture shutdownInput() {
        return shutdownInput(newPromise());
    }

    @Override
    public final ChannelFuture shutdownInput(final ChannelPromise promise) {
        EventLoop loop = eventLoop();
        if (loop.inEventLoop()) {
            shutdownInput0(promise);
        } else {
            loop.execute(new Runnable() {
                @Override
                public void run() {
                    shutdownInput0(promise);
                }
            });
        }
        return promise;
    }

    private void shutdownOutputDone(final ChannelFuture shutdownOutputFuture, final ChannelPromise promise) {
        ChannelFuture shutdownInputFuture = shutdownInput();
        if (shutdownInputFuture.isDone()) {
            shutdownDone(shutdownOutputFuture, shutdownInputFuture, promise);
        } else {
            shutdownInputFuture.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture shutdownInputFuture) throws Exception {
                    shutdownDone(shutdownOutputFuture, shutdownInputFuture, promise);
                }
            });
        }
    }

    private static void shutdownDone(ChannelFuture shutdownOutputFuture,
                                     ChannelFuture shutdownInputFuture,
                                     ChannelPromise promise) {
        Throwable shutdownOutputCause = shutdownOutputFuture.cause();
        Throwable shutdownInputCause = shutdownInputFuture.cause();
        if (shutdownOutputCause != null) {
            if (shutdownInputCause != null) {
                logger.info("Exception suppressed because a previous exception occurred.",
                             shutdownInputCause);
            }
            promise.setFailure(shutdownOutputCause);
        } else if (shutdownInputCause != null) {
            promise.setFailure(shutdownInputCause);
        } else {
            promise.setSuccess();
        }
    }

    @Override
    protected final void doRegister(ChannelPromise promise) {
        super.doRegister(promise);
        promise.addListener(f -> {
            if (f.isSuccess()) {
                if (active) {
                    // Register for POLLRDHUP if this channel is already considered active.
                    schedulePollRdHup();
                }
            }
        });
    }

    @Override
    protected Object filterOutboundMessage(Object msg) {
        // Since we cannot use synchronous sendfile,
        // the channel can only support DefaultFileRegion instead of FileRegion.
        if (IoUring.isIOUringSpliceSupported() && msg instanceof DefaultFileRegion) {
            return new IoUringFileRegion((DefaultFileRegion) msg);
        }

        return super.filterOutboundMessage(msg);
    }

    private final class IoUringStreamUnsafe extends AbstractUringUnsafe {

        private ByteBuf readBuffer;
        private IovArray iovArray;

        @Override
        protected int scheduleWriteMultiple(ChannelOutboundBuffer in) {
            assert iovArray == null;
            assert writeId == 0;
            int numElements = Math.min(in.size(), Limits.IOV_MAX);
            ByteBuf iovArrayBuffer = alloc().directBuffer(numElements * IovArray.IOV_SIZE);
            iovArray = new IovArray(iovArrayBuffer);
            try {
                int offset = iovArray.count();
                in.forEachFlushedMessage(iovArray);

                int fd = fd().intValue();
                IoUringIoRegistration registration = registration();
                IoUringIoOps ops = IoUringIoOps.newWritev(fd, 0, 0, iovArray.memoryAddress(offset),
                        iovArray.count() - offset, nextOpsId());
                byte opCode = ops.opcode();
                writeId = registration.submit(ops);
                writeOpCode = opCode;
                if (writeId == 0) {
                    return 0;
                }
            } catch (Exception e) {
                iovArray.release();
                iovArray = null;

                // This should never happen, anyway fallback to single write.
                scheduleWriteSingle(in.current());
            }
            return 1;
        }

        @Override
        protected int scheduleWriteSingle(Object msg) {
            assert iovArray == null;
            assert writeId == 0;

            int fd = fd().intValue();
            IoUringIoRegistration registration = registration();
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
                ops = IoUringIoOps.newWrite(fd, 0, 0,
                        buf.memoryAddress() + buf.readerIndex(), buf.readableBytes(), nextOpsId());
            }

            byte opCode = ops.opcode();
            writeId = registration.submit(ops);
            writeOpCode = opCode;
            if (writeId == 0) {
                return 0;
            }
            return 1;
        }

        @Override
        protected int scheduleRead0(boolean first) {
            assert readBuffer == null;
            assert readId == 0;

            final IoUringRecvByteAllocatorHandle allocHandle = recvBufAllocHandle();
            ByteBuf byteBuf = allocHandle.allocate(alloc());
            try {
                allocHandle.attemptedBytesRead(byteBuf.writableBytes());
                int fd = fd().intValue();
                IoUringIoRegistration registration = registration();
                // Depending on if this is the first read or not we will use Native.MSG_DONTWAIT.
                // The idea is that if the socket is blocking we can do the first read in a blocking fashion
                // and so not need to also register POLLIN. As we can not 100 % sure if reads after the first will
                // be possible directly we schedule these with Native.MSG_DONTWAIT. This allows us to still be
                // able to signal the fireChannelReadComplete() in a timely manner and be consistent with other
                // transports.
                IoUringIoOps ops = IoUringIoOps.newRecv(fd, 0, first ? 0 : Native.MSG_DONTWAIT,
                        byteBuf.memoryAddress() + byteBuf.writerIndex(), byteBuf.writableBytes(), nextOpsId());
                readId = registration.submit(ops);
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

        @Override
        protected void readComplete0(byte op, int res, int flags, short data, int outstanding) {
            assert readId != 0;
            readId = 0;
            boolean allDataRead = false;

            final IoUringRecvByteAllocatorHandle allocHandle = recvBufAllocHandle();
            final ChannelPipeline pipeline = pipeline();
            ByteBuf byteBuf = this.readBuffer;
            this.readBuffer = null;
            assert byteBuf != null;

            try {
                if (res < 0) {
                    if (res == Native.ERRNO_ECANCELED_NEGATIVE) {
                        byteBuf.release();
                        return;
                    }
                    // If res is negative we should pass it to ioResult(...) which will either throw
                    // or convert it to 0 if we could not read because the socket was not readable.
                    allocHandle.lastBytesRead(ioResult("io_uring read", res));
                } else if (res > 0) {
                    byteBuf.writerIndex(byteBuf.writerIndex() + res);
                    allocHandle.lastBytesRead(res);
                } else {
                    // EOF which we signal with -1.
                    allocHandle.lastBytesRead(-1);
                }
                if (allocHandle.lastBytesRead() <= 0) {
                    // nothing was read, release the buffer.
                    byteBuf.release();
                    byteBuf = null;
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
                if (allocHandle.continueReading() &&
                        // If IORING_CQE_F_SOCK_NONEMPTY is supported we should check for it first before
                        // trying to schedule a read. If it's supported and not part of the flags we know for sure
                        // that the next read (which would be using Native.MSG_DONTWAIT) will complete without
                        // be able to read any data. This is useless work and we can skip it.
                        (!IoUring.isIOUringCqeFSockNonEmptySupported() ||
                        (flags & Native.IORING_CQE_F_SOCK_NONEMPTY) != 0)) {
                    // Let's schedule another read.
                    scheduleRead(false);
                } else {
                    // We did not fill the whole ByteBuf so we should break the "read loop" and try again later.
                    allocHandle.readComplete();
                    pipeline.fireChannelReadComplete();
                }
            } catch (Throwable t) {
                handleReadException(pipeline, byteBuf, t, allDataRead, allocHandle);
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

        @Override
        boolean writeComplete0(byte op, int res, int flags, short data, int outstanding) {
            assert writeId != 0;
            writeId = 0;
            writeOpCode = 0;

            ChannelOutboundBuffer channelOutboundBuffer = unsafe().outboundBuffer();
            Object current = channelOutboundBuffer.current();
            if (current instanceof IoUringFileRegion) {
                IoUringFileRegion fileRegion = (IoUringFileRegion) current;
                try {
                    int result = res >= 0 ? res : ioResult("io_uring splice", res);
                    if (result == 0 && fileRegion.count() > 0) {
                        validateFileRegion(fileRegion.fileRegion, fileRegion.transfered());
                        return false;
                    }
                    int progress = fileRegion.handleResult(result, data);
                    if (progress == -1) {
                        // Done with writing
                        channelOutboundBuffer.remove();
                    } else if (progress > 0) {
                        channelOutboundBuffer.progress(progress);
                    }
                } catch (Throwable cause) {
                    handleWriteError(cause);
                }
                return true;
            }

            IovArray iovArray = this.iovArray;
            if (iovArray != null) {
                this.iovArray = null;
                iovArray.release();
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
        protected void freeResourcesNow(IoUringIoRegistration reg) {
            super.freeResourcesNow(reg);
            assert readBuffer == null;
        }
    }

    @Override
    protected final void cancelOutstandingReads(IoUringIoRegistration registration, int numOutstandingReads) {
        if (readId != 0) {
            // Let's try to cancel outstanding reads as these might be submitted and waiting for data (via fastpoll).
            assert numOutstandingReads == 1;
            int fd = fd().intValue();
            IoUringIoOps ops = IoUringIoOps.newAsyncCancel(fd, 0, readId, Native.IORING_OP_RECV);
            registration.submit(ops);
        } else {
            assert numOutstandingReads == 0;
        }
    }

    @Override
    protected final void cancelOutstandingWrites(IoUringIoRegistration registration, int numOutstandingWrites) {
        if (writeId != 0) {
            // Let's try to cancel outstanding writes as these might be submitted and waiting to finish writing
            // (via fastpoll).
            assert numOutstandingWrites == 1;
            assert writeOpCode != 0;
            int fd = fd().intValue();
            registration.submit(IoUringIoOps.newAsyncCancel(fd, 0, writeId, writeOpCode));
        } else {
            assert numOutstandingWrites == 0;
        }
    }
}
