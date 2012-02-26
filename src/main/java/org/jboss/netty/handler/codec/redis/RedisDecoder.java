package org.jboss.netty.handler.codec.redis;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.replay.ReplayingDecoder;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

enum State {

}

public class RedisDecoder extends ReplayingDecoder<State> {

    private static final char CR = '\r';
    private static final char LF = '\n';
    private static final char ZERO = '0';

    // We track the current multibulk reply in the case
    // where we do not get a complete reply in a single
    // decode invocation.
    private MultiBulkReply reply;

    public byte[] readBytes(ChannelBuffer is) throws IOException {
        int size = readInteger(is);
        if (size == -1) {
            return null;
        }
        if (super.actualReadableBytes() < size + 2) {
            // Trigger error
            is.skipBytes(size + 2);
            throw new AssertionError("Trustin says this isn't possible");
        }
        byte[] bytes = new byte[size];
        is.readBytes(bytes, 0, size);
        int cr = is.readByte();
        int lf = is.readByte();
        if (cr != CR || lf != LF) {
            throw new IOException("Improper line ending: " + cr + ", " + lf);
        }
        return bytes;
    }

    public static int readInteger(ChannelBuffer is) throws IOException {
        int size = 0;
        int sign = 1;
        int read = is.readByte();
        if (read == '-') {
            read = is.readByte();
            sign = -1;
        }
        do {
            if (read == CR) {
                if (is.readByte() == LF) {
                    break;
                }
            }
            int value = read - ZERO;
            if (value >= 0 && value < 10) {
                size *= 10;
                size += value;
            } else {
                throw new IOException("Invalid character in integer");
            }
            read = is.readByte();
        } while (true);
        return size * sign;
    }

    public Reply receive(final ChannelBuffer is) throws IOException {
        int code = is.readByte();
        switch (code) {
            case StatusReply.MARKER: {
                return new StatusReply(new DataInputStream(new ChannelBufferInputStream(is)).readLine());
            }
            case ErrorReply.MARKER: {
                return new ErrorReply(new DataInputStream(new ChannelBufferInputStream(is)).readLine());
            }
            case IntegerReply.MARKER: {
                return new IntegerReply(readInteger(is));
            }
            case BulkReply.MARKER: {
                return new BulkReply(readBytes(is));
            }
            case MultiBulkReply.MARKER: {
                return decodeMultiBulkReply(is);
            }
            default: {
                throw new IOException("Unexpected character in stream: " + code);
            }
        }
    }

    @Override
    public void checkpoint() {
        super.checkpoint();
    }

    @Override
    protected Object decode(ChannelHandlerContext channelHandlerContext, Channel channel, ChannelBuffer channelBuffer, State anEnum) throws Exception {
        return receive(channelBuffer);
    }

    public MultiBulkReply decodeMultiBulkReply(ChannelBuffer is) throws IOException {
        if (reply == null) {
            reply = new MultiBulkReply();
        }
        reply.read(this, is);
        return reply;
    }

    private static class ChannelBufferInputStream extends InputStream {
        private final ChannelBuffer is;

        public ChannelBufferInputStream(ChannelBuffer is) {
            this.is = is;
        }

        @Override
        public int read() throws IOException {
            return is.readByte();
        }
    }
}
