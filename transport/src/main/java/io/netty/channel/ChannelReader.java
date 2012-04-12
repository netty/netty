package io.netty.channel;

import java.util.Queue;

public interface ChannelReader<T> extends ChannelHandler {

    void channelRegistered(ChannelReaderContext<T> ctx) throws Exception;
    void channelUnregistered(ChannelReaderContext<T> ctx) throws Exception;

    void channelActive(ChannelReaderContext<T> ctx) throws Exception;
    void channelInactive(ChannelReaderContext<T> ctx) throws Exception;

    void exceptionCaught(ChannelReaderContext<T> ctx, Throwable cause) throws Exception;
    void userEventTriggered(ChannelReaderContext<T> ctx, Object evt) throws Exception;

    Queue<T> newReceiveBuffer(ChannelReaderContext<T> ctx) throws Exception;
    void receiveBufferUpdated(ChannelReaderContext<T> ctx) throws Exception;
    void receiveBufferClosed(ChannelReaderContext<T> ctx) throws Exception;
}
