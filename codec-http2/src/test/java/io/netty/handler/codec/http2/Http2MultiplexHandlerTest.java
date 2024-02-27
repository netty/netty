/*
 * Copyright 2019 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec.http2;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import javax.net.ssl.SSLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link Http2MultiplexHandler}.
 */
public class Http2MultiplexHandlerTest extends Http2MultiplexTest<Http2FrameCodec> {

    @Override
    protected Http2FrameCodec newCodec(TestChannelInitializer childChannelInitializer, Http2FrameWriter frameWriter) {
        return new Http2FrameCodecBuilder(true).frameWriter(frameWriter).build();
    }

    @Override
    protected ChannelHandler newMultiplexer(TestChannelInitializer childChannelInitializer) {
        return new Http2MultiplexHandler(childChannelInitializer, null);
    }

    @Override
    protected boolean useUserEventForResetFrame() {
        return true;
    }

    @Override
    protected boolean ignoreWindowUpdateFrames() {
        return true;
    }

    @Test
    public void sslExceptionTriggersChildChannelException() {
        final LastInboundHandler inboundHandler = new LastInboundHandler();
        Http2StreamChannel channel = newInboundStream(3, false, inboundHandler);
        assertTrue(channel.isActive());
        final RuntimeException testExc = new RuntimeException(new SSLException("foo"));
        channel.parent().pipeline().addLast(new ChannelInboundHandlerAdapter() {
            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                if (cause != testExc) {
                    super.exceptionCaught(ctx, cause);
                }
            }
        });
        channel.parent().pipeline().fireExceptionCaught(testExc);

        assertTrue(channel.isActive());
        RuntimeException exc = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                inboundHandler.checkException();
            }
        });
        assertEquals(testExc, exc);
    }

    @Test
    public void customExceptionForwarding() {
        final LastInboundHandler inboundHandler = new LastInboundHandler();
        Http2StreamChannel channel = newInboundStream(3, false, inboundHandler);
        assertTrue(channel.isActive());
        final RuntimeException testExc = new RuntimeException("xyz");
        channel.parent().pipeline().addLast(new ChannelInboundHandlerAdapter() {
            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                if (cause != testExc) {
                    super.exceptionCaught(ctx, cause);
                } else {
                    ctx.pipeline().fireExceptionCaught(new Http2MultiplexActiveStreamsException(cause));
                }
            }
        });
        channel.parent().pipeline().fireExceptionCaught(testExc);

        assertTrue(channel.isActive());
        RuntimeException exc = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                inboundHandler.checkException();
            }
        });
        assertEquals(testExc, exc);
    }
}
