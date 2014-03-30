package io.netty.handler.codec.memcache.ascii.response;

public final class AsciiMemcacheResponseStatus {

    private final String msg;
    private final boolean mutable;
    private String description;

    private AsciiMemcacheResponseStatus(final String msg) {
        this(msg, false);
    }

    private AsciiMemcacheResponseStatus(final String msg, final boolean mutable) {
        this.msg = msg;
        this.mutable = mutable;
    }

    public String getMsg() {
        return msg;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        if (!mutable) {
            throw new IllegalStateException("This response status type does not allow descriptions.");
        }
        this.description = description;
    }

    private static final AsciiMemcacheResponseStatus DELETED = new AsciiMemcacheResponseStatus("DELETED");
    private static final AsciiMemcacheResponseStatus NOT_FOUND = new AsciiMemcacheResponseStatus("NOT_FOUND");
    private static final AsciiMemcacheResponseStatus ERROR = new AsciiMemcacheResponseStatus("ERROR");
    private static final AsciiMemcacheResponseStatus STORED = new AsciiMemcacheResponseStatus("STORED");
    private static final AsciiMemcacheResponseStatus NOT_STORED = new AsciiMemcacheResponseStatus("NOT_STORED");
    private static final AsciiMemcacheResponseStatus EXISTS = new AsciiMemcacheResponseStatus("EXISTS");
    private static final AsciiMemcacheResponseStatus TOUCHED = new AsciiMemcacheResponseStatus("TOUCHED");

    public static final AsciiMemcacheResponseStatus clientError() {
        return new AsciiMemcacheResponseStatus("CLIENT_ERROR", true);
    }

    public static final AsciiMemcacheResponseStatus serverError() {
        return new AsciiMemcacheResponseStatus("SERVER_ERROR", true);
    }

    public static final AsciiMemcacheResponseStatus deleted() {
        return DELETED;
    }

    public static final AsciiMemcacheResponseStatus notFound() {
        return NOT_FOUND;
    }

    public static final AsciiMemcacheResponseStatus error() {
        return ERROR;
    }

    public static final AsciiMemcacheResponseStatus stored() {
        return STORED;
    }

    public static final AsciiMemcacheResponseStatus notStored() {
        return NOT_STORED;
    }

    public static final AsciiMemcacheResponseStatus exists() {
        return EXISTS;
    }

    public static final AsciiMemcacheResponseStatus touched() {
        return TOUCHED;
    }

}
