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
            os.writeBytes(Command.NEG_ONE_AND_CRLF);
        } else {
            os.writeBytes(Command.numAndCRLF(bytes.length));
            os.writeBytes(bytes);
            os.writeBytes(Command.CRLF);
        }
    }
}
