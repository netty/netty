/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.region;


import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

import io.netty.buffer.ChannelBuffer;
import io.netty.buffer.ChannelBuffers;
import io.netty.channel.ChannelDownstreamHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureAggregator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.Channels;
import io.netty.channel.MessageEvent;

/**
 * {@link WritableByteChannel} implementation which will take care to wrap the {@link ByteBuffer} to a {@link ChannelBuffer} and forward it to the next {@link ChannelDownstreamHandler} in the {@link ChannelPipeline} on every {@link #write(ByteBuffer)}
 * operation.
 */
public class ChannelWritableByteChannel implements WritableByteChannel {

    private boolean closed;
    private final ChannelHandlerContext context;
    private final ChannelFutureAggregator aggregator;
    private final SocketAddress remote;
    

    public ChannelWritableByteChannel(ChannelHandlerContext context, MessageEvent event) {
        this(context, new ChannelFutureAggregator(event.getFuture()), event.getRemoteAddress());
    }
    

    public ChannelWritableByteChannel(ChannelHandlerContext context, ChannelFutureAggregator aggregator, SocketAddress remote) {
        this.context = context;
        this.aggregator = aggregator;
        this.remote = remote;
    }
    
    @Override
    public boolean isOpen() {
        return !closed && context.channel().isOpen();
    }

    @Override
    public void close() throws IOException {
        closed = true;
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        int written = src.remaining();
        
        // create a new ChannelFuture and add it to the aggregator
        ChannelFuture future =  Channels.future(context.channel(), true);
        aggregator.addFuture(future);
        
        Channels.write(context, future, ChannelBuffers.wrappedBuffer(src), remote);
        return written;
    }

}
