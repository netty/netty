package io.netty.handler.codec.memcache.ascii.response;

import io.netty.handler.codec.memcache.ascii.AbstractAsciiMemcacheMessage;
import io.netty.handler.codec.memcache.ascii.AsciiMemcacheResponse;

public class AsciiMemcacheTouchResponse extends AbstractAsciiMemcacheMessage implements AsciiMemcacheResponse {

    private final TouchResponse response;

    public AsciiMemcacheTouchResponse(final TouchResponse response) {
        this.response = response;
    }

    public TouchResponse getResponse() {
        return response;
    }

    public static enum TouchResponse {
        TOUCHED("TOUCHED"),
        NOT_FOUND("NOT_FOUND");

        private final String value;

        TouchResponse(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}
