package io.netty.codec.socks;

import io.netty.buffer.ByteBuf;

public class UnknownSocksResponse extends SocksResponse {

    public UnknownSocksResponse() {
        super(SocksResponseType.UNKNOWN);
    }

    @Override
    public void encodeAsByteBuf(ByteBuf byteBuf) {
        //To change body of implemented methods use File | Settings | File Templates.
    }
}
