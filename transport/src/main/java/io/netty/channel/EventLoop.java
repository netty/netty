package io.netty.channel;

import java.util.concurrent.ScheduledExecutorService;

public interface EventLoop extends ScheduledExecutorService {
    ChannelFuture register(Channel channel);
    ChannelFuture register(Channel channel, ChannelFuture future);
    boolean inEventLoop();
}
