package io.netty.channel;

import java.util.concurrent.ThreadFactory;

public interface EventLoopFactory<T extends EventLoop> {
    T newEventLoop(ThreadFactory threadFactory) throws Exception;
}
