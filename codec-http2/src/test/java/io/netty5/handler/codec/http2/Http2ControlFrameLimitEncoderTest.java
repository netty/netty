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

package io.netty5.handler.codec.http2;

import io.netty5.buffer.ByteBuf;
import io.netty5.buffer.UnpooledByteBufAllocator;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelConfig;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelMetadata;
import io.netty5.channel.DefaultMessageSizeEstimator;
import io.netty5.util.ReferenceCountUtil;
import io.netty5.util.concurrent.EventExecutor;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.ImmediateEventExecutor;
import io.netty5.util.concurrent.Promise;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;

import java.util.ArrayDeque;
import java.util.Queue;

import static io.netty5.handler.codec.http2.Http2CodecUtil.DEFAULT_MAX_FRAME_SIZE;
import static io.netty5.handler.codec.http2.Http2Error.CANCEL;
import static io.netty5.handler.codec.http2.Http2Error.ENHANCE_YOUR_CALM;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link Http2ControlFrameLimitEncoder}.
 */
public class Http2ControlFrameLimitEncoderTest {

    private Http2ControlFrameLimitEncoder encoder;

    @Mock
    private Http2FrameWriter writer;

    @Mock
    private ChannelHandlerContext ctx;

    @Mock
    private Channel channel;

    @Mock
    private Channel.Unsafe unsafe;

    @Mock
    private ChannelConfig config;

    @Mock
    private EventExecutor executor;

    private int numWrites;

    private final Queue<Promise<Void>> goAwayPromises = new ArrayDeque<Promise<Void>>();

    /**
     * Init fields and do mocking.
     */
    @BeforeEach
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);

        numWrites = 0;

        Http2FrameWriter.Configuration configuration = mock(Http2FrameWriter.Configuration.class);
        Http2FrameSizePolicy frameSizePolicy = mock(Http2FrameSizePolicy.class);
        when(writer.configuration()).thenReturn(configuration);
        when(configuration.frameSizePolicy()).thenReturn(frameSizePolicy);
        when(frameSizePolicy.maxFrameSize()).thenReturn(DEFAULT_MAX_FRAME_SIZE);

        when(writer.writeRstStream(eq(ctx), anyInt(), anyLong()))
                .thenAnswer((Answer<Future<Void>>) invocationOnMock -> handlePromise().asFuture());
        when(writer.writeSettingsAck(any(ChannelHandlerContext.class)))
                .thenAnswer((Answer<Future<Void>>) invocationOnMock -> handlePromise().asFuture());
        when(writer.writePing(any(ChannelHandlerContext.class), anyBoolean(), anyLong()))
                .thenAnswer((Answer<Future<Void>>) invocationOnMock -> {
                    Promise<Void> promise = handlePromise();
                    if (invocationOnMock.getArgument(1) == Boolean.FALSE) {
                        promise.trySuccess(null);
                    }
                    return promise.asFuture();
                });
        when(writer.writeGoAway(any(ChannelHandlerContext.class), anyInt(), anyLong(), any(ByteBuf.class)))
                .thenAnswer((Answer<Future<Void>>) invocationOnMock -> {
                    ReferenceCountUtil.release(invocationOnMock.getArgument(3));
                    Promise<Void> promise =  ImmediateEventExecutor.INSTANCE.newPromise();
                    goAwayPromises.offer(promise);
                    return promise.asFuture();
                });
        Http2Connection connection = new DefaultHttp2Connection(false);
        connection.remote().flowController(new DefaultHttp2RemoteFlowController(connection));
        connection.local().flowController(new DefaultHttp2LocalFlowController(connection).frameWriter(writer));

        DefaultHttp2ConnectionEncoder defaultEncoder =
                new DefaultHttp2ConnectionEncoder(connection, writer);
        encoder = new Http2ControlFrameLimitEncoder(defaultEncoder, 2);
        DefaultHttp2ConnectionDecoder decoder =
                new DefaultHttp2ConnectionDecoder(connection, encoder, mock(Http2FrameReader.class));
        Http2ConnectionHandler handler = new Http2ConnectionHandlerBuilder()
                .frameListener(mock(Http2FrameListener.class))
                .codec(decoder, encoder).build();

        // Set LifeCycleManager on encoder and decoder
        when(ctx.channel()).thenReturn(channel);
        when(ctx.alloc()).thenReturn(UnpooledByteBufAllocator.DEFAULT);
        when(channel.alloc()).thenReturn(UnpooledByteBufAllocator.DEFAULT);
        when(executor.inEventLoop()).thenReturn(true);
        doAnswer((Answer<Promise<Void>>) invocation -> newPromise()).when(ctx).newPromise();
        doAnswer((Answer<Future<Void>>) invocation ->
                ImmediateEventExecutor.INSTANCE.newFailedFuture(invocation.getArgument(0)))
                .when(ctx).newFailedFuture(any(Throwable.class));

        when(ctx.executor()).thenReturn(executor);
        when(ctx.close()).thenReturn(ImmediateEventExecutor.INSTANCE.newSucceededFuture(null));
        when(channel.isActive()).thenReturn(false);
        when(channel.config()).thenReturn(config);
        when(channel.isWritable()).thenReturn(true);
        when(channel.bytesBeforeUnwritable()).thenReturn(Long.MAX_VALUE);
        when(config.getWriteBufferHighWaterMark()).thenReturn(Integer.MAX_VALUE);
        when(config.getMessageSizeEstimator()).thenReturn(DefaultMessageSizeEstimator.DEFAULT);
        ChannelMetadata metadata = new ChannelMetadata(false, 16);
        when(channel.metadata()).thenReturn(metadata);
        when(channel.unsafe()).thenReturn(unsafe);
        handler.handlerAdded(ctx);
    }

    private Promise<Void> handlePromise() {
        Promise<Void> p =  ImmediateEventExecutor.INSTANCE.newPromise();
        if (++numWrites == 2) {
            p.setSuccess(null);
        }
        return p;
    }

    @AfterEach
    public void tearDown() {
        // Close and release any buffered frames.
        encoder.close();

        // Notify all goAway Promise instances now as these will also release the retained ByteBuf for the
        // debugData.
        for (;;) {
            Promise<Void> promise = goAwayPromises.poll();
            if (promise == null) {
                break;
            }
            promise.setSuccess(null);
        }
    }

    @Test
    public void testLimitSettingsAck() {
        assertFalse(encoder.writeSettingsAck(ctx).isDone());
        // The second write is always marked as success by our mock, which means it will also not be queued and so
        // not count to the number of queued frames.
        assertTrue(encoder.writeSettingsAck(ctx).isSuccess());
        assertFalse(encoder.writeSettingsAck(ctx).isDone());

        verifyFlushAndClose(0, false);

        assertFalse(encoder.writeSettingsAck(ctx).isDone());
        assertFalse(encoder.writeSettingsAck(ctx).isDone());

        verifyFlushAndClose(1, true);
    }

    @Test
    public void testLimitPingAck() {
        assertFalse(encoder.writePing(ctx, true, 8).isDone());
        // The second write is always marked as success by our mock, which means it will also not be queued and so
        // not count to the number of queued frames.
        assertTrue(encoder.writePing(ctx, true, 8).isSuccess());
        assertFalse(encoder.writePing(ctx, true, 8).isDone());

        verifyFlushAndClose(0, false);

        assertFalse(encoder.writePing(ctx, true, 8).isDone());
        assertFalse(encoder.writePing(ctx, true, 8).isDone());

        verifyFlushAndClose(1, true);
    }

    @Test
    public void testNotLimitPing() {
        assertTrue(encoder.writePing(ctx, false, 8).isSuccess());
        assertTrue(encoder.writePing(ctx, false, 8).isSuccess());
        assertTrue(encoder.writePing(ctx, false, 8).isSuccess());
        assertTrue(encoder.writePing(ctx, false, 8).isSuccess());

        verifyFlushAndClose(0, false);
    }

    @Test
    public void testLimitRst() {
        assertFalse(encoder.writeRstStream(ctx, 1, CANCEL.code()).isDone());
        // The second write is always marked as success by our mock, which means it will also not be queued and so
        // not count to the number of queued frames.
        assertTrue(encoder.writeRstStream(ctx, 1, CANCEL.code()).isSuccess());
        assertFalse(encoder.writeRstStream(ctx, 1, CANCEL.code()).isDone());

        verifyFlushAndClose(0, false);

        assertFalse(encoder.writeRstStream(ctx, 1, CANCEL.code()).isDone());
        assertFalse(encoder.writeRstStream(ctx, 1, CANCEL.code()).isDone());

        verifyFlushAndClose(1, true);
    }

    @Test
    public void testLimit() {
        assertFalse(encoder.writeRstStream(ctx, 1, CANCEL.code()).isDone());
        // The second write is always marked as success by our mock, which means it will also not be queued and so
        // not count to the number of queued frames.
        assertTrue(encoder.writePing(ctx, false, 8).isSuccess());
        assertFalse(encoder.writePing(ctx, true, 8).isSuccess());

        verifyFlushAndClose(0, false);

        assertFalse(encoder.writeSettingsAck(ctx).isDone());
        assertFalse(encoder.writeRstStream(ctx, 1, CANCEL.code()).isDone());
        assertFalse(encoder.writePing(ctx, true, 8).isSuccess());

        verifyFlushAndClose(1, true);
    }

    private void verifyFlushAndClose(int invocations, boolean failed) {
        verify(ctx, atLeast(invocations)).flush();
        verify(ctx, times(invocations)).close();
        if (failed) {
            verify(writer, times(1)).writeGoAway(eq(ctx), eq(Integer.MAX_VALUE), eq(ENHANCE_YOUR_CALM.code()),
                    any(ByteBuf.class));
        }
    }

    private static Promise<Void> newPromise() {
        return ImmediateEventExecutor.INSTANCE.newPromise();
    }
}
