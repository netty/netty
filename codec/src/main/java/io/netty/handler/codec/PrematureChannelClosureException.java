package io.netty.handler.codec;

import io.netty.channel.Channel;

/**
 * A {@link CodecException} which is thrown when a {@link Channel} is closed unexpectedly before
 * the codec finishes handling the current message, such as missing response while waiting for a
 * request.
 */
public class PrematureChannelClosureException extends CodecException {

    private static final long serialVersionUID = 4907642202594703094L;

    /**
     * Creates a new instance.
     */
    public PrematureChannelClosureException() {
        super();
    }

    /**
     * Creates a new instance.
     */
    public PrematureChannelClosureException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates a new instance.
     */
    public PrematureChannelClosureException(String message) {
        super(message);
    }

    /**
     * Creates a new instance.
     */
    public PrematureChannelClosureException(Throwable cause) {
        super(cause);
    }
}
