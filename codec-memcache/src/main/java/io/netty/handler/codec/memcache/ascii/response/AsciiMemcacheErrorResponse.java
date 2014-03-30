package io.netty.handler.codec.memcache.ascii.response;

import io.netty.handler.codec.memcache.ascii.AbstractAsciiMemcacheMessage;
import io.netty.handler.codec.memcache.ascii.AsciiMemcacheResponse;

public class AsciiMemcacheErrorResponse extends AbstractAsciiMemcacheMessage implements AsciiMemcacheResponse {

    private final ErrorResponse response;
    private final String message;

    public AsciiMemcacheErrorResponse(final ErrorResponse response, final String message) {
        this.response = response;
        this.message = message;
    }

    public AsciiMemcacheErrorResponse(final ErrorResponse response) {
        this(response, null);
    }

    public ErrorResponse getResponse() {
        return response;
    }

    public String getMessage() {
        return message;
    }

    public static enum ErrorResponse {
        ERROR("ERROR"),
        CLIENT_ERROR("CLIENT_ERROR"),
        EXISTS("SERVER_ERROR");

        private final String value;

        ErrorResponse(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

}
