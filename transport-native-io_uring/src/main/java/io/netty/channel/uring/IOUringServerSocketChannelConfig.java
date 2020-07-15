package io.netty.channel.uring;

import io.netty.channel.socket.ServerSocketChannelConfig;

public class IOUringServerSocketChannelConfig extends IOUringServerChannelConfig implements ServerSocketChannelConfig {

    IOUringServerSocketChannelConfig(AbstractIOUringServerChannel channel) {
        super(channel);
    }
}
