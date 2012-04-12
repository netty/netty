package io.netty.channel;

import java.util.Queue;

public interface ChannelWriterContext<T> extends ChannelHandlerContext {
    Queue<T> out();
}
