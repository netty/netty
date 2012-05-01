package io.netty.channel;

public interface ClientChannelConfig extends ChannelConfig {
    long getConnectTimeoutMillis();
    void setConnectTimeoutMillis(long connectTimeoutMillis);
}
