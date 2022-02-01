/*
 * Copyright 2021 The Netty Project
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
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Promise;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import static io.netty.incubator.codec.http3.Http3TestUtils.newHeadersFrameWithPseudoHeaders;
import static java.util.function.UnaryOperator.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class Http3ServerPushStreamManagerTest {

    private EmbeddedQuicChannel channel;
    private Http3ServerPushStreamManager manager;
    private Http3ServerConnectionHandler connectionHandler;
    private ChannelHandlerContext controlStreamHandlerCtx;
    private EmbeddedQuicStreamChannel localControlStream;

    @BeforeEach
    public void setUp() throws Exception {
        connectionHandler = new Http3ServerConnectionHandler(new Http3RequestStreamInboundHandler() {
            @Override
            protected void channelRead(ChannelHandlerContext ctx, Http3HeadersFrame frame, boolean isLast) {
                ReferenceCountUtil.release(frame);
            }

            @Override
            protected void channelRead(ChannelHandlerContext ctx, Http3DataFrame frame, boolean isLast) {
                ReferenceCountUtil.release(frame);
            }
        }, null, null, null, true);
        channel = new EmbeddedQuicChannel(true, connectionHandler);
        localControlStream = (EmbeddedQuicStreamChannel) Http3.getLocalControlStream(channel);
        assertNotNull(localControlStream);
        assertTrue(localControlStream.releaseOutbound()); // settings

        controlStreamHandlerCtx = mock(ChannelHandlerContext.class);
        when(controlStreamHandlerCtx.channel()).thenReturn(localControlStream);
        connectionHandler.localControlStreamHandler.channelRead(controlStreamHandlerCtx,
                new DefaultHttp3SettingsFrame());
        manager = new Http3ServerPushStreamManager(channel);
    }

    @AfterEach
    public void tearDown() {
        assertFalse(localControlStream.finish());
        assertFalse(channel.finish());
    }

    @Test
    public void pushAllowed() throws Exception {
        assertFalse(manager.isPushAllowed());
        sendMaxPushId(1);
        assertTrue(manager.isPushAllowed());
    }

    @Test
    public void reserveWhenPushNotAllowed() {
        assertThrows(IllegalStateException.class, () -> manager.reserveNextPushId());
    }

    @Test
    public void reserveWhenPushAllowed() throws Exception {
        sendMaxPushId(2);
        assertEquals(0, manager.reserveNextPushId());
    }

    @Test
    public void reservesAfterRefreshMaxId() throws Exception {
        sendMaxPushId(0);
        assertEquals(0, manager.reserveNextPushId());
        assertFalse(manager.isPushAllowed());
        sendMaxPushId(1);
        assertEquals(1, manager.reserveNextPushId());
    }

    @Test
    public void pushStreamNoHandler() throws Exception {
        pushStreamCreateAndClose(pushId -> newPushStream(null, pushId));
    }

    @Test
    public void pushStreamWithHandler() throws Exception {
        final PushStreamListener pushStreamHandler = new PushStreamListener();
        pushStreamCreateAndClose(pushId -> newPushStream(pushStreamHandler, pushId));
        assertEquals(1, pushStreamHandler.framesWritten.size());
        assertTrue(pushStreamHandler.framesWritten.get(0) instanceof Http3HeadersFrame);
    }

    @Test
    public void pushStreamWithBootstrapNoHandler() throws Exception {
        pushStreamWithBootstrapCreateAndClose(null);
    }

    @Test
    public void pushStreamWithBootstrapWithHandler() throws Exception {
        final PushStreamListener pushStreamHandler = new PushStreamListener();
        pushStreamWithBootstrapCreateAndClose(pushStreamHandler);
        assertEquals(1, pushStreamHandler.framesWritten.size());
        assertTrue(pushStreamHandler.framesWritten.get(0) instanceof Http3HeadersFrame);
    }

    private void pushStreamWithBootstrapCreateAndClose(ChannelHandler pushStreamHandler) throws Exception {
        pushStreamCreateAndClose(pushId -> newPushStreamWithBootstrap(pushStreamHandler, pushId));
    }

    private void pushStreamCreateAndClose(PushStreamFactory pushStreamFactory) throws Exception {
        sendMaxPushId(1);
        final long pushId = manager.reserveNextPushId();
        final EmbeddedQuicStreamChannel pushStream = pushStreamFactory.createPushStream(pushId);

        final DefaultHttp3HeadersFrame headerFrame = newHeadersFrameWithPseudoHeaders();
        assertTrue(pushStream.writeOutbound(headerFrame));
        final ByteBuf encodedHeaders = pushStream.readOutbound();
        assertNotNull(encodedHeaders);
        encodedHeaders.release();

        final ChannelInboundHandler controlStreamListener = manager.controlStreamListener();
        controlStreamListener.channelRead(controlStreamHandlerCtx, new DefaultHttp3CancelPushFrame(pushId));
        assertFalse(pushStream.isActive());
    }

    private EmbeddedQuicStreamChannel newPushStream(ChannelHandler pushStreamHandler, long pushId) throws Exception {
        return newPushStream(() -> (EmbeddedQuicStreamChannel) manager.newPushStream(pushId, pushStreamHandler).get());
    }

    private EmbeddedQuicStreamChannel newPushStreamWithBootstrap(ChannelHandler pushStreamHandler, long pushId)
            throws Exception {
        return newPushStream(() -> {
            final Promise<QuicStreamChannel> promise = channel.eventLoop().newPromise();
            manager.newPushStream(pushId, pushStreamHandler, identity(), promise);
            return (EmbeddedQuicStreamChannel) promise.get();
        });
    }

    private EmbeddedQuicStreamChannel newPushStream(Callable<EmbeddedQuicStreamChannel> pushStreamFactory)
            throws Exception {
        final EmbeddedQuicStreamChannel pushStream = pushStreamFactory.call();
        assertTrue(pushStream.isActive());
        pushStream.flushOutbound(); // flush the stream header
        ByteBuf streamHeader = pushStream.readOutbound();
        assertNotNull(streamHeader);
        streamHeader.release();
        return pushStream;
    }

    private void sendMaxPushId(int maxPushId) throws QpackException {
        final DefaultHttp3MaxPushIdFrame maxPushIdFrame = new DefaultHttp3MaxPushIdFrame(maxPushId);
        connectionHandler.localControlStreamHandler.channelRead(controlStreamHandlerCtx, maxPushIdFrame);
        assertTrue(channel.isActive());
    }

    private static class PushStreamListener extends ChannelOutboundHandlerAdapter {
        final List<Http3PushStreamFrame> framesWritten = new ArrayList<>();

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            if (msg instanceof Http3PushStreamFrame) {
                framesWritten.add((Http3PushStreamFrame) msg);
            }
            super.write(ctx, msg, promise);
        }
    }

    @FunctionalInterface
    private interface PushStreamFactory {
        EmbeddedQuicStreamChannel createPushStream(long pushId) throws Exception;
    }
}
