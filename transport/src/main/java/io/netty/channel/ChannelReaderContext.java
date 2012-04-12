package io.netty.channel;

import java.util.Queue;

public interface ChannelReaderContext<T> extends ChannelHandlerContext {
    Queue<T> in();
}
