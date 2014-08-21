package io.netty.handler.codec.memcache.ascii.request;

import io.netty.handler.codec.memcache.ascii.AbstractAsciiMemcacheMessage;
import io.netty.handler.codec.memcache.ascii.AbstractAsciiMemcacheRequest;
import io.netty.handler.codec.memcache.ascii.AsciiMemcacheRequest;

public class AsciiMemcacheDeleteRequest extends AbstractAsciiMemcacheRequest {

    private final String key;

    public AsciiMemcacheDeleteRequest(final String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }

}
