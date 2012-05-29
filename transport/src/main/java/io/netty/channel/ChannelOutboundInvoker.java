package io.netty.channel;

import java.net.SocketAddress;

public interface ChannelOutboundInvoker {
    ChannelFuture bind(SocketAddress localAddress);
    ChannelFuture connect(SocketAddress remoteAddress);
    ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress);
    ChannelFuture disconnect();
    ChannelFuture close();
    ChannelFuture deregister();
    ChannelFuture flush();
    ChannelFuture write(Object message);

    ChannelFuture bind(SocketAddress localAddress, ChannelFuture future);
    ChannelFuture connect(SocketAddress remoteAddress, ChannelFuture future);
    ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelFuture future);
    ChannelFuture disconnect(ChannelFuture future);
    ChannelFuture close(ChannelFuture future);
    ChannelFuture deregister(ChannelFuture future);
    ChannelFuture flush(ChannelFuture future);
    ChannelFuture write(Object message, ChannelFuture future);
}
