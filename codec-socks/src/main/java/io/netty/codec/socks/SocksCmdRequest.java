package io.netty.codec.socks;

import io.netty.buffer.ByteBuf;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

public final class SocksCmdRequest extends SocksRequest {
    private final CmdType cmdType;
    private final AddressType addressType;
    private final String host;
    private final int port;

    public SocksCmdRequest(CmdType cmdType, AddressType addressType, String host, int port) {
        super(SocksRequestType.CMD);
        this.cmdType = cmdType;
        this.addressType = addressType;
        this.host = host;
        this.port = port;
    }

    public CmdType getCmdType() {
        return cmdType;
    }

    public AddressType getAddressType() {
        return addressType;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    @Override
    public void encodeAsByteBuf(ByteBuf byteBuf) {
        byteBuf.writeByte(getProtocolVersion().getByteValue());
        byteBuf.writeByte(cmdType.getByteValue());
        byteBuf.writeByte(0x00);
        byteBuf.writeByte(addressType.getByteValue());
        switch (addressType) {

            case IPv4: {
                throw new NotImplementedException();
            }

            case DOMAIN: {
                byteBuf.writeByte(host.length());
                byteBuf.writeBytes(host.getBytes());
                byteBuf.writeShort(port);
                break;
            }

            case IPv6: {
                throw new NotImplementedException();
            }
        }
    }
}
