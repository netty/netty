package io.netty.handler.codec.memcache.ascii;

public abstract class AbstractAsciiMemcacheRequest extends AbstractAsciiMemcacheMessage
    implements AsciiMemcacheRequest {

    private boolean noreply;

    protected  AbstractAsciiMemcacheRequest() {
        this(false);
    }

    protected AbstractAsciiMemcacheRequest(boolean noreply) {
        this.noreply = noreply;
    }

    @Override
    public boolean getNoreply() {
        return noreply;
    }

    @Override
    public AbstractAsciiMemcacheRequest setNoreply(boolean noreply) {
        this.noreply = noreply;
        return this;
    }
}
