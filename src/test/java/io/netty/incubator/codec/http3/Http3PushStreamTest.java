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
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.util.ReferenceCountUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.ExecutionException;

import static io.netty.channel.ChannelFutureListener.CLOSE_ON_FAILURE;
import static io.netty.incubator.codec.http3.Http3ErrorCode.H3_ID_ERROR;
import static io.netty.incubator.codec.http3.Http3TestUtils.assertFrameEquals;
import static io.netty.incubator.codec.http3.Http3TestUtils.verifyClose;
import static io.netty.incubator.codec.quic.QuicStreamType.UNIDIRECTIONAL;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class Http3PushStreamTest {
    private Http3ServerConnectionHandler serverConnectionHandler;
    private EmbeddedQuicChannel serverChannel;
    private Http3ServerPushStreamManager pushStreamManager;
    private Http3ClientConnectionHandler clientConnectionHandler;
    private EmbeddedQuicChannel clientChannel;
    private int maxPushId;
    private ChannelHandlerContext serverControlStreamHandlerCtx;
    private EmbeddedQuicStreamChannel serverLocalControlStream;
    private EmbeddedQuicStreamChannel clientLocalControlStream;

    @Before
    public void setUp() throws Exception {
        serverConnectionHandler = new Http3ServerConnectionHandler(new ChannelDuplexHandler(), null, null, null, true);
        serverChannel = new EmbeddedQuicChannel(true, serverConnectionHandler);
        serverLocalControlStream = (EmbeddedQuicStreamChannel) Http3.getLocalControlStream(serverChannel);
        assertNotNull(serverLocalControlStream);
        serverControlStreamHandlerCtx = mock(ChannelHandlerContext.class);
        when(serverControlStreamHandlerCtx.channel()).thenReturn(serverLocalControlStream);

        serverConnectionHandler.localControlStreamHandler.channelRead(serverControlStreamHandlerCtx,
                new DefaultHttp3SettingsFrame());
        pushStreamManager = new Http3ServerPushStreamManager(serverChannel);

        clientConnectionHandler = new Http3ClientConnectionHandler(null, null, null, null, true);
        clientChannel = new EmbeddedQuicChannel(false, clientConnectionHandler);
        clientLocalControlStream = (EmbeddedQuicStreamChannel) Http3.getLocalControlStream(clientChannel);
        assertNotNull(clientLocalControlStream);

        assertTrue(serverLocalControlStream.releaseOutbound());
        assertTrue(clientLocalControlStream.releaseOutbound());
        maxPushId = 0; // allow 1 push
        sendMaxPushId(maxPushId);
    }

    private void sendMaxPushId(int maxPushId) throws QpackException {
        final DefaultHttp3MaxPushIdFrame maxPushIdFrame = new DefaultHttp3MaxPushIdFrame(maxPushId);
        serverConnectionHandler.localControlStreamHandler.channelRead(serverControlStreamHandlerCtx, maxPushIdFrame);
        assertTrue(serverChannel.isActive());

        clientLocalControlStream.writeAndFlush(maxPushIdFrame).addListener(CLOSE_ON_FAILURE);
        assertTrue(clientChannel.isActive());
        assertTrue(clientLocalControlStream.releaseOutbound());
    }

    @After
    public void tearDown() {
        assertFalse(serverLocalControlStream.finish());
        assertFalse(serverChannel.finish());
        assertFalse(clientLocalControlStream.finish());
        assertFalse(clientChannel.finish());
    }

    @Test
    public void headersData() throws Exception {
        testWriteAndReadFrames(Http3TestUtils.newHeadersFrameWithPseudoHeaders(), newDataFrame());
    }

    @Test
    public void headersDataTrailers() throws Exception {
        testWriteAndReadFrames(Http3TestUtils.newHeadersFrameWithPseudoHeaders(), newDataFrame(),
                new DefaultHttp3HeadersFrame());
    }

    @Test
    public void pushPromise() throws Exception {
        final EmbeddedQuicStreamChannel serverStream = newServerStream();
        readStreamHeader(serverStream).release();
        try {
            assertThrows(Http3Exception.class, () -> serverStream.writeOutbound(new DefaultHttp3PushPromiseFrame(1)));
        } finally {
            assertFalse(serverStream.finish());
        }
    }

    @Test
    public void invalidPushId() throws Exception {
        final EmbeddedQuicStreamChannel serverStream =
                (EmbeddedQuicStreamChannel) serverChannel.createStream(UNIDIRECTIONAL,
                        new Http3PushStreamServerInitializer(maxPushId + 1) {
                            @Override
                            protected void initPushStream(QuicStreamChannel ch) {
                                // noop
                            }
                        }).get();
        final EmbeddedQuicStreamChannel clientStream = newClientStreamUninitialized();
        try {
            final ByteBuf streamHeader = readStreamHeader(serverStream);
            assertFalse(clientStream.writeInbound(streamHeader));
            verifyClose(H3_ID_ERROR, clientChannel);
        } finally {
            assertFalse(serverStream.finish());
            assertFalse(clientStream.finish());
        }
    }

    @Test
    public void updateMaxPushId() throws Exception {
        testWriteAndReadFrames(Http3TestUtils.newHeadersFrameWithPseudoHeaders(), newDataFrame());
        assertFalse(pushStreamManager.isPushAllowed());

        sendMaxPushId(maxPushId + 1);
        testWriteAndReadFrames(Http3TestUtils.newHeadersFrameWithPseudoHeaders(), newDataFrame());
    }

    private void testWriteAndReadFrames(Http3RequestStreamFrame... frames) throws Exception {
        final EmbeddedQuicStreamChannel serverStream = newServerStream();
        final EmbeddedQuicStreamChannel clientStream = newClientStream(serverStream);
        try {
            for (Http3RequestStreamFrame frame : frames) {
                writeAndReadFrame(serverStream, clientStream, frame);
            }
        } finally {
            assertFalse(serverStream.finish());
            assertFalse(clientStream.finish());
        }
    }

    private static void writeAndReadFrame(EmbeddedQuicStreamChannel serverStream,
                                          EmbeddedQuicStreamChannel clientStream, Http3RequestStreamFrame frame) {
        ReferenceCountUtil.retain(frame); // retain so that we can compare later
        assertTrue(serverStream.writeOutbound(frame));
        final ByteBuf encodedFrame = serverStream.readOutbound();
        assertNotNull(encodedFrame);
        assertTrue(clientStream.writeInbound(encodedFrame));
        final Http3RequestStreamFrame readFrame = clientStream.readInbound();
        assertFrameEquals(frame, readFrame); // releases both the frames.

        assertTrue(serverStream.isActive());
        assertTrue(clientStream.isActive());
    }

    private DefaultHttp3DataFrame newDataFrame() {
        return new DefaultHttp3DataFrame(serverChannel.alloc().buffer(1).writeByte(1));
    }

    private EmbeddedQuicStreamChannel newServerStream() throws InterruptedException, ExecutionException {
        assertTrue(pushStreamManager.isPushAllowed());
        final long pushId = pushStreamManager.reserveNextPushId();
        return (EmbeddedQuicStreamChannel) pushStreamManager.newPushStream(pushId, null).get();
    }

    private EmbeddedQuicStreamChannel newClientStream(EmbeddedQuicStreamChannel serverStream) throws Exception {
        final EmbeddedQuicStreamChannel clientStream = newClientStreamUninitialized();
        ByteBuf streamHeader = readStreamHeader(serverStream);
        assertFalse(clientStream.writeInbound(streamHeader));
        assertTrue(clientChannel.isActive());
        return clientStream;
    }

    private EmbeddedQuicStreamChannel newClientStreamUninitialized() throws InterruptedException, ExecutionException {
        return (EmbeddedQuicStreamChannel) clientChannel.createStream(UNIDIRECTIONAL,
                new Http3UnidirectionalStreamInboundClientHandler(
                        (__, encodeState, decodeState) -> clientConnectionHandler.newCodec(encodeState, decodeState),
                        clientConnectionHandler.localControlStreamHandler,
                        clientConnectionHandler.remoteControlStreamHandler, null,
                        __ -> new Http3PushStreamClientInitializer() {
                            @Override
                            protected void initPushStream(QuicStreamChannel ch) {
                                // noop
                            }
                        }, ChannelDuplexHandler::new, ChannelDuplexHandler::new)).get();
    }

    private ByteBuf readStreamHeader(EmbeddedQuicStreamChannel serverStream) {
        serverStream.flushOutbound(); // flush the stream header
        ByteBuf streamHeader = serverStream.readOutbound();
        assertNotNull(streamHeader);
        return streamHeader;
    }
}
