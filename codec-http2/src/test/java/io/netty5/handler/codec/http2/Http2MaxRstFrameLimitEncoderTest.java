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

import io.netty5.buffer.Buffer;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.DefaultMessageSizeEstimator;
import io.netty5.channel.WriteBufferWaterMark;
import io.netty5.util.Resource;
import io.netty5.util.concurrent.EventExecutor;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.ImmediateEventExecutor;
import io.netty5.util.concurrent.MockTicker;
import io.netty5.util.concurrent.Promise;
import io.netty5.util.concurrent.Ticker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

import static io.netty5.buffer.DefaultBufferAllocators.onHeapAllocator;
import static io.netty5.handler.codec.http2.Http2CodecUtil.DEFAULT_MAX_FRAME_SIZE;
import static io.netty5.handler.codec.http2.Http2Error.CANCEL;
import static io.netty5.handler.codec.http2.Http2Error.ENHANCE_YOUR_CALM;
import static io.netty5.handler.codec.http2.Http2Error.NO_ERROR;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.any;
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
 * Tests for {@link Http2MaxRstFrameLimitEncoder}.
 */
public class Http2MaxRstFrameLimitEncoderTest {

    private Http2MaxRstFrameLimitEncoder encoder;

    @Mock
    private Http2FrameWriter writer;

    @Mock
    private ChannelHandlerContext ctx;

    @Mock
    private Channel channel;

    @Mock
    private EventExecutor executor;

    private final MockTicker ticker = Ticker.newMockTicker();
    private final Queue<Promise<Void>> goAwayPromises = new ArrayDeque<>();

    /**
     * Init fields and do mocking.
     */
    @BeforeEach
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);

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
        when(writer.writeGoAway(any(ChannelHandlerContext.class), anyInt(), anyLong(), any(Buffer.class)))
                .thenAnswer((Answer<Future<Void>>) invocationOnMock -> {
                    Resource.dispose(invocationOnMock.getArgument(3));
                    Promise<Void> promise =  io.netty5.util.concurrent.ImmediateEventExecutor.INSTANCE.newPromise();
                    goAwayPromises.offer(promise);
                    return promise.asFuture();
                });
        Http2Connection connection = new DefaultHttp2Connection(false);
        connection.remote().flowController(new DefaultHttp2RemoteFlowController(connection));
        connection.local().flowController(new DefaultHttp2LocalFlowController(connection).frameWriter(writer));

        DefaultHttp2ConnectionEncoder defaultEncoder =
                new DefaultHttp2ConnectionEncoder(connection, writer);
        encoder = new Http2MaxRstFrameLimitEncoder(defaultEncoder, 2, 1, ticker);
        DefaultHttp2ConnectionDecoder decoder =
                new DefaultHttp2ConnectionDecoder(connection, encoder, mock(Http2FrameReader.class));
        Http2ConnectionHandler handler = new Http2ConnectionHandlerBuilder()
                .frameListener(mock(Http2FrameListener.class))
                .codec(decoder, encoder).build();

        // Set LifeCycleManager on encoder and decoder
        when(ctx.channel()).thenReturn(channel);
        when(ctx.bufferAllocator()).thenReturn(onHeapAllocator());
        when(channel.bufferAllocator()).thenReturn(onHeapAllocator());
        when(executor.inEventLoop()).thenReturn(true);
        doAnswer((Answer<Promise<Void>>) invocation -> newPromise()).when(ctx).newPromise();
        doAnswer((Answer<Future<Void>>) invocation ->
                ImmediateEventExecutor.INSTANCE.newFailedFuture(invocation.getArgument(0)))
                .when(ctx).newFailedFuture(any(Throwable.class));

        when(ctx.executor()).thenReturn(executor);
        when(ctx.close()).thenReturn(ImmediateEventExecutor.INSTANCE.newSucceededFuture(null));
        when(channel.isActive()).thenReturn(false);
        when(channel.writableBytes()).thenReturn(Long.MAX_VALUE);
        when(channel.isWritable()).thenReturn(true);
        when(channel.getOption(ChannelOption.WRITE_BUFFER_WATER_MARK)).thenReturn(
                new WriteBufferWaterMark(1024, Integer.MAX_VALUE));
        when(channel.getOption(ChannelOption.MESSAGE_SIZE_ESTIMATOR)).thenReturn(DefaultMessageSizeEstimator.DEFAULT);
        handler.handlerAdded(ctx);
    }

    private Promise<Void> handlePromise() {
        Promise<Void> p = ImmediateEventExecutor.INSTANCE.newPromise();
        return p.setSuccess(null);
    }

    @AfterEach
    public void teardown() {
        // Close and release any buffered frames.
        encoder.close();

        // Notify all goAway ChannelPromise instances now as these will also release the retained ByteBuf for the
        // debugData.
        for (;;) {
            Promise<Void> promise = goAwayPromises.poll();
            if (promise == null) {
                break;
            }
            promise.setSuccess(null);
        }
    }

    @ParameterizedTest
    @EnumSource(Http2Error.class)
    public void testLimitRst(Http2Error error) {
        assertTrue(encoder.writeRstStream(ctx, 1, error.code()).isSuccess());
        assertTrue(encoder.writeRstStream(ctx, 1, error.code()).isSuccess());
        verifyFlushAndClose(0, false);
        assertTrue(encoder.writeRstStream(ctx, 1, error.code()).isSuccess());
        if (error == CANCEL || error == NO_ERROR) {
            // CANCEL and NO_ERROR are ignored as these will not be caused by a stream error.
            verifyFlushAndClose(0, false);
        } else {
            verifyFlushAndClose(1, true);
        }
    }

    @ParameterizedTest
    @EnumSource(Http2Error.class)
    public void testLimitRstReset(Http2Error error) throws Exception {
        assertTrue(encoder.writeRstStream(ctx, 1, error.code()).isSuccess());
        assertTrue(encoder.writeRstStream(ctx, 1, error.code()).isSuccess());
        verifyFlushAndClose(0, false);
        ticker.advance(1, TimeUnit.SECONDS);
        assertTrue(encoder.writeRstStream(ctx, 1, error.code()).isSuccess());
        verifyFlushAndClose(0, false);
    }

    private void verifyFlushAndClose(int invocations, boolean failed) {
        verify(ctx, atLeast(invocations)).flush();
        verify(ctx, times(invocations)).close();
        if (failed) {
            verify(writer, times(1)).writeGoAway(eq(ctx), eq(Integer.MAX_VALUE), eq(ENHANCE_YOUR_CALM.code()),
                    any(Buffer.class));
        }
    }

    private static Promise<Void> newPromise() {
        return ImmediateEventExecutor.INSTANCE.newPromise();
    }
}
