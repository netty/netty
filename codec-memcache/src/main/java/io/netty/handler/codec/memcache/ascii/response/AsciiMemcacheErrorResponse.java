package io.netty.handler.codec.memcache.ascii.response;

import io.netty.handler.codec.memcache.ascii.AbstractAsciiMemcacheMessage;
import io.netty.handler.codec.memcache.ascii.AsciiMemcacheResponse;

public class AsciiMemcacheErrorResponse extends AbstractAsciiMemcacheMessage implements AsciiMemcacheResponse {

    private final AsciiMemcacheResponseStatus status;

    public AsciiMemcacheErrorResponse(final AsciiMemcacheResponseStatus status) {
        this.status = status;
    }

    public AsciiMemcacheResponseStatus getStatus() {
        return status;
    }

}
