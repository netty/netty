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
package org.jboss.netty.handler.codec.redis;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.ChannelHandler.Sharable;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * {@link SimpleChannelDownstreamHandler} which encodes {@link Command}'s to {@link ChannelBuffer}'s
 */
@Sharable
public class RedisEncoder extends SimpleChannelDownstreamHandler {

    private final Queue<ChannelBuffer> pool;

    /**
     * Calls {@link #RedisEncoder(boolean)} with <code>false</code>
     */
    public RedisEncoder() {
        this(false);
    }

    /**
     * Create a new {@link RedisEncoder} instance
     *
     * @param poolBuffers <code>true</code> if the {@link ChannelBuffer}'s should be pooled. This should be used with caution as this
     *                    can lead to unnecessary big memory consummation if one of the written values is very big and the rest is very small.
     */
    public RedisEncoder(boolean poolBuffers) {
        if (poolBuffers) {
            pool = new ConcurrentLinkedQueue<ChannelBuffer>();
        } else {
            pool = null;
        }
    }


    @Override
    public void writeRequested(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        Object o = e.getMessage();
        if (o instanceof Command) {
            ChannelBuffer cb = getChannelBuffer();
            ChannelFuture future = e.getFuture();

            Command command = (Command) o;
            command.write(cb);
            returnToPool(cb, future);
            Channels.write(ctx, future, cb);

        } else if (o instanceof Iterable) {
            ChannelBuffer cb = getChannelBuffer();
            ChannelFuture future = e.getFuture();

            // Useful for transactions and database select
            for (Object i : (Iterable) o) {
                if (i instanceof Command) {
                    Command command = (Command) i;
                    command.write(cb);
                } else {
                    if (pool != null) {
                        cb.clear();
                        pool.add(cb);
                        super.writeRequested(ctx, e);
                        return;
                    }
                }
            }
            returnToPool(cb, future);
            Channels.write(ctx, future, cb);
        } else {
            super.writeRequested(ctx, e);
        }
    }

    private void returnToPool(ChannelBuffer cb, ChannelFuture future) {
        if (pool != null) {
            final ChannelBuffer finalCb = cb;
            future.addListener(new ChannelFutureListener() {
                public void operationComplete(ChannelFuture channelFuture) throws Exception {
                    finalCb.clear();
                    pool.add(finalCb);
                }
            });
        }
    }

    private ChannelBuffer getChannelBuffer() {
        ChannelBuffer cb = null;
        if (pool != null) {
            cb = pool.poll();
        }
        if (cb == null) {
            cb = ChannelBuffers.dynamicBuffer();
        }
        return cb;
    }
}
