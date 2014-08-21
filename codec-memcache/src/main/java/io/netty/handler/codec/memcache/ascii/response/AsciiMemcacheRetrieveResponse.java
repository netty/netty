package io.netty.handler.codec.memcache.ascii.response;

import io.netty.handler.codec.memcache.ascii.AbstractAsciiMemcacheMessage;
import io.netty.handler.codec.memcache.ascii.AsciiMemcacheResponse;

public class AsciiMemcacheRetrieveResponse extends AbstractAsciiMemcacheMessage implements AsciiMemcacheResponse {

    private final String key;
    private final int flags;
    private final int length;
    private long cas;

    public AsciiMemcacheRetrieveResponse(final String key, final int length) {
        this(key, length, 0);
    }

    public AsciiMemcacheRetrieveResponse(final String key, final int length, final int flags) {
        this.key = key;
        this.length = length;
        this.flags = flags;
    }

    public String getKey() {
        return key;
    }

    public int getFlags() {
        return flags;
    }

    public int getLength() {
        return length;
    }

    public long getCas() {
        return cas;
    }

    public AsciiMemcacheRetrieveResponse setCas(long cas) {
        this.cas = cas;
        return this;
    }
}
