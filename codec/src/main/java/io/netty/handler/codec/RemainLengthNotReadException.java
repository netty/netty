package io.netty.handler.codec;

public class RemainLengthNotReadException extends DecoderException {
    private static final long serialVersionUID = -1995801950698951641L;

    public RemainLengthNotReadException() {
    }

    public RemainLengthNotReadException(String message, Throwable cause) {
        super(message, cause);
    }

    public RemainLengthNotReadException(String message) {
        super(message);
    }

    public RemainLengthNotReadException(Throwable cause) {
        super(cause);
    }
}
