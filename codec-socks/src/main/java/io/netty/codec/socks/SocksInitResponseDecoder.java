package io.netty.codec.socks;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ReplayingDecoder;

public class SocksInitResponseDecoder extends ReplayingDecoder<SocksResponse, SocksInitResponseDecoder.State> {
    private static final String name = "SOCKS_INIT_RESPONSE_DECODER";

    public static String getName() {
        return name;
    }

    private SocksMessage.ProtocolVersion version;
    private SocksMessage.AuthScheme authScheme;

    private SocksResponse msg = new UnknownSocksResponse();

    public SocksInitResponseDecoder() {
        super(State.CHECK_PROTOCOL_VERSION);
    }

    @Override
    public SocksResponse decode(ChannelHandlerContext ctx, ByteBuf byteBuf) throws Exception {
        switch (state()) {
            case CHECK_PROTOCOL_VERSION: {
                version = SocksMessage.ProtocolVersion.fromByte(byteBuf.readByte());
                if (version != SocksMessage.ProtocolVersion.SOCKS5) {
                    break;
                }
                checkpoint(State.READ_PREFFERED_AUTH_TYPE);
            }
            case READ_PREFFERED_AUTH_TYPE: {
                authScheme = SocksMessage.AuthScheme.fromByte(byteBuf.readByte());
                msg = new SocksInitResponse(authScheme);
                break;
            }
        }
        ctx.pipeline().remove(this);
        return msg;
    }

    public enum State {
        CHECK_PROTOCOL_VERSION,
        READ_PREFFERED_AUTH_TYPE
    }
}
