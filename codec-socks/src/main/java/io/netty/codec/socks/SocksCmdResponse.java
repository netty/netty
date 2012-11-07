package io.netty.codec.socks;

import io.netty.buffer.ByteBuf;

public final class SocksCmdResponse extends SocksResponse {
    private final CmdStatus cmdStatus;
    private final AddressType addressType;
    private static final byte IPv4_HOSTNAME_ZEROED[] = {0x00, 0x00, 0x00, 0x00};
    private static final byte IPv6_HOSTNAME_ZEROED[] = {0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};

    public SocksCmdResponse(CmdStatus cmdStatus, AddressType addressType) {
        super(SocksResponseType.CMD);
        this.cmdStatus = cmdStatus;
        this.addressType = addressType;
    }

    public CmdStatus getCmdStatus() {
        return cmdStatus;
    }

    @Override
    public void encodeAsByteBuf(ByteBuf byteBuf) {
        byteBuf.writeByte(getProtocolVersion().getByteValue());
        byteBuf.writeByte(cmdStatus.getByteValue());
        byteBuf.writeByte(0x00);
        byteBuf.writeByte(addressType.getByteValue());
        switch (addressType) {
            case IPv4: {
                byteBuf.writeBytes(IPv4_HOSTNAME_ZEROED);
                byteBuf.writeShort(0);
                break;
            }
            case DOMAIN: {
                byteBuf.writeByte(1);   // domain length
                byteBuf.writeByte(0);   // domain value
                byteBuf.writeShort(0);  // port value
                break;
            }
            case IPv6: {
                byteBuf.writeBytes(IPv6_HOSTNAME_ZEROED);
                byteBuf.writeShort(0);
                break;
            }
        }
    }
}
