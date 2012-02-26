package org.jboss.netty.handler.codec.redis;

import org.jboss.netty.buffer.ChannelBuffer;

import java.io.IOException;

public class BulkReply extends Reply {
    public static final char MARKER = '$';
    public final byte[] bytes;

    public BulkReply(byte[] bytes) {
        this.bytes = bytes;
    }

    public void write(ChannelBuffer os) throws IOException {
        os.writeByte(MARKER);
        if (bytes == null) {
            os.writeBytes(Command.NEG_ONE);
        } else {
            os.writeBytes(Command.numToBytes(bytes.length));
            os.writeBytes(Command.CRLF);
            os.writeBytes(bytes);
        }
        os.writeBytes(Command.CRLF);
    }
}
