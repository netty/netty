package io.netty.channel.socket.nio;

import io.netty.channel.EventLoopFactory;
import io.netty.channel.MultithreadEventLoop;

import java.nio.channels.spi.SelectorProvider;
import java.util.concurrent.ThreadFactory;

public class NioEventLoop extends MultithreadEventLoop {

    public NioEventLoop() {
        this(DEFAULT_POOL_SIZE);
    }

    public NioEventLoop(int nThreads) {
        this(nThreads, DEFAULT_THREAD_FACTORY);
    }

    public NioEventLoop(int nThreads, ThreadFactory threadFactory) {
        this(nThreads, threadFactory, SelectorProvider.provider());
    }

    public NioEventLoop(int nThreads, ThreadFactory threadFactory, final SelectorProvider selectorProvider) {
        super(new EventLoopFactory<NioChildEventLoop>() {
            @Override
            public NioChildEventLoop newEventLoop(ThreadFactory threadFactory) throws Exception {
                return new NioChildEventLoop(threadFactory, selectorProvider);
            }

        }, nThreads, threadFactory);
    }
}
