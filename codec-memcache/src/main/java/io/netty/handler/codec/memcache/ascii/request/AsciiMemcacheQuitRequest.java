package io.netty.handler.codec.memcache.ascii.request;

import io.netty.handler.codec.memcache.ascii.AbstractAsciiMemcacheMessage;
import io.netty.handler.codec.memcache.ascii.AbstractAsciiMemcacheRequest;
import io.netty.handler.codec.memcache.ascii.AsciiMemcacheRequest;

public class AsciiMemcacheQuitRequest extends AbstractAsciiMemcacheRequest {

    public static final AsciiMemcacheQuitRequest INSTANCE = new AsciiMemcacheQuitRequest();

    private AsciiMemcacheQuitRequest() {
        // disallow construction
    }

    @Override
    public AsciiMemcacheQuitRequest setNoreply(boolean noreply) {
        throw new IllegalStateException("noreply is not supported on the quit request.");
    }

}
