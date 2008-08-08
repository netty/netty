/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * by the @author tags. See the COPYRIGHT.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.netty.channel.socket.nio;

import java.io.IOException;
import java.nio.channels.SocketChannel;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelException;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelSink;

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
