package org.jboss.netty.handler.codec.redis;

import org.jboss.netty.buffer.ChannelBuffer;

import java.io.IOException;

public class PUnsubscribeReply extends UnsubscribeReply {

    public PUnsubscribeReply(byte[][] patterns) {
        super(patterns);
    }

    @Override
    public void write(ChannelBuffer os) throws IOException {

    }
}
