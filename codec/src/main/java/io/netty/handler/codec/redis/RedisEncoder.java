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
package io.netty.handler.codec.redis;

import io.netty.buffer.ChannelBuffer;
import io.netty.buffer.ChannelBuffers;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.Channels;
import io.netty.channel.MessageEvent;
import io.netty.channel.SimpleChannelDownstreamHandler;

/**
 * {@link SimpleChannelDownstreamHandler} which encodes {@link Command}'s to {@link ChannelBuffer}'s
 */
@Sharable
public class RedisEncoder extends SimpleChannelDownstreamHandler {

    @Override
    public void writeRequested(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        Object o = e.getMessage();
        if (o instanceof Command) {
            ChannelBuffer cb = ChannelBuffers.dynamicBuffer();
            ChannelFuture future = e.getFuture();

            Command command = (Command) o;
            command.write(cb);
            Channels.write(ctx, future, cb);

        } else if (o instanceof Iterable) {
            ChannelBuffer cb = ChannelBuffers.dynamicBuffer();
            ChannelFuture future = e.getFuture();

            // Useful for transactions and database select
            for (Object i : (Iterable<?>) o) {
                if (i instanceof Command) {
                    Command command = (Command) i;
                    command.write(cb);
                } else {
                    super.writeRequested(ctx, e);
                    return;
                }
            }
            Channels.write(ctx, future, cb);
        } else {
            super.writeRequested(ctx, e);
        }
    }
}
