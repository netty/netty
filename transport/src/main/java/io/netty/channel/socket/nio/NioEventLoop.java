package io.netty.channel.socket.nio;

import io.netty.channel.EventExecutor;
import io.netty.channel.MultithreadEventLoop;

import java.nio.channels.spi.SelectorProvider;
import java.util.concurrent.ThreadFactory;

public class NioEventLoop extends MultithreadEventLoop {

    public NioEventLoop() {}

    public NioEventLoop(int nThreads) {
        super(nThreads);
    }

    public NioEventLoop(int nThreads, ThreadFactory threadFactory) {
        super(nThreads, threadFactory);
    }

    public NioEventLoop(int nThreads, ThreadFactory threadFactory, final SelectorProvider selectorProvider) {
        super(nThreads, threadFactory, selectorProvider);
    }

    @Override
    protected EventExecutor newChild(ThreadFactory threadFactory, Object... args) throws Exception {
        SelectorProvider selectorProvider;
        if (args == null || args.length == 0 || args[0] == null) {
            selectorProvider = SelectorProvider.provider();
        } else {
            selectorProvider = (SelectorProvider) args[0];
        }
        return new NioChildEventLoop(this, threadFactory, selectorProvider);
    }
}
