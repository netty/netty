/*
 * Copyright 2014 The Netty Project
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
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPromise;
import io.netty.channel.ServerChannel;
import io.netty.channel.unix.FileDescriptor;

import java.net.SocketAddress;

public class AbstractIOUringServerChannel extends AbstractIOUringChannel implements ServerChannel {

    private volatile SocketAddress local;

    AbstractIOUringServerChannel(final Channel parent, final LinuxSocket fd, final boolean active, final long ioUring) {
        super(parent, fd, active, ioUring);
    }

    @Override
    public ChannelConfig config() {
        return null;
    }

    @Override
    protected AbstractUringUnsafe newUnsafe() {
        return new UringServerChannelUnsafe();
    }

    @Override
    protected SocketAddress localAddress0() {
        return null;
    }

    @Override
    protected SocketAddress remoteAddress0() {
        return null;
    }

    @Override
    protected void doWrite(ChannelOutboundBuffer in) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    public FileDescriptor fd() {
        return null;
    }

    final class UringServerChannelUnsafe extends AbstractIOUringChannel.AbstractUringUnsafe {
        private final byte[] acceptedAddress = new byte[26];

        @Override
        public void connect(final SocketAddress remoteAddress, final SocketAddress localAddress,
                            final ChannelPromise promise) {
            promise.setFailure(new UnsupportedOperationException());
        }

        @Override
        public void uringEventExecution() {
            final IOUringEventLoop ioUringEventLoop = (IOUringEventLoop) eventLoop();

            long eventId = ioUringEventLoop.incrementEventIdCounter();
            final Event event = new Event();
            event.setId(eventId);
            event.setOp(EventType.ACCEPT);

            if (socket.acceptEvent(getIoUring(), eventId, acceptedAddress) == 0) {
                ioUringEventLoop.addNewEvent(event);
                Native.submit(getIoUring());
            }
        }
    }
}
