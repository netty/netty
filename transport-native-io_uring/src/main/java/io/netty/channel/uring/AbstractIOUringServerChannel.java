/*
 * Copyright 2020 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel.uring;


import io.netty.channel.Channel;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.ServerChannel;
import io.netty.channel.unix.Errors;

import java.net.SocketAddress;

import static io.netty.channel.unix.Errors.*;

abstract class AbstractIOUringServerChannel extends AbstractIOUringChannel implements ServerChannel {

    protected AbstractIOUringServerChannel(LinuxSocket socket, boolean active) {
        super(null, socket, active);
    }

    @Override
    protected AbstractUringUnsafe newUnsafe() {
        return new UringServerChannelUnsafe();
    }

    @Override
    protected void doWrite(ChannelOutboundBuffer in) {
        throw new UnsupportedOperationException();
    }

    public AbstractIOUringChannel getChannel() {
        return this;
    }

    abstract Channel newChildChannel(int fd) throws Exception;

    final class UringServerChannelUnsafe extends AbstractIOUringChannel.AbstractUringUnsafe {
        @Override
        protected void scheduleRead0() {
            final IOUringRecvByteAllocatorHandle allocHandle = recvBufAllocHandle();
            allocHandle.reset(config());
            allocHandle.attemptedBytesRead(1);

            IOUringSubmissionQueue submissionQueue = submissionQueue();
            //Todo get network addresses
            submissionQueue.addAccept(fd().intValue());
            submissionQueue.submit();
        }

        protected void readComplete0(int res) {
            final IOUringRecvByteAllocatorHandle allocHandle =
                    (IOUringRecvByteAllocatorHandle) unsafe()
                            .recvBufAllocHandle();
            final ChannelPipeline pipeline = pipeline();
            allocHandle.lastBytesRead(res);

            if (res >= 0) {
                allocHandle.incMessagesRead(1);
                try {
                    Channel channel = newChildChannel(res);
                    pipeline.fireChannelRead(channel);
                    if (allocHandle.continueReading()) {
                        scheduleRead();
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
    }
}

