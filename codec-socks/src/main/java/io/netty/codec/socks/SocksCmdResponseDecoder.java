package io.netty.codec.socks;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ReplayingDecoder;

import java.nio.charset.Charset;

public class SocksCmdResponseDecoder extends ReplayingDecoder<SocksResponse, SocksCmdResponseDecoder.State> {
    private static final String name = "SOCKS_CMD_RESPONSE_DECODER";

    public static String getName() {
        return name;
    }

    private SocksMessage.ProtocolVersion version;
    private int fieldLength;
    private SocksMessage.CmdStatus cmdStatus;
    private SocksMessage.AddressType addressType;
    private byte reserved;
    private String host;
    private int port;

    private SocksResponse msg = new UnknownSocksResponse();


    public SocksCmdResponseDecoder() {
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
                checkpoint(State.READ_CMD_HEADER);
            }
            case READ_CMD_HEADER: {
                cmdStatus = SocksMessage.CmdStatus.fromByte(byteBuf.readByte());
                reserved = byteBuf.readByte();
                addressType = SocksMessage.AddressType.fromByte(byteBuf.readByte());
                checkpoint(State.READ_CMD_ADDRESS);
            }
            case READ_CMD_ADDRESS: {
                switch (addressType) {
                    case IPv4: {
                        host = SocksCommonUtils.intToIp(byteBuf.readInt());
                        port = byteBuf.readUnsignedShort();
                        msg = new SocksCmdResponse(cmdStatus, addressType);
                        break;
                    }
                    case DOMAIN: {
                        fieldLength = byteBuf.readByte();
                        host = byteBuf.readBytes(fieldLength).toString(Charset.forName("US-ASCII"));
                        port = byteBuf.readUnsignedShort();
                        msg = new SocksCmdResponse(cmdStatus, addressType);
                        break;
                    }
                    case IPv6: {
                        host = SocksCommonUtils.ipv6toStr(byteBuf.readBytes(16).array());
                        port = byteBuf.readUnsignedShort();
                        msg = new SocksCmdResponse(cmdStatus, addressType);
                        break;
                    }
                    case UNKNOWN:
                        byteBuf.clear();
                        break;
                }
            }
        }
        ctx.pipeline().remove(this);
        return msg;
    }

    public enum State {
        CHECK_PROTOCOL_VERSION,
        READ_CMD_HEADER,
        READ_CMD_ADDRESS
    }
}
