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

import net.gleamynode.netty.channel.Channel;
import net.gleamynode.netty.channel.ChannelEvent;
import net.gleamynode.netty.channel.ChannelFactory;
import net.gleamynode.netty.pipeline.Pipeline;
import net.gleamynode.netty.pipeline.PipelineSink;

public class OioServerSocketChannelFactory implements ChannelFactory {

    final Executor bossExecutor;
    final PipelineSink<ChannelEvent> sink;

    public OioServerSocketChannelFactory(
            Executor bossExecutor, Executor workerExecutor) {
        if (bossExecutor == null) {
            throw new NullPointerException("bossExecutor");
        }
        if (workerExecutor == null) {
            throw new NullPointerException("workerExecutor");
        }
        this.bossExecutor = bossExecutor;
        sink = new OioServerSocketPipelineSink(workerExecutor);
    }

    public Channel newChannel(Pipeline<ChannelEvent> pipeline) {
        pipeline.setSink(sink);
        return new OioServerSocketChannel(this, pipeline);
    }
}
