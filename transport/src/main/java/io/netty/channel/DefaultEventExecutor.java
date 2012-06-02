package io.netty.channel;

import java.util.concurrent.ThreadFactory;

public class DefaultEventExecutor extends MultithreadEventExecutor {

    public DefaultEventExecutor(int nThreads) {
        super(nThreads);
    }

    public DefaultEventExecutor(int nThreads, ThreadFactory threadFactory) {
        super(nThreads, threadFactory);
    }

    @Override
    protected EventExecutor newChild(ThreadFactory threadFactory, Object... args) throws Exception {
        return new DefaultChildEventExecutor(this, threadFactory);
    }
}
