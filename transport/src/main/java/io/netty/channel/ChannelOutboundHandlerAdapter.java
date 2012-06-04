/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel;

import io.netty.buffer.ChannelBuffer;

import java.net.SocketAddress;
import java.util.Queue;

public abstract class ChannelOutboundHandlerAdapter<O> extends AbstractChannelHandler
        implements ChannelOutboundHandler<O> {

    @Override
    public void bind(ChannelOutboundHandlerContext<O> ctx, SocketAddress localAddress, ChannelFuture future) throws Exception {
        ctx.bind(localAddress, future);
    }

    @Override
    public void connect(ChannelOutboundHandlerContext<O> ctx, SocketAddress remoteAddress, SocketAddress localAddress, ChannelFuture future) throws Exception {
        ctx.connect(remoteAddress, localAddress, future);
    }

    @Override
    public void disconnect(ChannelOutboundHandlerContext<O> ctx, ChannelFuture future) throws Exception {
        ctx.disconnect(future);
    }

    @Override
    public void close(ChannelOutboundHandlerContext<O> ctx, ChannelFuture future) throws Exception {
        ctx.close(future);
    }

    @Override
    public void deregister(ChannelOutboundHandlerContext<O> ctx, ChannelFuture future) throws Exception {
        ctx.deregister(future);
    }

    @Override
    public void flush(ChannelOutboundHandlerContext<O> ctx, ChannelFuture future) throws Exception {
        flush0(ctx, future);
    }

    static <O> void flush0(ChannelOutboundHandlerContext<O> ctx, ChannelFuture future) {
        if (ctx.outbound().isBypass()) {
            ctx.flush(future);
            return;
        }

        if (ctx.outbound().hasMessageBuffer()) {
            Queue<O> out = ctx.outbound().messageBuffer();
            Queue<Object> nextOut = ctx.nextOutboundMessageBuffer();
            for (;;) {
                O msg = out.poll();
                if (msg == null) {
                    break;
                }
                nextOut.add(msg);
            }
        } else {
            ChannelBuffer out = ctx.outbound().byteBuffer();
            ChannelBuffer nextOut = ctx.nextOutboundByteBuffer();
            nextOut.writeBytes(out);
            out.discardReadBytes();
        }
        ctx.flush(future);
    }
}
