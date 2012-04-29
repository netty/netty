package io.netty.channel;

public class EventLoopException extends ChannelException {

    private static final long serialVersionUID = -8969100344583703616L;

    public EventLoopException() {
    }

    public EventLoopException(String message, Throwable cause) {
        super(message, cause);
    }

    public EventLoopException(String message) {
        super(message);
    }

    public EventLoopException(Throwable cause) {
        super(cause);
    }

}
