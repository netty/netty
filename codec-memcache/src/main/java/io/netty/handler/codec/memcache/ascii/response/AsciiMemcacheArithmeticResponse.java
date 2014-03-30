package io.netty.handler.codec.memcache.ascii.response;


import io.netty.handler.codec.memcache.ascii.AbstractAsciiMemcacheMessage;
import io.netty.handler.codec.memcache.ascii.AsciiMemcacheResponse;

public class AsciiMemcacheArithmeticResponse extends AbstractAsciiMemcacheMessage implements AsciiMemcacheResponse {

    private final boolean found;
    private final long value;

    public static final AsciiMemcacheArithmeticResponse NOT_FOUND = new AsciiMemcacheArithmeticResponse();

    private AsciiMemcacheArithmeticResponse() {
        found = false;
        value = 0;
    }

    public AsciiMemcacheArithmeticResponse(long value) {
        found = true;
        this.value = value;
    }

    public boolean getFound() {
        return found;
    }

    public long getValue() {
        return value;
    }

}
