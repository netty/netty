package org.jboss.netty.handler.codec.redis;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;

import java.io.IOException;
import java.nio.charset.Charset;

/**
 * Replies.
 * User: sam
 * Date: 7/27/11
 * Time: 3:04 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class Reply {

    public static final Charset UTF_8 = Charset.forName("UTF-8");

    public abstract void write(ChannelBuffer os) throws IOException;

    public String toString() {
        ChannelBuffer channelBuffer = ChannelBuffers.dynamicBuffer();
        try {
            write(channelBuffer);
        } catch (IOException e) {
            throw new AssertionError("Trustin says this won't happen either");
        }
        return channelBuffer.toString(UTF_8);
    }

}
