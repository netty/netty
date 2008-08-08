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
package net.gleamynode.netty.channel.socket.nio;

import java.io.IOException;
import java.nio.channels.SocketChannel;

import net.gleamynode.netty.channel.Channel;
import net.gleamynode.netty.channel.ChannelException;
import net.gleamynode.netty.channel.ChannelFactory;
import net.gleamynode.netty.channel.ChannelPipeline;
import net.gleamynode.netty.channel.ChannelSink;

class NioAcceptedSocketChannel extends NioSocketChannel {

    final NioWorker worker;

    NioAcceptedSocketChannel(
            ChannelFactory factory, ChannelPipeline pipeline,
            Channel parent, ChannelSink sink,
            SocketChannel socket, NioWorker worker) {

        super(parent, factory, pipeline, sink, socket);

        this.worker = worker;
        try {
            socket.configureBlocking(false);
        } catch (IOException e) {
            throw new ChannelException("Failed to enter non-blocking mode.", e);
        }
    }

    @Override
    NioWorker getWorker() {
        return worker;
    }

    @Override
    void setWorker(NioWorker worker) {
        // worker never changes.
        if (this.worker != worker) {
            throw new IllegalStateException("Should not reach here.");
        }
    }
}
