package io.netty.codec.socks;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ReplayingDecoder;

import java.nio.charset.Charset;

public class SocksCmdRequestDecoder extends ReplayingDecoder<SocksRequest, SocksCmdRequestDecoder.State> {
    private static final String name = "SOCKS_CMD_REQUEST_DECODER";

    public static String getName() {
        return name;
    }

    private SocksMessage.ProtocolVersion version;
    private int fieldLength;
    private SocksMessage.CmdType cmdType;
    private SocksMessage.AddressType addressType;
    private byte reserved;
    private String host;
    private int port;
    private SocksRequest msg = new UnknownSocksRequest();

    public SocksCmdRequestDecoder() {
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
                checkpoint(State.READ_CMD_HEADER);
            }
            case READ_CMD_HEADER: {
                cmdType = SocksMessage.CmdType.fromByte(byteBuf.readByte());
                reserved = byteBuf.readByte();
                addressType = SocksMessage.AddressType.fromByte(byteBuf.readByte());
                checkpoint(State.READ_CMD_ADDRESS);
            }
            case READ_CMD_ADDRESS: {
                switch (addressType) {
                    case IPv4: {
                        host = SocksCommonUtils.intToIp(byteBuf.readInt());
                        port = byteBuf.readUnsignedShort();
                        msg = new SocksCmdRequest(cmdType, addressType, host, port);
                        break;
                    }
                    case DOMAIN: {
                        fieldLength = byteBuf.readByte();
                        host = byteBuf.readBytes(fieldLength).toString(Charset.forName("US-ASCII"));
                        port = byteBuf.readUnsignedShort();
                        msg = new SocksCmdRequest(cmdType, addressType, host, port);
                        break;
                    }
                    case IPv6:
                    case UNKNOWN:
                        byteBuf.clear();
                        msg = new UnknownSocksRequest();
                        break;
                }
            }
        }
        ctx.pipeline().remove(this);
        return msg;  //To change body of implemented methods use File | Settings | File Templates.
    }

    enum State {
        CHECK_PROTOCOL_VERSION,
        READ_CMD_HEADER,
        READ_CMD_ADDRESS
    }

}
