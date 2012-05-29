package io.netty.channel;


public interface ChannelInboundInvoker {
    void fireChannelRegistered();
    void fireChannelUnregistered();
    void fireChannelActive();
    void fireChannelInactive();
    void fireExceptionCaught(Throwable cause);
    void fireUserEventTriggered(Object event);
    void fireInboundBufferUpdated();
}
