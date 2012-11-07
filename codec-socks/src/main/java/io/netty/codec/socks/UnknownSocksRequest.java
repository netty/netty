package io.netty.codec.socks;

import io.netty.buffer.ByteBuf;

public class UnknownSocksRequest extends SocksRequest {

    public UnknownSocksRequest() {
        super(SocksRequestType.UNKNOWN);
    }

    @Override
    public void encodeAsByteBuf(ByteBuf byteBuf) {
        //To change body of implemented methods use File | Settings | File Templates.
    }
}
