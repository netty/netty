package io.netty.channel;


public interface ChannelInboundInvoker {
    ChannelBufferHolder<Object> nextIn();

    void fireChannelRegistered();
    void fireChannelUnregistered();
    void fireChannelActive();
    void fireChannelInactive();
    void fireExceptionCaught(Throwable cause);
    void fireUserEventTriggered(Object event);
    void fireInboundBufferUpdated();
}
