package io.netty.channel.local;

import io.netty.channel.EventExecutor;
import io.netty.channel.MultithreadEventLoop;

import java.util.concurrent.ThreadFactory;

public class LocalEventLoop extends MultithreadEventLoop {

    public LocalEventLoop() {}

    public LocalEventLoop(int nThreads) {
        super(nThreads);
    }

    public LocalEventLoop(int nThreads, ThreadFactory threadFactory) {
        super(nThreads, threadFactory);
    }

    @Override
    protected EventExecutor newChild(ThreadFactory threadFactory, Object... args) throws Exception {
        return new LocalChildEventLoop(this, threadFactory);
    }
}
