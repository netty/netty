package io.netty.codec.socks;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ReplayingDecoder;

public class SocksAuthResponseDecoder extends ReplayingDecoder<SocksResponse, SocksAuthResponseDecoder.State> {
    private static final String name = "SOCKS_AUTH_RESPONSE_DECODER";

    public static String getName() {
        return name;
    }

    private SocksMessage.ProtocolVersion version;
    private SocksMessage.AuthStatus authStatus;
    private SocksResponse msg = new UnknownSocksResponse();

    public SocksAuthResponseDecoder() {
        super(State.CHECK_PROTOCOL_VERSION);
    }

    @Override
    public SocksResponse decode(ChannelHandlerContext channelHandlerContext, ByteBuf byteBuf) throws Exception {
        switch (state()) {
            case CHECK_PROTOCOL_VERSION: {
                version = SocksMessage.ProtocolVersion.fromByte(byteBuf.readByte());
                if (version != SocksMessage.ProtocolVersion.SOCKS5) {
                    return new UnknownSocksResponse();
                }
                checkpoint(State.READ_AUTH_RESPONSE);
            }
            case READ_AUTH_RESPONSE: {
                authStatus = SocksMessage.AuthStatus.fromByte(byteBuf.readByte());
                msg = new SocksAuthResponse(authStatus);
            }
        }
        channelHandlerContext.pipeline().remove(this);
        return msg;
    }

    public enum State {
        CHECK_PROTOCOL_VERSION,
        READ_AUTH_RESPONSE
    }
}
