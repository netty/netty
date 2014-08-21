package io.netty.handler.codec.memcache.ascii.request;

import io.netty.handler.codec.memcache.ascii.AbstractAsciiMemcacheMessage;
import io.netty.handler.codec.memcache.ascii.AbstractAsciiMemcacheRequest;
import io.netty.handler.codec.memcache.ascii.AsciiMemcacheRequest;

public class AsciiMemcacheVersionRequest extends AbstractAsciiMemcacheRequest {

    public static final AsciiMemcacheVersionRequest INSTANCE = new AsciiMemcacheVersionRequest();

    private AsciiMemcacheVersionRequest() {
        // disallow construction
    }

    @Override
    public AsciiMemcacheVersionRequest setNoreply(boolean noreply) {
        throw new IllegalStateException("noreply is not supported on the version request.");
    }

}
