package io.netty.channel.uring;

import io.netty.channel.Channel;
import io.netty.channel.DefaultChannelConfig;
import io.netty.channel.RecvByteBufAllocator;

public class IOUringChannelConfig extends DefaultChannelConfig {
    public IOUringChannelConfig(Channel channel) {
        super(channel);
    }

    protected IOUringChannelConfig(Channel channel, RecvByteBufAllocator allocator) {
        super(channel, allocator);
    }
}
