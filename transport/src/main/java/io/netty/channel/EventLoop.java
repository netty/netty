package io.netty.channel;

import java.util.concurrent.ExecutorService;

public interface EventLoop extends ExecutorService {
    ChannelFuture attach(Channel channel);
    void attach(Channel channel, ChannelFuture future);
    boolean inEventLoop();
}
