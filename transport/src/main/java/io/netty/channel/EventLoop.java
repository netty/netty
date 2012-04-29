package io.netty.channel;

import java.util.concurrent.ExecutorService;

public interface EventLoop extends ExecutorService {
    ChannelFuture register(Channel channel);
    EventLoop register(Channel channel, ChannelFuture future);
    boolean inEventLoop();
}
