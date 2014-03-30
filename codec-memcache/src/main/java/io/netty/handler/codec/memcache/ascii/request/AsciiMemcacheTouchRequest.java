package io.netty.handler.codec.memcache.ascii.request;

import io.netty.handler.codec.memcache.ascii.AbstractAsciiMemcacheMessage;
import io.netty.handler.codec.memcache.ascii.AbstractAsciiMemcacheRequest;
import io.netty.handler.codec.memcache.ascii.AsciiMemcacheRequest;

public class AsciiMemcacheTouchRequest extends AbstractAsciiMemcacheRequest {

    private final String key;
    private final int expiration;

    public AsciiMemcacheTouchRequest(String key, int expiration) {
        this.key = key;
        this.expiration = expiration;
    }

    public String getKey() {
        return key;
    }

    public int getExpiration() {
        return expiration;
    }

}
