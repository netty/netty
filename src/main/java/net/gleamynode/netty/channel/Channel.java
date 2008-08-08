/*
 * Copyright (C) 2008  Trustin Heuiseung Lee
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, 5th Floor, Boston, MA 02110-1301 USA
 */
package net.gleamynode.netty.channel;

import java.net.SocketAddress;
import java.util.UUID;

import net.gleamynode.netty.pipeline.Pipeline;

public interface Channel {
    static int OP_NONE = 0;
    static int OP_READ = 1;
    static int OP_WRITE = 4;
    static int OP_READ_WRITE = OP_READ | OP_WRITE;

    UUID getId();
    ChannelFactory getFactory();
    Channel getParent();
    ChannelConfig getConfig();
    Pipeline<ChannelEvent> getPipeline();

    boolean isOpen();
    boolean isBound();
    boolean isConnected();

    SocketAddress getLocalAddress();
    SocketAddress getRemoteAddress();

    ChannelFuture write(Object message);
    ChannelFuture write(Object message, SocketAddress remoteAddress);

    ChannelFuture bind(SocketAddress localAddress);
    ChannelFuture connect(SocketAddress remoteAddress);
    ChannelFuture disconnect();
    ChannelFuture close();

    int getInterestOps();
    boolean isReadable();
    boolean isWritable();
    ChannelFuture setInterestOps(int interestOps);
    ChannelFuture setReadable(boolean readable);
}
