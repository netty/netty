package io.netty.handler.codec.memcache.ascii.request;

import io.netty.handler.codec.memcache.ascii.AbstractAsciiMemcacheMessage;
import io.netty.handler.codec.memcache.ascii.AsciiMemcacheRequest;

public class AsciiMemcacheDeleteRequest extends AbstractAsciiMemcacheMessage implements AsciiMemcacheRequest {

    private final String key;
    private final boolean noreply;

    public AsciiMemcacheDeleteRequest(final String key) {
        this(key, false);
    }

    public AsciiMemcacheDeleteRequest(final String key, final boolean noreply) {
        this.key = key;
        this.noreply = noreply;
    }

    public String getKey() {
        return key;
    }

    public boolean getNoreply() {
        return noreply;
    }
}
