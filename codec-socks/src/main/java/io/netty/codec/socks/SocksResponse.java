package io.netty.codec.socks;

public abstract class SocksResponse extends SocksMessage {
    private SocksResponseType socksResponseType;

    public SocksResponse(SocksResponseType socksResponseType) {
        super(MessageType.RESPONSE);
        this.socksResponseType = socksResponseType;
    }

    public SocksResponseType getSocksResponseType() {
        return socksResponseType;
    }

    public enum SocksResponseType {
        INIT,
        AUTH,
        CMD,
        UNKNOWN
    }
}
