package io.netty.channel;

import io.netty.util.internal.ConversionUtil;

import java.util.concurrent.TimeUnit;

public class DefaultClientChannelConfig extends DefaultChannelConfig implements
        ClientChannelConfig {

    private static final long DEFAULT_CONNECT_TIMEOUT = TimeUnit.SECONDS.toMillis(30);

    private volatile long connectTimeoutMillis = DEFAULT_CONNECT_TIMEOUT;

    @Override
    public boolean setOption(String key, Object value) {
        if ("connectTimeoutMillis".equals(key)) {
            setConnectTimeoutMillis(ConversionUtil.toLong(value));
        } else {
            return false;
        }
        return true;
    }

    @Override
    public long getConnectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    @Override
    public void setConnectTimeoutMillis(long connectTimeoutMillis) {
        if (connectTimeoutMillis < 0) {
            throw new IllegalArgumentException("connectTimeoutMillis: " + connectTimeoutMillis);
        }
        this.connectTimeoutMillis = connectTimeoutMillis;
    }
}
