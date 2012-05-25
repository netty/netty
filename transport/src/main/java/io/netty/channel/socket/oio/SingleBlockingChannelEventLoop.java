package io.netty.channel.socket.oio;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.SingleThreadEventLoop;


class SingleBlockingChannelEventLoop extends SingleThreadEventLoop {

    private final BlockingChannelEventLoop parent;
    private Channel ch;

    SingleBlockingChannelEventLoop(BlockingChannelEventLoop parent) {
        super(parent.threadFactory);
        this.parent = parent;
    }

    @Override
    public ChannelFuture register(Channel channel, ChannelFuture future) {
        return super.register(channel, future).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (future.isSuccess()) {
                    ch = future.channel();
                } else {
                    deregister();
                }
            }
        });
    }

    @Override
    protected void run() {
        for (;;) {
            Channel ch = SingleBlockingChannelEventLoop.this.ch;
            if (ch == null || !ch.isActive()) {
                Runnable task;
                try {
                    task = takeTask();
                    task.run();
                } catch (InterruptedException e) {
                    // Waken up by interruptThread()
                }
            } else {
                processTaskQueue();
                ch.unsafe().read();

                // Handle deregistration
                if (!ch.isRegistered()) {
                    processTaskQueue();
                    deregister();
                }
            }

            if (isShutdown() && peekTask() == null) {
                break;
            }
        }
    }

    private void processTaskQueue() {
        for (;;) {
            Runnable task = pollTask();
            if (task == null) {
                break;
            }
            task.run();
        }
    }

    @Override
    protected void wakeup(boolean inEventLoop) {
        interruptThread();
    }

    private void deregister() {
        ch = null;
        parent.activeChildren.remove(this);
        parent.idleChildren.add(this);
    }
}
