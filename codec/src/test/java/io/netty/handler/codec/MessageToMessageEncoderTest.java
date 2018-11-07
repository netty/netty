/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.List;


public class MessageToMessageEncoderTest {

    /**
     * Test-case for https://github.com/netty/netty/issues/1656
     */
    @Test(expected = EncoderException.class)
    public void testException() {
        EmbeddedChannel channel = new EmbeddedChannel(new MessageToMessageEncoder<Object>() {
            @Override
            protected void encode(ChannelHandlerContext ctx, Object msg, List<Object> out) throws Exception {
                throw new Exception();
            }
        });
        channel.writeOutbound(new Object());
    }

    @Test
    public void testIntermediateWriteFailures() {
        ChannelHandler encoder = new MessageToMessageEncoder<Object>() {
            @Override
            protected void encode(ChannelHandlerContext ctx, Object msg, List<Object> out) {
                out.add(new Object());
                out.add(msg);
            }
        };

        final Exception firstWriteException = new Exception();

        ChannelHandler writeThrower = new ChannelOutboundHandlerAdapter() {
            private boolean firstWritten;
            @Override
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
                if (firstWritten) {
                    ctx.write(msg, promise);
                } else {
                    firstWritten = true;
                    promise.setFailure(firstWriteException);
                }
            }
        };

        EmbeddedChannel channel = new EmbeddedChannel(writeThrower, encoder);
        Object msg = new Object();
        ChannelFuture write = channel.writeAndFlush(msg);
        assertSame(firstWriteException, write.cause());
        assertSame(msg, channel.readOutbound());
        assertFalse(channel.finish());
    }
}
