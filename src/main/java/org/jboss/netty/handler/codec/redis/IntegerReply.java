package org.jboss.netty.handler.codec.redis;

import org.jboss.netty.buffer.ChannelBuffer;

import java.io.IOException;

public class IntegerReply extends Reply {
    public static final char MARKER = ':';
    public final long integer;

    public IntegerReply(long integer) {
        this.integer = integer;
    }

    public void write(ChannelBuffer os) throws IOException {
        os.writeByte(MARKER);
        os.writeBytes(Command.numToBytes(integer));
        os.writeBytes(Command.CRLF);
    }
}
