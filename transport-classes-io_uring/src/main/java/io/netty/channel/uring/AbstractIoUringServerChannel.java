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
import io.netty.channel.IoRegistration;
import io.netty.channel.ServerChannel;
import io.netty.channel.unix.Buffer;
import io.netty.channel.unix.Errors;

import java.net.SocketAddress;
import java.nio.ByteBuffer;

import static io.netty.channel.unix.Errors.ERRNO_EAGAIN_NEGATIVE;
import static io.netty.channel.unix.Errors.ERRNO_EWOULDBLOCK_NEGATIVE;

abstract class AbstractIoUringServerChannel extends AbstractIoUringChannel implements ServerChannel {
    private static final ChannelMetadata METADATA = new ChannelMetadata(false, 16);

    private static final class AcceptedAddressMemory {
        private final ByteBuffer acceptedAddressMemory;
        private final ByteBuffer acceptedAddressLengthMemory;
        private final long acceptedAddressMemoryAddress;
        private final long acceptedAddressLengthMemoryAddress;

        AcceptedAddressMemory() {
            acceptedAddressMemory = Buffer.allocateDirectWithNativeOrder(Native.SIZEOF_SOCKADDR_STORAGE);
            acceptedAddressMemoryAddress = Buffer.memoryAddress(acceptedAddressMemory);
            acceptedAddressLengthMemory = Buffer.allocateDirectWithNativeOrder(Long.BYTES);
            // Needs to be initialized to the size of acceptedAddressMemory.
            // See https://man7.org/linux/man-pages/man2/accept.2.html
            acceptedAddressLengthMemory.putLong(0, Native.SIZEOF_SOCKADDR_STORAGE);
            acceptedAddressLengthMemoryAddress = Buffer.memoryAddress(acceptedAddressLengthMemory);
        }

        void free() {
            Buffer.free(acceptedAddressMemory);
            Buffer.free(acceptedAddressLengthMemory);
        }
    }
    private final AcceptedAddressMemory acceptedAddressMemory;
    private long acceptId;

    protected AbstractIoUringServerChannel(LinuxSocket socket, boolean active) {
        super(null, socket, active);

        // We can only depend on the accepted address if multi-shot is not used.
        // From the manpage:
        //       The multishot variants allow an application to issue a single
        //       accept request, which will repeatedly trigger a CQE when a
        //       connection request comes in. Like other multishot type requests,
        //       the application should look at the CQE flags and see if
        //       IORING_CQE_F_MORE is set on completion as an indication of whether
        //       or not the accept request will generate further CQEs. Note that
        //       for the multishot variants, setting addr and addrlen may not make
        //       a lot of sense, as the same value would be used for every accepted
        //       connection. This means that the data written to addr may be
        //       overwritten by a new connection before the application has had
        //       time to process a past connection. If the application knows that a
        //       new connection cannot come in before a previous one has been
        //       processed, it may be used as expected.
        if (IoUring.isAcceptMultishotEnabled()) {
            acceptedAddressMemory = null;
        } else {
            acceptedAddressMemory = new AcceptedAddressMemory();
        }
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
    protected final void cancelOutstandingReads(IoRegistration registration, int numOutstandingReads) {
        if (acceptId != 0) {
            assert numOutstandingReads == 1 || numOutstandingReads == -1;
            IoUringIoOps ops = IoUringIoOps.newAsyncCancel((byte) 0, acceptId, Native.IORING_OP_ACCEPT);
            registration.submit(ops);
            acceptId = 0;
        } else {
            assert numOutstandingReads == 0 || numOutstandingReads == -1;
        }
    }

    @Override
    protected final void cancelOutstandingWrites(IoRegistration registration, int numOutstandingWrites) {
        assert numOutstandingWrites == 0;
    }

    abstract Channel newChildChannel(
            int fd, ByteBuffer acceptedAddressMemory) throws Exception;

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
            IoRegistration registration = registration();

            final short ioPrio;

            final long acceptedAddressMemoryAddress;
            final long acceptedAddressLengthMemoryAddress;
            if (IoUring.isAcceptMultishotEnabled()) {
                // Let's use multi-shot accept to reduce overhead.
                ioPrio = Native.IORING_ACCEPT_MULTISHOT;
                acceptedAddressMemoryAddress = 0;
                acceptedAddressLengthMemoryAddress = 0;
            } else {
                // Depending on if socketIsEmpty is true we will arm the poll upfront and skip the initial transfer
                // attempt.
                // See https://github.com/axboe/liburing/wiki/io_uring-and-networking-in-2023#socket-state
                //
                // Depending on if this is the first read or not we will use Native.IORING_ACCEPT_DONT_WAIT.
                // The idea is that if the socket is blocking we can do the first read in a blocking fashion
                // and so not need to also register POLLIN. As we can not 100 % sure if reads after the first will
                // be possible directly we schedule these with Native.IORING_ACCEPT_DONT_WAIT. This allows us to still
                // be able to signal the fireChannelReadComplete() in a timely manner and be consistent with other
                // transports.
                //
                // IORING_ACCEPT_POLL_FIRST and IORING_ACCEPT_DONTWAIT were added in the same release.
                // We need to check if its supported as otherwise providing these would result in an -EINVAL.
                if (IoUring.isAcceptNoWaitSupported()) {
                    if (first) {
                        ioPrio = socketIsEmpty ? Native.IORING_ACCEPT_POLL_FIRST : 0;
                    } else {
                        ioPrio = Native.IORING_ACCEPT_DONTWAIT;
                    }
                } else {
                    ioPrio = 0;
                }

                assert acceptedAddressMemory != null;
                acceptedAddressMemoryAddress = acceptedAddressMemory.acceptedAddressMemoryAddress;
                acceptedAddressLengthMemoryAddress = acceptedAddressMemory.acceptedAddressLengthMemoryAddress;
            }

            // See https://github.com/axboe/liburing/wiki/What's-new-with-io_uring-in-6.10#improvements-for-accept
            IoUringIoOps ops = IoUringIoOps.newAccept(fd, (byte) 0, 0, ioPrio,
                    acceptedAddressMemoryAddress, acceptedAddressLengthMemoryAddress, nextOpsId());
            acceptId = registration.submit(ops);
            if (acceptId == 0) {
                return 0;
            }
            if ((ioPrio & Native.IORING_ACCEPT_MULTISHOT) != 0) {
                // Let's return -1 to signal that we used multi-shot.
                return -1;
            }
            return 1;
        }

        @Override
        protected void readComplete0(byte op, int res, int flags, short data, int outstanding) {
            if (res == Native.ERRNO_ECANCELED_NEGATIVE) {
                acceptId = 0;
                return;
            }
            boolean rearm = (flags & Native.IORING_CQE_F_MORE) == 0;
            if (rearm) {
                // Only reset if we don't use multi-shot or we need to re-arm because the multi-shot was cancelled.
                acceptId = 0;
            }
            final IoUringRecvByteAllocatorHandle allocHandle =
                    (IoUringRecvByteAllocatorHandle) unsafe()
                            .recvBufAllocHandle();
            final ChannelPipeline pipeline = pipeline();
            allocHandle.lastBytesRead(res);

            if (res >= 0) {
                allocHandle.incMessagesRead(1);
                final ByteBuffer acceptedAddressBuffer;
                final long acceptedAddressLengthMemoryAddress;
                if (acceptedAddressMemory == null) {
                    acceptedAddressBuffer = null;
                } else {
                    acceptedAddressBuffer = acceptedAddressMemory.acceptedAddressMemory;
                }
                try {
                    Channel channel = newChildChannel(res, acceptedAddressBuffer);
                    pipeline.fireChannelRead(channel);

                    if (allocHandle.continueReading() &&
                            // If IORING_CQE_F_SOCK_NONEMPTY is supported we should check for it first before
                            // trying to schedule a read. If it's supported and not part of the flags we know for sure
                            // that the next read (which would be using Native.IORING_ACCEPT_DONTWAIT) will complete
                            // without be able to read any data. This is useless work and we can skip it.
                            //
                            // See https://github.com/axboe/liburing/wiki/What's-new-with-io_uring-in-6.10
                            !socketIsEmpty(flags)) {
                        if (rearm) {
                            // We only should schedule another read if we need to rearm.
                            // See https://github.com/axboe/liburing/wiki/io_uring-and-networking-in-2023#multi-shot
                            scheduleRead(false);
                        }
                    } else {
                        allocHandle.readComplete();
                        pipeline.fireChannelReadComplete();
                    }
                } catch (Throwable cause) {
                    allocHandle.readComplete();
                    pipeline.fireChannelReadComplete();
                    pipeline.fireExceptionCaught(cause);
                }
            } else {
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
        protected void freeResourcesNow(IoRegistration reg) {
            super.freeResourcesNow(reg);
            if (acceptedAddressMemory != null) {
                acceptedAddressMemory.free();
            }
        }
    }

    @Override
    protected boolean socketIsEmpty(int flags) {
        // IORING_CQE_F_SOCK_NONEMPTY is used for accept since IORING_ACCEPT_DONTWAIT was added.
        // See https://github.com/axboe/liburing/wiki/What's-new-with-io_uring-in-6.10
        return IoUring.isAcceptNoWaitSupported() &&
                IoUring.isCqeFSockNonEmptySupported() && (flags & Native.IORING_CQE_F_SOCK_NONEMPTY) == 0;
    }

    @Override
    boolean isPollInFirst() {
        return false;
    }
}

