package io.netty.codec.socks;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ReplayingDecoder;

import java.util.ArrayList;
import java.util.List;

public class SocksInitRequestDecoder extends ReplayingDecoder<SocksRequest, SocksInitRequestDecoder.State> {
    private static final String name = "SOCKS_INIT_REQUEST_DECODER";

    public static String getName() {
        return name;
    }

    private SocksMessage.ProtocolVersion version;
    private List<SocksMessage.AuthScheme> authSchemes = new ArrayList<SocksMessage.AuthScheme>();
    private byte authSchemeNum;
    private SocksRequest msg = new UnknownSocksRequest();

    public SocksInitRequestDecoder() {
        super(State.CHECK_PROTOCOL_VERSION);
    }

    @Override
    public SocksRequest decode(ChannelHandlerContext ctx, ByteBuf byteBuf) throws Exception {
        switch (state()) {
            case CHECK_PROTOCOL_VERSION: {
                version = SocksMessage.ProtocolVersion.fromByte(byteBuf.readByte());
                if (version != SocksMessage.ProtocolVersion.SOCKS5) {
                    break;
                }
                checkpoint(State.READ_AUTH_SCHEMES);
            }
            case READ_AUTH_SCHEMES: {
                authSchemes.clear();
                authSchemeNum = byteBuf.readByte();
                for (int i = 0; i < authSchemeNum; i++) {
                    authSchemes.add(SocksMessage.AuthScheme.fromByte(byteBuf.readByte()));
                }
                msg = new SocksInitRequest(authSchemes);
                break;
            }
        }
        ctx.pipeline().remove(this);
        return msg;
    }

    enum State {
        CHECK_PROTOCOL_VERSION,
        READ_AUTH_SCHEMES
    }
}
