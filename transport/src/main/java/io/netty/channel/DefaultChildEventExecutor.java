package io.netty.channel;

import java.util.concurrent.ThreadFactory;

class DefaultChildEventExecutor extends SingleThreadEventExecutor {

    DefaultChildEventExecutor(ThreadFactory threadFactory) {
        super(threadFactory);
    }

    @Override
    protected void run() {
        for (;;) {
            Runnable task;
            try {
                task = takeTask();
                task.run();
            } catch (InterruptedException e) {
                // Waken up by interruptThread()
            }

            if (isShutdown() && peekTask() == null) {
                break;
            }
        }
    }

    @Override
    protected void wakeup(boolean inEventLoop) {
        if (!inEventLoop) {
            interruptThread();
        }
    }
}
