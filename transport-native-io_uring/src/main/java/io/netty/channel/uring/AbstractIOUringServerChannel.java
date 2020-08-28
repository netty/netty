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

    AbstractIOUringServerChannel(int fd) {
        super(null, new LinuxSocket(fd));
    }

    AbstractIOUringServerChannel(LinuxSocket fd) {
        super(null, fd);
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
        private final byte[] acceptedAddress = new byte[26];

        private void acceptSocket() {
            IOUringSubmissionQueue submissionQueue = submissionQueue();
            //Todo get network addresses
            submissionQueue.addAccept(fd().intValue());
            submissionQueue.submit();
        }

        @Override
        void pollIn(int res) {
            acceptSocket();
        }

        // TODO: Respect MAX_MESSAGES_READ
        protected void readComplete0(int res) {
            final IOUringRecvByteAllocatorHandle allocHandle =
                    (IOUringRecvByteAllocatorHandle) unsafe()
                            .recvBufAllocHandle();
            final ChannelPipeline pipeline = pipeline();
                if (res >= 0) {
                    allocHandle.incMessagesRead(1);
                    try {
                        pipeline.fireChannelRead(newChildChannel(res));
                    } catch (Throwable cause) {
                        allocHandle.readComplete();
                        pipeline.fireExceptionCaught(cause);
                        pipeline.fireChannelReadComplete();
                    }
                    acceptSocket();
                } else {
                    allocHandle.readComplete();
                    // Check if we did fail because there was nothing to accept atm.
                    if (res != ERRNO_EAGAIN_NEGATIVE && res != ERRNO_EWOULDBLOCK_NEGATIVE) {
                        // Something bad happened. Convert to an exception.
                        pipeline.fireExceptionCaught(Errors.newIOException("io_uring accept", res));
                    }
                    pipeline.fireChannelReadComplete();
                }
        }

        @Override
        public void connect(final SocketAddress remoteAddress, final SocketAddress localAddress,
                            final ChannelPromise promise) {
            promise.setFailure(new UnsupportedOperationException());
        }
    }
}

