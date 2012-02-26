package org.jboss.netty.handler.codec.redis;

public class PSubscribeReply extends SubscribeReply {

    public PSubscribeReply(byte[][] patterns) {
        super(patterns);
    }

}
