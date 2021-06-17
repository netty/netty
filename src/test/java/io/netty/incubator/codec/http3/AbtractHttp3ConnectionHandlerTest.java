/*
 * Copyright 2020 The Netty Project
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
package io.netty.incubator.codec.http3;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.incubator.codec.quic.QuicStreamType;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public abstract class AbtractHttp3ConnectionHandlerTest {

    private final boolean server;

    protected abstract Http3ConnectionHandler newConnectionHandler();

    protected abstract void assertBidirectionalStreamHandled(EmbeddedQuicChannel channel,
                                                             QuicStreamChannel streamChannel);

    public AbtractHttp3ConnectionHandlerTest(boolean server) {
        this.server = server;
    }

    @Test
    public void testOpenLocalControlStream() throws Exception {
        EmbeddedQuicChannel quicChannel = new EmbeddedQuicChannel(server, new ChannelDuplexHandler());
        ChannelHandlerContext ctx = quicChannel.pipeline().firstContext();

        Http3ConnectionHandler handler = newConnectionHandler();
        handler.handlerAdded(ctx);
        handler.channelRegistered(ctx);
        handler.channelActive(ctx);

        final EmbeddedQuicStreamChannel localControlStream = quicChannel.localControlStream();
        assertNotNull(localControlStream);

        assertNotNull(Http3.getLocalControlStream(quicChannel));

        handler.channelInactive(ctx);
        handler.channelUnregistered(ctx);
        handler.handlerRemoved(ctx);

        assertTrue(localControlStream.finishAndReleaseAll());
    }

    @Test
    public void testBidirectionalStream() throws Exception {
        EmbeddedQuicChannel quicChannel = new EmbeddedQuicChannel(server, new ChannelDuplexHandler());
        final EmbeddedQuicStreamChannel bidirectionalStream =
                (EmbeddedQuicStreamChannel) quicChannel.createStream(QuicStreamType.BIDIRECTIONAL,
                        new ChannelDuplexHandler()).get();
        ChannelHandlerContext ctx = quicChannel.pipeline().firstContext();

        Http3ConnectionHandler handler = newConnectionHandler();
        handler.handlerAdded(ctx);
        handler.channelRegistered(ctx);
        handler.channelActive(ctx);

        final EmbeddedQuicStreamChannel localControlStream = quicChannel.localControlStream();
        assertNotNull(localControlStream);

        handler.channelRead(ctx, bidirectionalStream);

        assertBidirectionalStreamHandled(quicChannel, bidirectionalStream);
        handler.channelInactive(ctx);
        handler.channelUnregistered(ctx);
        handler.handlerRemoved(ctx);

        assertTrue(localControlStream.finishAndReleaseAll());
    }

    @Test
    public void testUnidirectionalStream() throws Exception {
        EmbeddedQuicChannel quicChannel = new EmbeddedQuicChannel(server, new ChannelDuplexHandler());
        final QuicStreamChannel unidirectionalStream =
                quicChannel.createStream(QuicStreamType.UNIDIRECTIONAL, new ChannelDuplexHandler()).get();
        ChannelHandlerContext ctx = quicChannel.pipeline().firstContext();

        Http3ConnectionHandler handler = newConnectionHandler();
        handler.handlerAdded(ctx);
        handler.channelRegistered(ctx);
        handler.channelActive(ctx);

        final EmbeddedQuicStreamChannel localControlStream = quicChannel.localControlStream();
        assertNotNull(localControlStream);

        handler.channelRead(ctx, unidirectionalStream);

        assertNotNull(unidirectionalStream.pipeline().get(Http3UnidirectionalStreamInboundHandler.class));

        handler.channelInactive(ctx);
        handler.channelUnregistered(ctx);
        handler.handlerRemoved(ctx);

        assertTrue(localControlStream.finishAndReleaseAll());
    }
}
