package io.netty.channel;

import java.util.concurrent.TimeUnit;

public class VoidChannelFuture implements ChannelFuture.Unsafe {

    private final Channel channel;

    /**
     * Creates a new instance.
     *
     * @param channel the {@link Channel} associated with this future
     */
    public VoidChannelFuture(Channel channel) {
        if (channel == null) {
            throw new NullPointerException("channel");
        }
        this.channel = channel;
    }

    @Override
    public void addListener(final ChannelFutureListener listener) {
        fail();
    }

    @Override
    public void removeListener(ChannelFutureListener listener) {
        // NOOP
    }

    @Override
    public ChannelFuture await() throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        return this;
    }

    @Override
    public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
        fail();
        return false;
    }

    @Override
    public boolean await(long timeoutMillis) throws InterruptedException {
        fail();
        return false;
    }

    @Override
    public ChannelFuture awaitUninterruptibly() {
        fail();
        return this;
    }

    @Override
    public boolean awaitUninterruptibly(long timeout, TimeUnit unit) {
        fail();
        return false;
    }

    @Override
    public boolean awaitUninterruptibly(long timeoutMillis) {
        fail();
        return false;
    }

    @Override
    public Channel channel() {
        return channel;
    }

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean isCancelled() {
        return false;
    }

    @Override
    public boolean isSuccess() {
        return false;
    }

    @Override
    public Throwable cause() {
        return null;
    }

    @Override
    public ChannelFuture rethrowIfFailed() throws Exception {
        fail();
        return this;
    }

    @Override
    public boolean setProgress(long amount, long current, long total) {
        return false;
    }

    @Override
    public boolean setFailure(Throwable cause) {
        return false;
    }

    @Override
    public boolean setSuccess() {
        return false;
    }

    @Override
    public boolean cancel() {
        return false;
    }

    private static void fail() {
        throw new IllegalStateException("void future");
    }
}
