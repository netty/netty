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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.DefaultChannelPipeline;
import io.netty.channel.DefaultChannelPromise;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.incubator.codec.quic.QuicStreamType;
import io.netty.util.AttributeMap;
import io.netty.util.DefaultAttributeMap;
import io.netty.util.concurrent.ImmediateEventExecutor;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public abstract class AbtractHttp3ConnectionHandlerTest {

    protected abstract Http3ConnectionHandler newConnectionHandler();

    protected abstract void assertBidirectionalStreamHandled(QuicChannel channel, QuicStreamChannel streamChannel);

    @Test
    public void testOpenLocalControlStream() throws Exception {
        QuicChannel quicChannel = mock(QuicChannel.class);
        QuicStreamChannel localControlStreamChannel = mock(QuicStreamChannel.class);
        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);

        AttributeMap attributeMap = new DefaultAttributeMap();
        when(quicChannel.attr(any())).then(a -> attributeMap.attr(a.getArgument(0)));
        when(quicChannel.createStream(any(QuicStreamType.class), any(ChannelHandler.class)))
                .thenReturn(ImmediateEventExecutor.INSTANCE.newSucceededFuture(localControlStreamChannel));

        when(ctx.channel()).thenReturn(quicChannel);
        Http3ConnectionHandler handler = newConnectionHandler();
        handler.handlerAdded(ctx);
        handler.channelRegistered(ctx);
        handler.channelActive(ctx);

        assertEquals(localControlStreamChannel, Http3.getLocalControlStream(quicChannel));

        handler.channelInactive(ctx);
        handler.channelUnregistered(ctx);
        handler.handlerRemoved(ctx);
    }

    @Test
    public void testBidirectionalStream() throws Exception {
        QuicChannel quicChannel = mock(QuicChannel.class);
        QuicStreamChannel localControlStreamChannel = mock(QuicStreamChannel.class);
        QuicStreamChannel bidirectionalStream = mock(QuicStreamChannel.class);

        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);

        AttributeMap attributeMap = new DefaultAttributeMap();
        when(quicChannel.attr(any())).then(a -> attributeMap.attr(a.getArgument(0)));
        when(quicChannel.createStream(any(QuicStreamType.class), any(ChannelHandler.class)))
                .thenReturn(ImmediateEventExecutor.INSTANCE.newSucceededFuture(localControlStreamChannel));
        when(quicChannel.close(anyBoolean(), anyInt(),
                any(ByteBuf.class))).thenReturn(new DefaultChannelPromise(quicChannel));
        when(quicChannel.alloc()).thenReturn(UnpooledByteBufAllocator.DEFAULT);

        when(ctx.channel()).thenReturn(quicChannel);

        when(bidirectionalStream.type()).thenReturn(QuicStreamType.BIDIRECTIONAL);
        when(bidirectionalStream.parent()).thenReturn(quicChannel);

        ChannelPipeline pipeline = new DefaultChannelPipeline(bidirectionalStream) { };
        when(bidirectionalStream.pipeline()).thenReturn(pipeline);

        Http3ConnectionHandler handler = newConnectionHandler();
        handler.handlerAdded(ctx);
        handler.channelRegistered(ctx);
        handler.channelActive(ctx);

        handler.channelRead(ctx, bidirectionalStream);

        assertBidirectionalStreamHandled(quicChannel, bidirectionalStream);
        handler.channelInactive(ctx);
        handler.channelUnregistered(ctx);
        handler.handlerRemoved(ctx);
    }

    @Test
    public void testUnidirectionalStream() throws Exception {
        QuicChannel quicChannel = mock(QuicChannel.class);
        QuicStreamChannel localControlStream = mock(QuicStreamChannel.class);
        QuicStreamChannel unidirectionalStream = mock(QuicStreamChannel.class);

        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);

        AttributeMap attributeMap = new DefaultAttributeMap();
        when(quicChannel.attr(any())).then(a -> attributeMap.attr(a.getArgument(0)));
        when(quicChannel.createStream(any(QuicStreamType.class), any(ChannelHandler.class)))
                .thenReturn(ImmediateEventExecutor.INSTANCE.newSucceededFuture(localControlStream));
        when(quicChannel.close(anyBoolean(), anyInt(),
                any(ByteBuf.class))).thenReturn(new DefaultChannelPromise(quicChannel));
        when(quicChannel.alloc()).thenReturn(UnpooledByteBufAllocator.DEFAULT);

        when(ctx.channel()).thenReturn(quicChannel);

        ChannelPipeline pipeline = new DefaultChannelPipeline(unidirectionalStream) { };
        when(unidirectionalStream.pipeline()).thenReturn(pipeline);
        when(unidirectionalStream.type()).thenReturn(QuicStreamType.UNIDIRECTIONAL);

        Http3ConnectionHandler handler = newConnectionHandler();
        handler.handlerAdded(ctx);
        handler.channelRegistered(ctx);
        handler.channelActive(ctx);

        handler.channelRead(ctx, unidirectionalStream);

        assertNotNull(pipeline.get(Http3UnidirectionalStreamInboundHandler.class));

        handler.channelInactive(ctx);
        handler.channelUnregistered(ctx);
        handler.handlerRemoved(ctx);
    }
}
