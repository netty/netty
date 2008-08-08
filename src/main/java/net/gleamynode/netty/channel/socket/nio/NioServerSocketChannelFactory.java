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

import java.util.concurrent.Executor;

import net.gleamynode.netty.channel.Channel;
import net.gleamynode.netty.channel.ChannelEvent;
import net.gleamynode.netty.channel.ChannelFactory;
import net.gleamynode.netty.pipeline.Pipeline;
import net.gleamynode.netty.pipeline.PipelineSink;

public class NioServerSocketChannelFactory implements ChannelFactory {

    final Executor bossExecutor;
    final PipelineSink<ChannelEvent> sink;

    public NioServerSocketChannelFactory(
            Executor bossExecutor, Executor workerExecutor) {
        this(bossExecutor, workerExecutor, Runtime.getRuntime().availableProcessors());
    }

    public NioServerSocketChannelFactory(
            Executor bossExecutor, Executor workerExecutor,
            int workerCount) {
        if (bossExecutor == null) {
            throw new NullPointerException("bossExecutor");
        }
        if (workerExecutor == null) {
            throw new NullPointerException("workerExecutor");
        }
        if (workerCount <= 0) {
            throw new IllegalArgumentException(
                    "workerCount (" + workerCount + ") " +
                    "must be a positive integer.");
        }
        this.bossExecutor = bossExecutor;
        sink = new NioServerSocketPipelineSink(workerExecutor, workerCount);
    }

    public Channel newChannel(Pipeline<ChannelEvent> pipeline) {
        pipeline.setSink(sink);
        return new NioServerSocketChannel(this, pipeline);
    }

}
