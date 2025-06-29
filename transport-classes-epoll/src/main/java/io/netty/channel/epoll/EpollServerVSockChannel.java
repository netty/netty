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
import io.netty.channel.unix.ServerVSockChannel;
import io.netty.channel.unix.VSockAddress;

import java.net.SocketAddress;

public class EpollServerVSockChannel extends AbstractEpollServerChannel implements ServerVSockChannel {
    private final EpollServerChannelConfig config = new EpollServerChannelConfig(this);
    private volatile VSockAddress local;

    public EpollServerVSockChannel() {
        super(LinuxSocket.newSocketVSock(), false);
    }

    public EpollServerVSockChannel(int fd) {
        super(fd);
    }

    EpollServerVSockChannel(LinuxSocket fd) {
        super(fd);
    }

    EpollServerVSockChannel(LinuxSocket fd, boolean active) {
        super(fd, active);
    }

    protected Channel newChildChannel(int fd, byte[] addr, int offset, int len) {
        return new EpollVSockChannel(this, new LinuxSocket(fd));
    }

    protected VSockAddress localAddress0() {
        return this.local;
    }

    protected void doBind(SocketAddress localAddress) throws Exception {
        if (!(localAddress instanceof VSockAddress)) {
            throw new RuntimeException("Please bind `ServerVSockChannel` through bind(new VSockAddress(CID, PORT))");
        }
        final VSockAddress address = (VSockAddress) localAddress;
        this.local = new VSockAddress(address.getCid(), address.getPort());
        this.socket.bind(this.local);
        this.socket.listen(this.config.getBacklog());
        this.active = true;
    }

    protected void doClose() throws Exception {
        super.doClose();
    }

    public EpollServerChannelConfig config() {
        return this.config;
    }

    public VSockAddress remoteAddress() {
        return (VSockAddress) super.remoteAddress();
    }

    public VSockAddress localAddress() {
        return (VSockAddress) super.localAddress();
    }
}
