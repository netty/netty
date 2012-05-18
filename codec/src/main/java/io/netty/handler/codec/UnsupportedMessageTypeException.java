package io.netty.handler.codec;

public class UnsupportedMessageTypeException extends CodecException {

    private static final long serialVersionUID = 2799598826487038726L;

    public UnsupportedMessageTypeException(
            Object message, Class<?> expectedType, Class<?>... otherExpectedTypes) {
        super(message(
                message == null? "null" : message.getClass().getName(),
                expectedType, otherExpectedTypes));
    }

    public UnsupportedMessageTypeException() {
        super();
    }

    public UnsupportedMessageTypeException(String message, Throwable cause) {
        super(message, cause);
    }

    public UnsupportedMessageTypeException(String s) {
        super(s);
    }

    public UnsupportedMessageTypeException(Throwable cause) {
        super(cause);
    }

    private static String message(
            String actualType, Class<?> expectedType, Class<?>... otherExpectedTypes) {
        if (expectedType == null) {
            throw new NullPointerException("expectedType");
        }

        StringBuilder buf = new StringBuilder(actualType);
        buf.append(" (expected: ").append(expectedType.getName());

        if (otherExpectedTypes != null) {
            for (Class<?> t: otherExpectedTypes) {
                if (t == null) {
                    break;
                }
                buf.append(", ").append(t.getName());
            }
        }

        return buf.append(')').toString();
    }
}
