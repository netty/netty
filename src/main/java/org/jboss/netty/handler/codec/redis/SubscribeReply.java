package org.jboss.netty.handler.codec.redis;

import org.jboss.netty.buffer.ChannelBuffer;

import java.io.IOException;

public class SubscribeReply extends Reply {

    private final byte[][] patterns;

    public SubscribeReply(byte[][] patterns) {
        this.patterns = patterns;
    }

    public byte[][] getPatterns() {
        return patterns;
    }

    @Override
    public void write(ChannelBuffer os) throws IOException {
    }
}
