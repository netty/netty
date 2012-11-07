package io.netty.codec.socks;

import io.netty.buffer.ByteBuf;

import java.util.List;

public final class SocksInitRequest extends SocksRequest {
    private List<AuthScheme> authSchemes;

    public SocksInitRequest(List<AuthScheme> authSchemes) {
        super(SocksRequestType.INIT);
        this.authSchemes = authSchemes;
    }

    public List<AuthScheme> getAuthSchemes() {
        return authSchemes;
    }

    @Override
    public void encodeAsByteBuf(ByteBuf byteBuf) {
        byteBuf.writeByte(getProtocolVersion().getByteValue());
        byteBuf.writeByte(authSchemes.size());
        for (AuthScheme authScheme : authSchemes) {
            byteBuf.writeByte(authScheme.getByteValue());
        }
    }
}
