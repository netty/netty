package io.netty.handler.codec.memcache.ascii.request;


import io.netty.handler.codec.memcache.ascii.AbstractAsciiMemcacheMessage;
import io.netty.handler.codec.memcache.ascii.AsciiMemcacheRequest;

public class AsciiMemcacheTouchRequest extends AbstractAsciiMemcacheMessage implements AsciiMemcacheRequest {

    private final String key;
    private final int expiration;
    private boolean noreply;

    public AsciiMemcacheTouchRequest(String key, int expiration) {
        this(key, expiration, false);
    }

    public AsciiMemcacheTouchRequest(String key, int expiration, boolean noreply) {
        this.key = key;
        this.expiration = expiration;
        this.noreply = noreply;
    }

    public AsciiMemcacheTouchRequest setNoreply(boolean noreply) {
        this.noreply = noreply;
        return this;
    }

    public String getKey() {
        return key;
    }

    public int getExpiration() {
        return expiration;
    }

    public boolean getNoreply() {
        return noreply;
    }
}
