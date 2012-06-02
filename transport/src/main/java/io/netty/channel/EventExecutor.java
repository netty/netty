package io.netty.channel;

import java.util.concurrent.ScheduledExecutorService;

public interface EventExecutor extends ScheduledExecutorService {
    EventExecutor parent();
    boolean inEventLoop();
    Unsafe unsafe();

    public interface Unsafe {
        EventExecutor nextChild();
    }
}
