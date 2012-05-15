package io.netty.channel.socket.nio;

import io.netty.channel.EventLoopFactory;
import io.netty.channel.MultithreadEventLoop;

import java.nio.channels.spi.SelectorProvider;
import java.util.concurrent.ThreadFactory;

public class SelectorEventLoop extends MultithreadEventLoop {

    public SelectorEventLoop() {
        this(DEFAULT_POOL_SIZE);
    }

    public SelectorEventLoop(int nThreads) {
        this(nThreads, DEFAULT_THREAD_FACTORY);
    }

    public SelectorEventLoop(int nThreads, ThreadFactory threadFactory) {
        this(nThreads, threadFactory, SelectorProvider.provider());
    }

    public SelectorEventLoop(int nThreads, ThreadFactory threadFactory, final SelectorProvider selectorProvider) {
        super(new EventLoopFactory<SingleThreadSelectorEventLoop>() {
            @Override
            public SingleThreadSelectorEventLoop newEventLoop(ThreadFactory threadFactory) throws Exception {
                return new SingleThreadSelectorEventLoop(threadFactory, selectorProvider);
            }

        }, nThreads, threadFactory);
    }
}
