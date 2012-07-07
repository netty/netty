package io.netty.channel.socket.aio;

import java.nio.channels.AsynchronousChannelGroup;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

final class AioGroup {

    static final AsynchronousChannelGroup GROUP;

    static {
        AsynchronousChannelGroup group;
        try {
            group = AsynchronousChannelGroup.withThreadPool(new AioGroupExecutor());
        } catch (Exception e) {
            throw new Error(e);
        }

        GROUP = group;
    }

    private AioGroup() {
        // Unused
    }

    static final class AioGroupExecutor extends AbstractExecutorService {

        @Override
        public void shutdown() {
            // Unstoppable
        }

        @Override
        public List<Runnable> shutdownNow() {
            return Collections.emptyList();
        }

        @Override
        public boolean isShutdown() {
            return false;
        }

        @Override
        public boolean isTerminated() {
            return false;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            Thread.sleep(unit.toMillis(timeout));
            return false;
        }

        @Override
        public void execute(Runnable command) {
            command.run();
        }
    }
}
