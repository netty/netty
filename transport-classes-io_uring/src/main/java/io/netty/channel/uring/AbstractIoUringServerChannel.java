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


import io.netty.channel.Channel;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.ServerChannel;
import io.netty.channel.unix.Buffer;
import io.netty.channel.unix.Errors;

import java.net.SocketAddress;
import java.nio.ByteBuffer;

import static io.netty.channel.unix.Errors.ERRNO_EAGAIN_NEGATIVE;
import static io.netty.channel.unix.Errors.ERRNO_EWOULDBLOCK_NEGATIVE;

abstract class AbstractIoUringServerChannel extends AbstractIoUringChannel implements ServerChannel {
    private static final ChannelMetadata METADATA = new ChannelMetadata(false, 16);

    private final ByteBuffer acceptedAddressMemory;
    private final ByteBuffer acceptedAddressLengthMemory;

    private final long acceptedAddressMemoryAddress;
    private final long acceptedAddressLengthMemoryAddress;

    private long acceptId;

    protected AbstractIoUringServerChannel(LinuxSocket socket, boolean active) {
        super(null, IoUring.isIOUringAcceptNoWaitSupported() ? LinuxSocket.makeBlocking(socket) : socket, active);

        acceptedAddressMemory = Buffer.allocateDirectWithNativeOrder(Native.SIZEOF_SOCKADDR_STORAGE);
        acceptedAddressMemoryAddress = Buffer.memoryAddress(acceptedAddressMemory);
        acceptedAddressLengthMemory = Buffer.allocateDirectWithNativeOrder(Long.BYTES);
        // Needs to be initialized to the size of acceptedAddressMemory.
        // See https://man7.org/linux/man-pages/man2/accept.2.html
        acceptedAddressLengthMemory.putLong(0, Native.SIZEOF_SOCKADDR_STORAGE);
        acceptedAddressLengthMemoryAddress = Buffer.memoryAddress(acceptedAddressLengthMemory);
    }

    @Override
    public final ChannelMetadata metadata() {
        return METADATA;
    }

    @Override
    protected final void doClose() throws Exception {
        super.doClose();
    }

    @Override
    protected final AbstractUringUnsafe newUnsafe() {
        return new UringServerChannelUnsafe();
    }

    @Override
    protected final void doWrite(ChannelOutboundBuffer in) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected final void cancelOutstandingReads(IoUringIoRegistration registration, int numOutstandingReads) {
        if (acceptId != 0) {
            assert numOutstandingReads == 1;
            int fd = fd().intValue();
            IoUringIoOps ops = IoUringIoOps.newAsyncCancel(
                    fd, flags((byte) 0), acceptId, Native.IORING_OP_ACCEPT);
            registration.submit(ops);
        } else {
            assert numOutstandingReads == 0;
        }
    }

    @Override
    protected final void cancelOutstandingWrites(IoUringIoRegistration registration, int numOutstandingWrites) {
        assert numOutstandingWrites == 0;
    }

    abstract Channel newChildChannel(
            int fd, long acceptedAddressMemoryAddress, long acceptedAddressLengthMemoryAddress) throws Exception;

    private final class UringServerChannelUnsafe extends AbstractIoUringChannel.AbstractUringUnsafe {

        @Override
        protected int scheduleWriteMultiple(ChannelOutboundBuffer in) {
            throw new UnsupportedOperationException();
        }

        @Override
        protected int scheduleWriteSingle(Object msg) {
            throw new UnsupportedOperationException();
        }

        @Override
        boolean writeComplete0(byte op, int res, int flags, short data, int outstanding) {
            throw new UnsupportedOperationException();
        }

        @Override
        protected int scheduleRead0(boolean first, boolean socketIsEmpty) {
            assert acceptId == 0;
            final IoUringRecvByteAllocatorHandle allocHandle = recvBufAllocHandle();
            allocHandle.attemptedBytesRead(1);

            int fd = fd().intValue();
            IoUringIoRegistration registration = registration();

            // Depending on if socketIsEmpty is true we will arm the poll upfront and skip the initial transfer
            // attempt.
            // See https://github.com/axboe/liburing/wiki/io_uring-and-networking-in-2023#socket-state
            //
            // Depending on if this is the first read or not we will use Native.IORING_ACCEPT_DONT_WAIT.
            // The idea is that if the socket is blocking we can do the first read in a blocking fashion
            // and so not need to also register POLLIN. As we can not 100 % sure if reads after the first will
            // be possible directly we schedule these with Native.IORING_ACCEPT_DONT_WAIT. This allows us to still be
            // able to signal the fireChannelReadComplete() in a timely manner and be consistent with other
            // transports.
            final short ioPrio;

            if (first) {
                ioPrio = socketIsEmpty ? Native.IORING_ACCEPT_POLL_FIRST : 0;
            } else {
                ioPrio = Native.IORING_ACCEPT_DONTWAIT;
            }
            // See https://github.com/axboe/liburing/wiki/What's-new-with-io_uring-in-6.10#improvements-for-accept
            IoUringIoOps ops = IoUringIoOps.newAccept(fd, flags((byte) 0), 0, ioPrio,
                    acceptedAddressMemoryAddress, acceptedAddressLengthMemoryAddress, nextOpsId());
            acceptId = registration.submit(ops);
            if (acceptId == 0) {
                return 0;
            }
            return 1;
        }

        @Override
        protected void readComplete0(byte op, int res, int flags, short data, int outstanding) {
            assert acceptId != 0;
            acceptId = 0;
            final IoUringRecvByteAllocatorHandle allocHandle =
                    (IoUringRecvByteAllocatorHandle) unsafe()
                            .recvBufAllocHandle();
            final ChannelPipeline pipeline = pipeline();
            allocHandle.lastBytesRead(res);

            if (res >= 0) {
                allocHandle.incMessagesRead(1);
                try {
                    Channel channel = newChildChannel(
                            res, acceptedAddressMemoryAddress, acceptedAddressLengthMemoryAddress);
                    pipeline.fireChannelRead(channel);

                    if (allocHandle.continueReading() &&
                            // If IORING_CQE_F_SOCK_NONEMPTY is supported we should check for it first before
                            // trying to schedule a read. If it's supported and not part of the flags we know for sure
                            // that the next read (which would be using Native.IORING_ACCEPT_DONTWAIT) will complete
                            // without be able to read any data. This is useless work and we can skip it.
                            //
                            // See https://github.com/axboe/liburing/wiki/What's-new-with-io_uring-in-6.10
                            !socketIsEmpty(flags)) {
                        scheduleRead(false);
                    } else {
                        allocHandle.readComplete();
                        pipeline.fireChannelReadComplete();
                    }
                } catch (Throwable cause) {
                    allocHandle.readComplete();
                    pipeline.fireChannelReadComplete();
                    pipeline.fireExceptionCaught(cause);
                }
            } else if (res != Native.ERRNO_ECANCELED_NEGATIVE) {
                allocHandle.readComplete();
                pipeline.fireChannelReadComplete();
                // Check if we did fail because there was nothing to accept atm.
                if (res != ERRNO_EAGAIN_NEGATIVE && res != ERRNO_EWOULDBLOCK_NEGATIVE) {
                    // Something bad happened. Convert to an exception.
                    pipeline.fireExceptionCaught(Errors.newIOException("io_uring accept", res));
                }
            }
        }

        @Override
        public void connect(final SocketAddress remoteAddress, final SocketAddress localAddress,
                            final ChannelPromise promise) {
            promise.setFailure(new UnsupportedOperationException());
        }

        @Override
        protected void freeResourcesNow(IoUringIoRegistration reg) {
            super.freeResourcesNow(reg);
            Buffer.free(acceptedAddressMemory);
            Buffer.free(acceptedAddressLengthMemory);
        }
    }

    @Override
    protected boolean socketIsEmpty(int flags) {
        // IORING_CQE_F_SOCK_NONEMPTY is used for accept since IORING_ACCEPT_DONTWAIT was added.
        // See https://github.com/axboe/liburing/wiki/What's-new-with-io_uring-in-6.10
        return IoUring.isIOUringAcceptNoWaitSupported() &&
                IoUring.isIOUringCqeFSockNonEmptySupported() && (flags & Native.IORING_CQE_F_SOCK_NONEMPTY) == 0;
    }
}

