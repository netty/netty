package io.netty.codec.socks;

import io.netty.buffer.ByteBuf;

public final class SocksInitResponse extends SocksResponse {
    private AuthScheme authScheme;

    public SocksInitResponse(AuthScheme authScheme) {
        super(SocksResponseType.INIT);
        this.authScheme = authScheme;
    }

    public AuthScheme getAuthScheme() {
        return authScheme;
    }

    @Override
    public void encodeAsByteBuf(ByteBuf byteBuf) {
        byteBuf.writeByte(getProtocolVersion().getByteValue());
        byteBuf.writeByte(authScheme.getByteValue());
    }
}
