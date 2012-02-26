package org.jboss.netty.handler.codec.redis;

import org.jboss.netty.buffer.ChannelBuffer;

import java.io.IOException;

public class StatusReply extends Reply {
    public static final char MARKER = '+';
    public final String status;

    public StatusReply(String status) {
        this.status = status;
    }

    public void write(ChannelBuffer os) throws IOException {
        os.writeByte(MARKER);
        os.writeBytes(status.getBytes(UTF_8));
        os.writeBytes(Command.CRLF);
    }
}
