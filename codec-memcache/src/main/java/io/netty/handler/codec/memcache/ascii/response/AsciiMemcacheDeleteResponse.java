package io.netty.handler.codec.memcache.ascii.response;

import io.netty.handler.codec.memcache.ascii.AbstractAsciiMemcacheMessage;
import io.netty.handler.codec.memcache.ascii.AsciiMemcacheResponse;

public class AsciiMemcacheDeleteResponse extends AbstractAsciiMemcacheMessage implements AsciiMemcacheResponse {

    private final AsciiMemcacheResponseStatus status;

    public AsciiMemcacheDeleteResponse(final AsciiMemcacheResponseStatus status) {
        this.status = status;
    }

    public AsciiMemcacheResponseStatus getStatus() {
        return status;
    }

}
