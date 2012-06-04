package io.netty.channel;

/**
 * A {@link ChannelPipelineException} which is raised if an inbound or outbound buffer of
 * the expected type is not found while transferring data between {@link ChannelHandler}s.
 * This exception is usually triggered by an incorrectly configured {@link ChannelPipeline}.
 */
public class NoSuchBufferException extends ChannelPipelineException {

    private static final String DEFAULT_MESSAGE =
            "Could not find a suitable destination buffer.  Double-check if the pipeline is " +
            "configured correctly and its handlers works as expected.";

    private static final long serialVersionUID = -131650785896627090L;

    public NoSuchBufferException() {
        this(DEFAULT_MESSAGE);
    }

    public NoSuchBufferException(String message, Throwable cause) {
        super(message, cause);
    }

    public NoSuchBufferException(String message) {
        super(message);
    }

    public NoSuchBufferException(Throwable cause) {
        super(cause);
    }
}
