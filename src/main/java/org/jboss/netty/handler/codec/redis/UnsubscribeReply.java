package org.jboss.netty.handler.codec.redis;

import org.jboss.netty.buffer.ChannelBuffer;

import java.io.IOException;

public class UnsubscribeReply extends Reply {

    private final byte[][] patterns;

    public UnsubscribeReply(byte[][] patterns) {
        this.patterns = patterns;
    }

    @Override
    public void write(ChannelBuffer os) throws IOException {

    }

    public byte[][] getPatterns() {
        return patterns;
    }
}
