package io.netty.channel;

public interface EventLoop extends EventExecutor {
    @Override
    EventLoop parent();
    ChannelFuture register(Channel channel);
    ChannelFuture register(Channel channel, ChannelFuture future);
}
