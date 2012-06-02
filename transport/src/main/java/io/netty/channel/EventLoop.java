package io.netty.channel;

public interface EventLoop extends EventExecutor {
    ChannelFuture register(Channel channel);
    ChannelFuture register(Channel channel, ChannelFuture future);
}
