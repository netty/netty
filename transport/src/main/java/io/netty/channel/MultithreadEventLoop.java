package io.netty.channel;

import java.util.concurrent.ThreadFactory;

public abstract class MultithreadEventLoop extends MultithreadEventExecutor implements EventLoop {


    protected MultithreadEventLoop(int nThreads, Object... args) {
        super(nThreads, args);
    }

    protected MultithreadEventLoop(int nThreads, ThreadFactory threadFactory,
            Object... args) {
        super(nThreads, threadFactory, args);
    }

    protected MultithreadEventLoop(Object... args) {
        super(args);
    }

    @Override
    protected abstract EventExecutor newChild(ThreadFactory threadFactory, Object... args) throws Exception;

    @Override
    public EventLoop parent() {
        return (EventLoop) super.parent();
    }

    @Override
    public ChannelFuture register(Channel channel) {
        return ((EventLoop) unsafe().nextChild()).register(channel);
    }

    @Override
    public ChannelFuture register(Channel channel, ChannelFuture future) {
        return ((EventLoop) unsafe().nextChild()).register(channel, future);
    }
}
