package io.netty.handler.codec.memcache.ascii.response;

import io.netty.handler.codec.memcache.ascii.AbstractAsciiMemcacheMessage;
import io.netty.handler.codec.memcache.ascii.AsciiMemcacheResponse;

public class AsciiMemcacheFlushResponse extends AbstractAsciiMemcacheMessage implements AsciiMemcacheResponse {

    public static final AsciiMemcacheFlushResponse INSTANCE = new AsciiMemcacheFlushResponse();

    private AsciiMemcacheFlushResponse() {
        // disallow construction
    }

}
