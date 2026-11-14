/*
 * Copyright 2014 The Netty Project
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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelPromise;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http2.Http2CodecUtil.Http2ConnectState;
import io.netty.handler.codec.http2.Http2Stream.State;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.EventExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;

import static io.netty.buffer.Unpooled.EMPTY_BUFFER;
import static io.netty.handler.codec.http2.Http2CodecUtil.CONNECT_TUNNEL_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class Http2ConnectTunnelEnforcementTest {
    private static final int STREAM_ID = 3;
    private static final int PUSH_STREAM_ID = 4;

    private Http2ConnectionDecoder decoder;
    private Http2ConnectionEncoder encoder;
    private ChannelPromise promise;
    private ArgumentCaptor<Http2RemoteFlowController.FlowControlled> payloadCaptor;

    @Mock
    private ChannelHandlerContext ctx;

    @Mock
    private Channel channel;

    @Mock
    private ChannelFuture future;

    @Mock
    private Http2FrameListener listener;

    @Mock
    private Http2FrameReader reader;

    @Mock
    private Http2FrameWriter writer;

    @Mock
    private Http2LifecycleManager lifecycleManager;

    @Mock
    private Http2FrameWriter.Configuration writerConfig;

    @Mock
    private Http2FrameSizePolicy frameSizePolicy;

    @Mock
    private Http2RemoteFlowController remoteFlow;

    @Mock
    private EventExecutor executor;

    private Http2Connection connection;

    @BeforeEach
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);

        promise = new DefaultChannelPromise(channel);
        connection = new DefaultHttp2Connection(true);

        when(writer.configuration()).thenReturn(writerConfig);
        when(writerConfig.frameSizePolicy()).thenReturn(frameSizePolicy);
        when(frameSizePolicy.maxFrameSize()).thenReturn(16384);

        when(channel.isActive()).thenReturn(true);
        when(ctx.alloc()).thenReturn(UnpooledByteBufAllocator.DEFAULT);
        when(ctx.channel()).thenReturn(channel);
        when(ctx.executor()).thenReturn(executor);
        when(executor.inEventLoop()).thenReturn(true);

        when(ctx.newSucceededFuture()).thenReturn(future);
        when(ctx.newPromise()).thenReturn(promise);
        when(ctx.write(any())).thenReturn(future);
        when(future.isSuccess()).thenReturn(true);
        when(future.isSuccess()).thenReturn(true);
        when(future.cause()).thenReturn(null);

        doAnswer(invocation -> {
            ChannelFutureListener listener = invocation.getArgument(0);
            listener.operationComplete(future);
            return future;
        }).when(future).addListener(any(ChannelFutureListener.class));

        payloadCaptor = ArgumentCaptor.forClass(Http2RemoteFlowController.FlowControlled.class);

        doAnswer(invocation -> {
            Http2RemoteFlowController.FlowControlled payload = invocation.getArgument(1);

            // Simulate the remote flow controller allowing the DATA to be written.
            payload.write(ctx, Integer.MAX_VALUE);
            payload.writeComplete();

            return null;
        }).when(remoteFlow).addFlowControlled(any(Http2Stream.class), payloadCaptor.capture());

        encoder = new DefaultHttp2ConnectionEncoder(connection, writer);
        encoder.lifecycleManager(lifecycleManager);

        decoder = new DefaultHttp2ConnectionDecoder(connection, encoder, reader);
        decoder.lifecycleManager(lifecycleManager);
        decoder.frameListener(listener);

        connection.local().flowController().channelHandlerContext(ctx);
        connection.remote().flowController(remoteFlow);
    }

    private Http2FrameListener decode() throws Exception {
        ArgumentCaptor<Http2FrameListener> internalListener = ArgumentCaptor.forClass(Http2FrameListener.class);
        doNothing().when(reader).readFrame(eq(ctx), any(ByteBuf.class), internalListener.capture());
        decoder.decodeFrame(ctx, EMPTY_BUFFER, Collections.emptyList());
        return internalListener.getValue();
    }

    @Test
    public void subsequentHeadersOnEstablishedConnectStreamShouldFail() throws Exception {
        Http2FrameListener dec = decode();
        final Http2Stream stream = establishConnectTunnel(dec);
        // A subsequent HEADERS frame on an established CONNECT tunnel is illegal.
        Http2Headers laterHeaders = new DefaultHttp2Headers().add("x-after-connect", "123456-value");
        assertThrows(Http2Exception.class,
                     () -> dec.onHeadersRead(ctx, STREAM_ID, laterHeaders, 0, (short) 16, false, 0, true));
    }

    @Test
    public void pushPromiseOnEstablishedConnectStreamShouldFail() throws Exception {
        Http2FrameListener dec = decode();
        final Http2Stream stream = establishConnectTunnel(dec);
        assertThrows(Http2Exception.class, () ->
                dec.onPushPromiseRead(ctx, STREAM_ID, PUSH_STREAM_ID,
                                      new DefaultHttp2Headers().method(
                                                                       HttpMethod.GET.asciiName())
                                                               .scheme("https")
                                                               .authority(
                                                                       "example.org")
                                                               .path("/resource"), 0));
    }

    @Test
    public void dataFromClientOnEstablishedConnectStreamShouldSucceed() throws Exception {
        Http2FrameListener dec = decode();
        final Http2Stream stream = establishConnectTunnel(dec);
        // Simulate application consuming all received DATA bytes.
        when(listener.onDataRead(eq(ctx), eq(STREAM_ID), any(ByteBuf.class), eq(0), eq(false))).thenAnswer(
                invocation -> ((ByteBuf) invocation.getArgument(2)).readableBytes());
        // DATA is permitted on an established CONNECT tunnel.
        ByteBuf data = Unpooled.copiedBuffer("Data record", CharsetUtil.UTF_8);
        try {
            int processedBytes = dec.onDataRead(ctx, STREAM_ID, data, 0, false);
            assertEquals(data.readableBytes(), processedBytes);
            assertEquals(State.OPEN, stream.state());
        } finally {
            data.release();
        }
    }

    @Test
    public void dataFromServerOnEstablishedConnectStreamShouldSucceed() throws Exception {
        Http2FrameListener dec = decode();
        establishConnectTunnel(dec);
        // DATA frames are allowed on an established CONNECT tunnel.
        ByteBuf data = Unpooled.copiedBuffer("Data record", CharsetUtil.UTF_8);
        try {
            encoder.writeData(ctx, STREAM_ID, data, 0, false, promise);
            verify(writer).writeData(eq(ctx), eq(STREAM_ID), eq(data), eq(0), eq(false), eq(promise));
        } finally {
            data.release();
        }
    }

    @Test
    public void priorityFromClientOnEstablishedConnectStreamShouldSucceed() throws Exception {
        Http2FrameListener dec = decode();
        final Http2Stream stream = establishConnectTunnel(dec);
        // PRIORITY frames are permitted on established CONNECT tunnels.
        dec.onPriorityRead(ctx, STREAM_ID, 0, (short) 16, false);
        //The CONNECT stream must remain open
        assertEquals(State.OPEN, stream.state());
        verify(listener).onPriorityRead(ctx, STREAM_ID, 0, (short) 16, false);
    }

    @Test
    public void priorityFromServerOnEstablishedConnectStreamShouldSucceed() throws Exception {
        Http2FrameListener dec = decode();
        final Http2Stream stream = establishConnectTunnel(dec);

        when(writer.writePriority(eq(ctx), eq(STREAM_ID), eq(0), eq((short) 16), eq(false), eq(promise))).thenReturn(
                future);
        // PRIORITY frames are permitted on established CONNECT tunnels.
        encoder.writePriority(ctx, STREAM_ID, 0, (short) 16, false, promise);
        verify(writer).writePriority(eq(ctx), eq(STREAM_ID), eq(0), eq((short) 16), eq(false), eq(promise));
    }

    @Test
    public void windowUpdateFromClientOnEstablishedConnectStreamShouldSucceed() throws Exception {
        Http2FrameListener dec = decode();
        final Http2Stream stream = establishConnectTunnel(dec);
        // WINDOW_UPDATE frames are permitted on established CONNECT tunnels.
        dec.onWindowUpdateRead(ctx, STREAM_ID, 16);
        //The CONNECT stream must remain open
        assertEquals(State.OPEN, stream.state());
        verify(listener).onWindowUpdateRead(ctx, STREAM_ID, 16);
    }

    // Stream management
    @Test
    public void rstStreamFromClientOnEstablishedConnectStreamShouldSucceed() throws Exception {
        Http2FrameListener dec = decode();
        final Http2Stream stream = establishConnectTunnel(dec);

        // RST_STREAM is permitted on established CONNECT tunnels.
        dec.onRstStreamRead(ctx, STREAM_ID, Http2Error.CANCEL.code());
        verify(listener).onRstStreamRead(ctx, STREAM_ID, Http2Error.CANCEL.code());
    }

    @Test
    public void rstStreamFromServerOnEstablishedConnectStreamShouldSucceed() throws Exception {
        Http2FrameListener dec = decode();
        final Http2Stream stream = establishConnectTunnel(dec);
        encoder.writeRstStream(ctx, STREAM_ID, Http2Error.CANCEL.code(), promise);
        verify(lifecycleManager).resetStream(
                ctx, STREAM_ID, Http2Error.CANCEL.code(), promise);
    }

    @Test
    public void unsuccessfulConnectResponseShouldNotEstablishTunnel() throws Exception {
        Http2FrameListener dec = decode();
        dec.onSettingsRead(ctx, new Http2Settings());

        Http2Headers connect = new DefaultHttp2Headers()
                .method(HttpMethod.CONNECT.asciiName())
                .authority("example.org:443");

        dec.onHeadersRead(ctx, STREAM_ID, connect, 0, (short) 16, false, 0, false);

        Http2Stream stream = connection.stream(STREAM_ID);

        Http2Headers response = new DefaultHttp2Headers().status("403");

        when(writer.writeHeaders(eq(ctx), eq(STREAM_ID), eq(response),
                                 eq(0), eq((short) 16), eq(false), eq(0), eq(false), eq(promise)))
                .thenReturn(future);

        encoder.writeHeaders(ctx, STREAM_ID, response, 0, (short) 16,
                             false, 0, false, promise);

        // Verify that the stream was not marked as an established tunnel.
        assertEquals(Http2ConnectState.PENDING,
                     stream.getProperty(CONNECT_TUNNEL_KEY));
    }

    private Http2Stream establishConnectTunnel(Http2FrameListener dec) throws Exception {
        dec.onSettingsRead(ctx, new Http2Settings());
        Http2Headers connect = new DefaultHttp2Headers()
                .method(HttpMethod.CONNECT.asciiName())
                .authority("example.org:443");
        dec.onHeadersRead(ctx, STREAM_ID, connect,
                          0, (short) 16, false, 0, false);
        Http2Stream stream = connection.stream(STREAM_ID);
        assertNotNull(stream);
        Http2Headers response = new DefaultHttp2Headers().status("200");
        when(writer.writeHeaders(eq(ctx), eq(STREAM_ID), eq(response),
                                 eq(0), eq((short) 16), eq(false), eq(0),
                                 eq(false), eq(promise))).thenReturn(future);
        assertEquals(Http2ConnectState.PENDING,
                     stream.getProperty(CONNECT_TUNNEL_KEY));
        System.out.println("isServer: " + connection.isServer());

        System.out.println("CONNECT state: " +
                           stream.getProperty(CONNECT_TUNNEL_KEY));

        System.out.println("Response status: " + response.status());
        encoder.writeHeaders(ctx, STREAM_ID, response,
                             0, (short) 16, false, 0, false, promise);
        assertEquals(State.OPEN, stream.state());
        assertEquals(Http2ConnectState.ESTABLISHED,
                     stream.getProperty(CONNECT_TUNNEL_KEY));
        return stream;
    }
}
