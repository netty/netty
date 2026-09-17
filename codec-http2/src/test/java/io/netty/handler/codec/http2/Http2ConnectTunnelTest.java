/*
 * Copyright 2026 The Netty Project
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

import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelPromise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.Collections;

import static io.netty.buffer.Unpooled.EMPTY_BUFFER;
import static io.netty.handler.codec.http2.Http2Error.PROTOCOL_ERROR;
import static io.netty.handler.codec.http2.Http2PromisedRequestVerifier.ALWAYS_VERIFY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * End-to-end test for the CONNECT tunnel enforcement required by
 * <a href="https://www.rfc-editor.org/rfc/rfc9113.html#section-8.5">RFC 9113, 8.5</a>: once an ordinary CONNECT
 * request has been answered with a successful (2xx) response, no further HEADERS frame is permitted on that
 * stream. Unlike {@link DefaultHttp2ConnectionDecoderTest}, this uses a real {@link DefaultHttp2Connection} and
 * {@link DefaultHttp2ConnectionEncoder} so that the state the decoder relies on -- tracked directly on
 * {@link DefaultHttp2Connection.DefaultStream}, set by the encoder when it writes the request/response and read
 * by the decoder via an {@code instanceof} check -- is actually exercised, rather than a mock standing in for it.
 */
public class Http2ConnectTunnelTest {
    private static final int STREAM_ID = 3;

    @Mock
    private ChannelHandlerContext ctx;
    @Mock
    private Channel channel;
    @Mock
    private Http2FrameWriter frameWriter;
    @Mock
    private Http2FrameWriter.Configuration writerConfig;
    @Mock
    private Http2HeadersEncoder.Configuration headersEncoderConfig;
    @Mock
    private Http2FrameSizePolicy frameSizePolicy;
    @Mock
    private Http2FrameReader frameReader;
    @Mock
    private Http2LifecycleManager lifecycleManager;
    @Mock
    private Http2FrameListener listener;

    private DefaultHttp2ConnectionEncoder encoder;
    private DefaultHttp2ConnectionDecoder decoder;

    @BeforeEach
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(channel.isActive()).thenReturn(true);
        when(ctx.channel()).thenReturn(channel);
        when(ctx.alloc()).thenReturn(UnpooledByteBufAllocator.DEFAULT);
        when(ctx.newPromise()).thenAnswer(new Answer<ChannelPromise>() {
            @Override
            public ChannelPromise answer(InvocationOnMock invocation) {
                return new DefaultChannelPromise(channel);
            }
        });
        when(ctx.newSucceededFuture()).thenAnswer(new Answer<ChannelFuture>() {
            @Override
            public ChannelFuture answer(InvocationOnMock invocation) {
                return new DefaultChannelPromise(channel).setSuccess();
            }
        });

        when(frameWriter.configuration()).thenReturn(writerConfig);
        when(writerConfig.headersConfiguration()).thenReturn(headersEncoderConfig);
        when(writerConfig.frameSizePolicy()).thenReturn(frameSizePolicy);
        when(frameSizePolicy.maxFrameSize()).thenReturn(Http2CodecUtil.DEFAULT_MAX_FRAME_SIZE);
        when(frameWriter.writeSettingsAck(eq(ctx), any(ChannelPromise.class))).thenAnswer(
                new Answer<ChannelFuture>() {
                    @Override
                    public ChannelFuture answer(InvocationOnMock invocation) {
                        return ((ChannelPromise) invocation.getArgument(1)).setSuccess();
                    }
                });
        when(frameWriter.writeHeaders(eq(ctx), anyInt(), any(Http2Headers.class), anyInt(), anyBoolean(),
                any(ChannelPromise.class))).thenAnswer(new Answer<ChannelFuture>() {
            @Override
            public ChannelFuture answer(InvocationOnMock invocation) {
                return ((ChannelPromise) invocation.getArgument(5)).setSuccess();
            }
        });

        Http2Connection connection = new DefaultHttp2Connection(true);

        encoder = new DefaultHttp2ConnectionEncoder(connection, frameWriter);
        encoder.lifecycleManager(lifecycleManager);

        decoder = new DefaultHttp2ConnectionDecoder(
                connection, encoder, frameReader, ALWAYS_VERIFY, true, true, true, true);
        decoder.lifecycleManager(lifecycleManager);
        decoder.frameListener(listener);
        doNothing().when(lifecycleManager).closeStreamRemote(any(Http2Stream.class), any(ChannelFuture.class));

        // Prime the decoder past the connection preface, as required before any other frame is accepted.
        decode().onSettingsRead(ctx, new Http2Settings());
    }

    /**
     * Calls {@link DefaultHttp2ConnectionDecoder#decodeFrame} and returns the captured internal listener, mirroring
     * the approach used by {@link DefaultHttp2ConnectionDecoderTest}.
     */
    private Http2FrameListener decode() throws Exception {
        ArgumentCaptor<Http2FrameListener> internalListener = ArgumentCaptor.forClass(Http2FrameListener.class);
        doNothing().when(frameReader).readFrame(eq(ctx), any(), internalListener.capture());
        decoder.decodeFrame(ctx, EMPTY_BUFFER, Collections.emptyList());
        return internalListener.getValue();
    }

    @Test
    public void headersAfterConnectTunnelEstablishedAreRejected() throws Exception {
        Http2FrameListener dec = decode();

        // The client's ordinary CONNECT request arrives.
        Http2Headers connectRequest = new DefaultHttp2Headers().method("CONNECT").authority("example.org:443");
        dec.onHeadersRead(ctx, STREAM_ID, connectRequest, 0, false);
        verify(listener).onHeadersRead(eq(ctx), eq(STREAM_ID), eq(connectRequest), eq(0),
                eq(Http2CodecUtil.DEFAULT_PRIORITY_WEIGHT), eq(false), eq(0), eq(false));

        // The (local) server accepts the tunnel with a successful response.
        Http2Headers response = new DefaultHttp2Headers().status("200");
        encoder.writeHeaders(ctx, STREAM_ID, response, 0, false, ctx.newPromise());

        // The tunnel is now established: any further HEADERS frame must be a stream error (RFC 9113, 8.5), not
        // silently accepted as trailers.
        final Http2Headers laterHeaders = new DefaultHttp2Headers().add("x-after-connect", "1");
        Http2Exception ex = assertThrows(Http2Exception.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                dec.onHeadersRead(ctx, STREAM_ID, laterHeaders, 0, true);
            }
        });
        assertEquals(PROTOCOL_ERROR, ex.error());
        verify(listener, never()).onHeadersRead(eq(ctx), eq(STREAM_ID), eq(laterHeaders), anyInt(),
                any(Short.class), anyBoolean(), anyInt(), anyBoolean());
    }

    @Test
    public void trailersAfterRejectedConnectAreStillAllowed() throws Exception {
        Http2FrameListener dec = decode();

        // The client's ordinary CONNECT request arrives.
        Http2Headers connectRequest = new DefaultHttp2Headers().method("CONNECT").authority("example.org:443");
        dec.onHeadersRead(ctx, STREAM_ID, connectRequest, 0, false);

        // The (local) server rejects the CONNECT request; no tunnel is established (RFC 9113, 8.5).
        Http2Headers response = new DefaultHttp2Headers().status("403");
        encoder.writeHeaders(ctx, STREAM_ID, response, 0, false, ctx.newPromise());

        // A normal trailing HEADERS frame following a non-2xx response must still be accepted.
        Http2Headers trailers = new DefaultHttp2Headers().add("x-trailer", "value");
        dec.onHeadersRead(ctx, STREAM_ID, trailers, 0, true);
        verify(listener).onHeadersRead(eq(ctx), eq(STREAM_ID), eq(trailers), eq(0),
                eq(Http2CodecUtil.DEFAULT_PRIORITY_WEIGHT), eq(false), eq(0), eq(true));
    }

    @Test
    public void headersAfterConnectTunnelEstablishedAreRejectedOnClientSide() throws Exception {
        // The inverse direction from headersAfterConnectTunnelEstablishedAreRejected: this endpoint is the
        // client, so it writes the ordinary CONNECT request itself (rather than decoding one) and decodes the
        // server's response, exercising the encoder -> decoder bridge for the request side end-to-end.
        Http2Connection clientConnection = new DefaultHttp2Connection(false);
        DefaultHttp2ConnectionEncoder clientEncoder = new DefaultHttp2ConnectionEncoder(clientConnection, frameWriter);
        clientEncoder.lifecycleManager(lifecycleManager);
        DefaultHttp2ConnectionDecoder clientDecoder = new DefaultHttp2ConnectionDecoder(
                clientConnection, clientEncoder, frameReader, ALWAYS_VERIFY, true, true, true, true);
        clientDecoder.lifecycleManager(lifecycleManager);
        clientDecoder.frameListener(listener);

        ArgumentCaptor<Http2FrameListener> internalListener = ArgumentCaptor.forClass(Http2FrameListener.class);
        doNothing().when(frameReader).readFrame(eq(ctx), any(), internalListener.capture());
        clientDecoder.decodeFrame(ctx, EMPTY_BUFFER, Collections.emptyList());
        Http2FrameListener dec = internalListener.getValue();
        dec.onSettingsRead(ctx, new Http2Settings());

        // The client writes its own ordinary CONNECT request.
        Http2Headers connectRequest = new DefaultHttp2Headers().method("CONNECT").authority("example.org:443");
        clientEncoder.writeHeaders(ctx, STREAM_ID, connectRequest, 0, false, ctx.newPromise());

        // The server's successful response is decoded.
        Http2Headers response = new DefaultHttp2Headers().status("200");
        dec.onHeadersRead(ctx, STREAM_ID, response, 0, false);
        verify(listener).onHeadersRead(eq(ctx), eq(STREAM_ID), eq(response), eq(0),
                eq(Http2CodecUtil.DEFAULT_PRIORITY_WEIGHT), eq(false), eq(0), eq(false));

        // The tunnel is now established: any further inbound HEADERS from the server must be a stream error.
        final Http2Headers laterHeaders = new DefaultHttp2Headers().add("x-after-connect", "1");
        Http2Exception ex = assertThrows(Http2Exception.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                dec.onHeadersRead(ctx, STREAM_ID, laterHeaders, 0, true);
            }
        });
        assertEquals(PROTOCOL_ERROR, ex.error());
        verify(listener, never()).onHeadersRead(eq(ctx), eq(STREAM_ID), eq(laterHeaders), anyInt(),
                any(Short.class), anyBoolean(), anyInt(), anyBoolean());
    }
}
