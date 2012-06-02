package io.netty.channel;

import java.util.concurrent.ThreadFactory;

public abstract class SingleThreadEventLoop extends SingleThreadEventExecutor implements EventLoop {

    protected SingleThreadEventLoop() {}

    protected SingleThreadEventLoop(ThreadFactory threadFactory) {
        super(threadFactory);
    }

    @Override
    public ChannelFuture register(Channel channel) {
        if (channel == null) {
            throw new NullPointerException("channel");
        }
        return register(channel, channel.newFuture());
    }

    @Override
    public ChannelFuture register(final Channel channel, final ChannelFuture future) {
        if (inEventLoop()) {
            channel.unsafe().register(this, future);
        } else {
            execute(new Runnable() {
                @Override
                public void run() {
                    channel.unsafe().register(SingleThreadEventLoop.this, future);
                }
            });
        }
        return future;
    }
}
