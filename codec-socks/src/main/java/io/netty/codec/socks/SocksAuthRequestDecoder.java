package io.netty.codec.socks;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ReplayingDecoder;

import java.nio.charset.Charset;

public class SocksAuthRequestDecoder extends ReplayingDecoder<SocksRequest, SocksAuthRequestDecoder.State> {
    private static final String name = "SOCKS_AUTH_REQUEST_DECODER";

    public static String getName() {
        return name;
    }

    private SocksMessage.ProtocolVersion version;
    private int fieldLength;
    private String username;
    private String password;
    private SocksRequest msg = new UnknownSocksRequest();

    public SocksAuthRequestDecoder() {
        super(State.CHECK_PROTOCOL_VERSION);
    }

    @Override
    public SocksRequest decode(ChannelHandlerContext ctx, ByteBuf byteBuf) throws Exception {
        switch (state()) {
            case CHECK_PROTOCOL_VERSION: {
                version = SocksMessage.ProtocolVersion.fromByte((byte) byteBuf.readByte());
                System.out.println(" " + version);
                if (version != SocksMessage.ProtocolVersion.SOCKS5) {
                    return new UnknownSocksRequest();
                }
                checkpoint(State.READ_USERNAME);
            }
            case READ_USERNAME: {
                fieldLength = byteBuf.readByte();
                username = byteBuf.readBytes(fieldLength).toString(Charset.forName("US-ASCII"));
                checkpoint(State.READ_PASSWORD);
            }
            case READ_PASSWORD: {
                fieldLength = byteBuf.readByte();
                password = byteBuf.readBytes(fieldLength).toString(Charset.forName("US-ASCII"));
                msg = new SocksAuthRequest(username, password);
            }
        }
        ctx.pipeline().remove(this);
        return msg;
    }

    enum State {
        CHECK_PROTOCOL_VERSION,
        READ_USERNAME,
        READ_PASSWORD
    }
}
