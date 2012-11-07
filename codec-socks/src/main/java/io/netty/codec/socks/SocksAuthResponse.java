package io.netty.codec.socks;

import io.netty.buffer.ByteBuf;

public final class SocksAuthResponse extends SocksResponse {

    private AuthStatus authStatus;

    public SocksAuthResponse(AuthStatus authStatus) {
        super(SocksResponseType.AUTH);
        this.authStatus = authStatus;
    }

    public AuthStatus getAuthStatus() {
        return authStatus;
    }

    @Override
    public void encodeAsByteBuf(ByteBuf byteBuf) {
        byteBuf.writeByte(getProtocolVersion().getByteValue());
        byteBuf.writeByte(authStatus.getByteValue());
    }
}
