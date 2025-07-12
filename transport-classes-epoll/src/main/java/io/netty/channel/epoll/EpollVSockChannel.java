/*
 * Copyright 2025 The Netty Project
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
package io.netty.channel.epoll;

import io.netty.channel.Channel;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.unix.FileDescriptor;
import io.netty.channel.unix.VSockAddress;
import io.netty.channel.unix.VSockChannel;

import java.net.SocketAddress;

public class EpollVSockChannel extends AbstractEpollStreamChannel implements VSockChannel {
    private final EpollVSockChannelConfig config;
    private volatile VSockAddress local;
    private volatile VSockAddress remote;

    public EpollVSockChannel() {
        super(LinuxSocket.newSocketVSock(), false);
        config = new EpollVSockChannelConfig(this);
    }

    EpollVSockChannel(Channel parent, FileDescriptor fd) {
        this(parent, new LinuxSocket(fd.intValue()));
    }

    public EpollVSockChannel(Channel parent, LinuxSocket socket) {
        super(parent, socket);
        local = socket.localVSockAddress0();
        remote = socket.remoteVSockAddress0();
        config = new EpollVSockChannelConfig(this);
    }

    protected VSockAddress localAddress0() {
        return this.local;
    }

    protected VSockAddress remoteAddress0() {
        return this.remote;
    }

    protected void doBind(SocketAddress localAddress) throws Exception {
        if (!(localAddress instanceof VSockAddress)) {
            throw new RuntimeException("Please bind `VSockChannel` through bind(new VSockAddress(CID, PORT))");
        }
        final VSockAddress address = (VSockAddress) localAddress;
        this.local = new VSockAddress(VSockAddress.VMADDR_CID_ANY, address.getPort());
        this.socket.bind(this.local);
    }

    public EpollVSockChannelConfig config() {
        return this.config;
    }

    protected boolean doConnect(SocketAddress remoteAddress, SocketAddress localAddress) throws Exception {
        this.remote = (VSockAddress) remoteAddress;
        final boolean vsConnected = socket.connect(remoteAddress);
        this.local = localAddress != null ? (VSockAddress) localAddress : this.socket.localVSockAddress0();
        if (!vsConnected) {
            setFlag(Native.EPOLLOUT);
        }
        return vsConnected;
    }

    public VSockAddress remoteAddress() {
        return (VSockAddress) super.remoteAddress();
    }

    public VSockAddress localAddress() {
        return (VSockAddress) super.localAddress();
    }

    protected int doWriteSingle(ChannelOutboundBuffer in) throws Exception {
        Object msg = in.current();
        if (msg instanceof FileDescriptor && this.socket.sendFd(((FileDescriptor) msg).intValue()) > 0) {
            in.remove();
            return 1;
        } else {
            return super.doWriteSingle(in);
        }
    }

    protected Object filterOutboundMessage(Object msg) {
        return msg instanceof FileDescriptor ? msg : super.filterOutboundMessage(msg);
    }
}
