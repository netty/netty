package io.netty.codec.socks;

import io.netty.buffer.ByteBuf;

public class UnknownSocksMessage extends SocksMessage {

    public UnknownSocksMessage() {
        super(MessageType.UNKNOWN);
    }

    @Override
    public void encodeAsByteBuf(ByteBuf byteBuf) {
        //To change body of implemented methods use File | Settings | File Templates.
    }
}
