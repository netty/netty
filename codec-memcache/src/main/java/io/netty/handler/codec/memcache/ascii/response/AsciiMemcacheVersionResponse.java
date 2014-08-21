package io.netty.handler.codec.memcache.ascii.response;

import io.netty.handler.codec.memcache.ascii.AbstractAsciiMemcacheMessage;
import io.netty.handler.codec.memcache.ascii.AsciiMemcacheResponse;

public class AsciiMemcacheVersionResponse extends AbstractAsciiMemcacheMessage implements AsciiMemcacheResponse {

    private final String version;

    public AsciiMemcacheVersionResponse(String version) {
        this.version = version;
    }

    public String getVersion() {
        return version;
    }
}
