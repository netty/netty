package io.netty.codec.socks;

import io.netty.buffer.ByteBuf;

public final class SocksAuthRequest extends SocksRequest {
    private final String username;
    private final String password;

    public SocksAuthRequest(String username, String password) {
        super(SocksRequestType.AUTH);
        this.username = username;
        this.password = password;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    @Override
    public void encodeAsByteBuf(ByteBuf byteBuf) {
        byteBuf.writeByte(getProtocolVersion().getByteValue());
        byteBuf.writeByte(username.length());
        byteBuf.writeBytes(username.getBytes());
        byteBuf.writeByte(password.length());
        byteBuf.writeBytes(password.getBytes());
    }
}
