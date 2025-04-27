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
import io.netty.channel.IoRegistration;
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

    // The configured buffer ring if any
    private IoUringBufferRing bufferRing;

    AbstractIoUringStreamChannel(Channel parent, LinuxSocket socket, boolean active) {
        super(parent, socket, active);
    }

    AbstractIoUringStreamChannel(Channel parent, LinuxSocket socket, SocketAddress remote) {
        super(parent, socket, remote);
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
        ChannelPromise registerPromise = this.newPromise();
        // Ensure that the buffer group is properly set before channel::read
        registerPromise.addListener(f -> {
            if (f.isSuccess()) {
               try {
                   short bgid = ((IoUringStreamChannelConfig) config()).getBufferGroupId();
                   if (bgid >= 0) {
                       final IoUringIoHandler ioUringIoHandler = registration().attachment();
                       bufferRing = ioUringIoHandler.findBufferRing(bgid);
                   }
                   if (active) {
                       // Register for POLLRDHUP if this channel is already considered active.
                       schedulePollRdHup();
                   }
               } finally {
                   promise.setSuccess();
               }
            } else {
                promise.setFailure(f.cause());
            }
        });

        super.doRegister(registerPromise);
    }

    @Override
    protected Object filterOutboundMessage(Object msg) {
        // Since we cannot use synchronous sendfile,
        // the channel can only support DefaultFileRegion instead of FileRegion.
        if (IoUring.isSpliceSupported() && msg instanceof DefaultFileRegion) {
            return new IoUringFileRegion((DefaultFileRegion) msg);
        }

        return super.filterOutboundMessage(msg);
    }

    private final class IoUringStreamUnsafe extends AbstractUringUnsafe {

        private ByteBuf readBuffer;

        @Override
        protected int scheduleWriteMultiple(ChannelOutboundBuffer in) {
            assert writeId == 0;
            int numElements = Math.min(in.size(), Limits.IOV_MAX);
            IoUringIoHandler handler = registration().attachment();
            IovArray iovArray = handler.iovArray();
            try {
                int offset = iovArray.count();
                in.forEachFlushedMessage(iovArray);

                int fd = fd().intValue();
                IoRegistration registration = registration();
                IoUringIoOps ops = IoUringIoOps.newWritev(fd, (byte) 0, 0, iovArray.memoryAddress(offset),
                        iovArray.count() - offset, nextOpsId());
                byte opCode = ops.opcode();
                writeId = registration.submit(ops);
                writeOpCode = opCode;
                if (writeId == 0) {
                    return 0;
                }
            } catch (Exception e) {
                // This should never happen, anyway fallback to single write.
                scheduleWriteSingle(in.current());
            }
            return 1;
        }

        @Override
        protected int scheduleWriteSingle(Object msg) {
            assert writeId == 0;

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
                ops = IoUringIoOps.newWrite(fd, (byte) 0, 0,
                        IoUring.memoryAddress(buf) + buf.readerIndex(), buf.readableBytes(), nextOpsId());
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
            assert readBuffer == null;
            assert readId == 0 : readId;
            final IoUringRecvByteAllocatorHandle allocHandle = recvBufAllocHandle();

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
                if (readId == 0) {
                    return 0;
                }
                if (multishot) {
                    // Return -1 to signal we used multishot and so expect multiple recvComplete(...) calls.
                    return -1;
                }
                return 1;
            } catch (IllegalArgumentException illegalArgumentException) {
                this.handleReadException(pipeline(), null, illegalArgumentException, false, recvBufAllocHandle());
                return 0;
            }
        }

        @Override
        protected void readComplete0(byte op, int res, int flags, short data, int outstanding) {
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

            final IoUringRecvByteAllocatorHandle allocHandle = recvBufAllocHandle();
            final ChannelPipeline pipeline = pipeline();

            try {
                if (res < 0) {
                    if (res == Native.ERRNO_NOBUFS_NEGATIVE) {
                        // recv with provider buffer failed, Fire the BufferRingExhaustedEvent to notify users.
                        // About the failure. If this happens to often the user should most likely increase the
                        // buffer ring size.
                        pipeline.fireUserEventTriggered(bufferRing.getExhaustedEvent());

                        // try to expand the buffer ring by adding more buffers to it if there is any space left.
                        bufferRing.expand();

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

                        if (IoUring.isRecvsendBundleEnabled()) {
                            // If RECVSEND_BUNDLE is supported we need to do a bit more work here.
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

                                // Fill a new buffer for the bid after we fired the buffer through the pipeline.
                                // We do it only after we called fireChannelRead(...) as there is a good chance
                                // that the user will have released the buffer. In this case we reduce memory usage.
                                bufferRing.fillBuffer();
                                bid = bufferRing.nextBid(bid);
                                if (!allocHandle.continueReading()) {
                                    // We should call fireChannelReadComplete() to mimic a normal read loop.
                                    allocHandle.readComplete();
                                    pipeline.fireChannelReadComplete();
                                    allocHandle.reset(config());
                                }
                            }
                        } else {
                            int attemptedBytesRead = bufferRing.attemptedBytesRead(bid);
                            byteBuf = bufferRing.useBuffer(bid, res, more);
                            allocHandle.attemptedBytesRead(attemptedBytesRead);
                            allocHandle.lastBytesRead(res);
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
                if (useBufferRing && !more) {
                    // Fill a new buffer for the bid after we fired the buffer through the pipeline.
                    // We do it only after we called fireChannelRead(...) as there is a good chance
                    // that the user will have released the buffer. In this case we reduce memory usage.
                    bufferRing.fillBuffer();
                }
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

        @Override
        boolean writeComplete0(byte op, int res, int flags, short data, int outstanding) {
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
        protected void freeResourcesNow(IoRegistration reg) {
            super.freeResourcesNow(reg);
            assert readBuffer == null;
        }
    }

    @Override
    protected final void cancelOutstandingReads(IoRegistration registration, int numOutstandingReads) {
        if (readId != 0) {
            // Let's try to cancel outstanding reads as these might be submitted and waiting for data (via fastpoll).
            assert numOutstandingReads == 1 || numOutstandingReads == -1;
            IoUringIoOps ops = IoUringIoOps.newAsyncCancel((byte) 0, readId, Native.IORING_OP_RECV);
            long id = registration.submit(ops);
            assert id != 0;
            readId = 0;
        }
    }

    @Override
    protected final void cancelOutstandingWrites(IoRegistration registration, int numOutstandingWrites) {
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
    protected void submitAndRunNow() {
        if (writeId != 0) {
            // Force a submit and processing of the completions to ensure we drain the outbound buffer and
            // send the data to the remote peer.
            ((IoUringIoHandler) registration().attachment()).submitAndRunNow(writeId);
        }
        super.submitAndRunNow();
    }

    @Override
    boolean isPollInFirst() {
        return bufferRing == null || !bufferRing.isUsable();
    }
}
