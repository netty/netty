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
import io.netty.channel.ChannelPromise;
import io.netty.channel.ServerChannel;
import io.netty.channel.unix.Socket;

import java.net.SocketAddress;

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
    protected void doWrite(ChannelOutboundBuffer in) throws Exception {
        throw new UnsupportedOperationException();
    }

    public AbstractIOUringChannel getChannel() {
        return this;
    }

    abstract Channel newChildChannel(int fd) throws Exception;

    final class UringServerChannelUnsafe extends AbstractIOUringChannel.AbstractUringUnsafe {
        private final byte[] acceptedAddress = new byte[26];

        @Override
        public void connect(final SocketAddress remoteAddress, final SocketAddress localAddress,
                            final ChannelPromise promise) {
            promise.setFailure(new UnsupportedOperationException());
        }

        private void addPoll(IOUringEventLoop ioUringEventLoop) {
            long eventId = ioUringEventLoop.incrementEventIdCounter();
            Event event = new Event();
            event.setOp(EventType.POLL_LINK);

            event.setId(eventId);
            event.setAbstractIOUringChannel(AbstractIOUringServerChannel.this);
            ioUringEventLoop.getRingBuffer().getIoUringSubmissionQueue()
                    .addPoll(eventId, socket.intValue(), event.getOp());
            ((IOUringEventLoop) eventLoop()).addNewEvent(event);
        }

        @Override
        public void uringEventExecution() {
            final IOUringEventLoop ioUringEventLoop = (IOUringEventLoop) eventLoop();
            IOUringSubmissionQueue submissionQueue = ioUringEventLoop.getRingBuffer().getIoUringSubmissionQueue();

            addPoll(ioUringEventLoop);

            long eventId = ioUringEventLoop.incrementEventIdCounter();
            final Event event = new Event();
            event.setId(eventId);
            event.setOp(EventType.ACCEPT);
            event.setAbstractIOUringChannel(getChannel());

            //Todo get network addresses
            submissionQueue.add(eventId, EventType.ACCEPT, getChannel().getSocket().intValue(), 0, 0, 0);
            ioUringEventLoop.addNewEvent(event);

            submissionQueue.submit();
        }
    }
}

