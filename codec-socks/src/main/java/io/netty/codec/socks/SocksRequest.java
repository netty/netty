package io.netty.codec.socks;

public abstract class SocksRequest extends SocksMessage {
    private SocksRequestType socksRequestType;

    public SocksRequest(SocksRequestType socksRequestType) {
        super(MessageType.REQUEST);
        this.socksRequestType = socksRequestType;
    }

    public SocksRequestType getSocksRequestType() {
        return socksRequestType;
    }

    public enum SocksRequestType {
        INIT,
        AUTH,
        CMD,
        UNKNOWN
    }
}
