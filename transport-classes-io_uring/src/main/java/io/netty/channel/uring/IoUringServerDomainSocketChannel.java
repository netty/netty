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
package io.netty.channel.uring;

import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.unix.DomainSocketAddress;
import io.netty.channel.unix.ServerDomainSocketChannel;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.File;
import java.net.SocketAddress;
import java.nio.ByteBuffer;

public final class IoUringServerDomainSocketChannel extends AbstractIoUringServerChannel
        implements ServerDomainSocketChannel {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(
            IoUringServerDomainSocketChannel.class);

    private final IoUringServerSocketChannelConfig config;

    private volatile DomainSocketAddress local;

    public IoUringServerDomainSocketChannel() {
        super(LinuxSocket.newSocketDomain(), false);
        this.config = new IoUringServerSocketChannelConfig(this);
        this.closeFuture().addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (local != null) {
                    // Delete the socket file if possible.
                    File socketFile = new File(local.path());
                    boolean success = socketFile.delete();
                    if (!success && logger.isDebugEnabled()) {
                        logger.debug("Failed to delete a domain socket file: {}", local.path());
                    }
                }
            }
        });
    }

    @Override
    Channel newChildChannel(int fd, ByteBuffer acceptedAddressMemory) throws Exception {
        return new IoUringDomainSocketChannel(this, new LinuxSocket(fd));
    }

    @Override
    public ChannelConfig config() {
        return config;
    }

    @Override
    public DomainSocketAddress localAddress() {
        return local;
    }

    @Override
    public DomainSocketAddress remoteAddress() {
        return (DomainSocketAddress) super.remoteAddress();
    }

    @Override
    protected void doBind(SocketAddress localAddress) throws Exception {
        socket.bind(localAddress);
        socket.listen(config.getBacklog());
        local = (DomainSocketAddress) localAddress;
        active = true;
    }

}
