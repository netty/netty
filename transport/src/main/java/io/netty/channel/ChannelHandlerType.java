package io.netty.channel;

public enum ChannelHandlerType {
    STATE(0),
    OPERATION(1),
    INBOUND(0),
    OUTBOUND(1);

    final int direction; // 0 - up (inbound), 1 - down (outbound)

    private ChannelHandlerType(int direction) {
        this.direction = direction;
    }
}
