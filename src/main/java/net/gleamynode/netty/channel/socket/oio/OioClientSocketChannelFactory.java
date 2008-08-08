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
package net.gleamynode.netty.channel.socket.oio;

import java.util.concurrent.Executor;

import net.gleamynode.netty.channel.ChannelPipeline;
import net.gleamynode.netty.channel.socket.ClientSocketChannelFactory;
import net.gleamynode.netty.channel.socket.SocketChannel;

public class OioClientSocketChannelFactory implements ClientSocketChannelFactory {

    final OioClientSocketPipelineSink sink;

    public OioClientSocketChannelFactory(Executor workerExecutor) {
        if (workerExecutor == null) {
            throw new NullPointerException("workerExecutor");
        }
        sink = new OioClientSocketPipelineSink(workerExecutor);
    }

    public SocketChannel newChannel(ChannelPipeline pipeline) {
        return new OioClientSocketChannel(this, pipeline, sink);
    }
}
