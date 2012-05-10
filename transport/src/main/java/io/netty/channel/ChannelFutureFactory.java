package io.netty.channel;

public interface ChannelFutureFactory {
    ChannelFuture newFuture();
    ChannelFuture newSucceededFuture();
    ChannelFuture newFailedFuture(Throwable cause);
}
