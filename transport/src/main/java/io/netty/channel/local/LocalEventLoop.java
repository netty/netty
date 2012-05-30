package io.netty.channel.local;

import io.netty.channel.EventLoopFactory;
import io.netty.channel.MultithreadEventLoop;

import java.util.concurrent.ThreadFactory;

public class LocalEventLoop extends MultithreadEventLoop {

    public LocalEventLoop() {
        this(DEFAULT_POOL_SIZE);
    }

    public LocalEventLoop(int nThreads) {
        this(nThreads, DEFAULT_THREAD_FACTORY);
    }

    public LocalEventLoop(int nThreads, ThreadFactory threadFactory) {
        super(new EventLoopFactory<LocalChildEventLoop>() {
            @Override
            public LocalChildEventLoop newEventLoop(ThreadFactory threadFactory) throws Exception {
                return new LocalChildEventLoop(threadFactory);
            }
        }, nThreads, threadFactory);
    }
}
