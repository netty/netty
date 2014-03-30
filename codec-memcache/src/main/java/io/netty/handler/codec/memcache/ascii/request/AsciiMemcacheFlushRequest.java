package io.netty.handler.codec.memcache.ascii.request;

import io.netty.handler.codec.memcache.ascii.AbstractAsciiMemcacheMessage;
import io.netty.handler.codec.memcache.ascii.AsciiMemcacheRequest;

public class AsciiMemcacheFlushRequest extends AbstractAsciiMemcacheMessage implements AsciiMemcacheRequest {

    private boolean noreply;

    public AsciiMemcacheFlushRequest() {
        this.noreply = false;
    }

    public boolean getNoreply() {
        return noreply;
    }

    public AsciiMemcacheFlushRequest setNoreply(boolean noreply) {
        this.noreply = noreply;
        return this;
    }
}
