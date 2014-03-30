package io.netty.handler.codec.memcache.ascii.response;

import io.netty.handler.codec.memcache.ascii.AbstractAsciiMemcacheMessage;
import io.netty.handler.codec.memcache.ascii.AsciiMemcacheResponse;

public class AsciiMemcacheDeleteResponse extends AbstractAsciiMemcacheMessage implements AsciiMemcacheResponse {

    private final DeleteResponse response;

    public AsciiMemcacheDeleteResponse(final DeleteResponse response) {
        this.response = response;
    }

    public DeleteResponse getResponse() {
        return response;
    }


    public static enum DeleteResponse {
        DELETED("DELETED"),
        NOT_FOUND("NOT_FOUND");

        private final String value;

        DeleteResponse(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

}
