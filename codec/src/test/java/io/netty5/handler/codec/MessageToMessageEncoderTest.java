/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty5.handler.codec;

import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.embedded.EmbeddedChannel;
import io.netty5.util.concurrent.Future;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class MessageToMessageEncoderTest {

    /**
     * Test-case for https://github.com/netty/netty/issues/1656
     */
    @Test
    public void testException() {
        EmbeddedChannel channel = new EmbeddedChannel(new MessageToMessageEncoder<Object>() {
            @Override
            protected void encode(ChannelHandlerContext ctx, Object msg, List<Object> out) throws Exception {
                throw new Exception();
            }
        });
        assertThrows(EncoderException.class, () -> channel.writeOutbound(new Object()));
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

        ChannelHandler writeThrower = new ChannelHandler() {
            private boolean firstWritten;
            @Override
            public Future<Void> write(ChannelHandlerContext ctx, Object msg) {
                if (firstWritten) {
                    return ctx.write(msg);
                } else {
                    firstWritten = true;
                    return ctx.newFailedFuture(firstWriteException);
                }
            }
        };

        EmbeddedChannel channel = new EmbeddedChannel(writeThrower, encoder);
        Object msg = new Object();
        Future<Void> write = channel.writeAndFlush(msg);
        assertSame(firstWriteException, write.cause());
        assertSame(msg, channel.readOutbound());
        assertFalse(channel.finish());
    }
}
