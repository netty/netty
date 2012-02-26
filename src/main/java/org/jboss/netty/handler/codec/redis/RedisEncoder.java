package org.jboss.netty.handler.codec.redis;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelDownstreamHandler;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class RedisEncoder extends SimpleChannelDownstreamHandler {

    private Queue<ChannelBuffer> pool = new ConcurrentLinkedQueue<ChannelBuffer>();

    @Override
    public void writeRequested(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        Object o = e.getMessage();
        if (o instanceof Command) {
            Command command = (Command) o;
            ChannelBuffer cb = pool.poll();
            if (cb == null) {
                cb = ChannelBuffers.dynamicBuffer();
            }
            command.write(cb);
            ChannelFuture future = e.getFuture();
            final ChannelBuffer finalCb = cb;
            future.addListener(new ChannelFutureListener() {
                public void operationComplete(ChannelFuture channelFuture) throws Exception {
                    finalCb.clear();
                    pool.add(finalCb);
                }
            });
            Channels.write(ctx, future, cb);
        } else {
            super.writeRequested(ctx, e);
        }
    }
}
