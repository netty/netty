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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.DefaultMessageSizeEstimator;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.MockTicker;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.Ticker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

import static io.netty.handler.codec.http2.Http2CodecUtil.DEFAULT_MAX_FRAME_SIZE;
import static io.netty.handler.codec.http2.Http2Error.CANCEL;
import static io.netty.handler.codec.http2.Http2Error.ENHANCE_YOUR_CALM;
import static io.netty.handler.codec.http2.Http2Error.NO_ERROR;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    private ChannelConfig config;

    @Mock
    private EventExecutor executor;

    private final MockTicker ticker = Ticker.newMockTicker();

    private final Queue<Promise> goAwayPromises = new ArrayDeque<>();

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

        doAnswer(invocationOnMock -> {
            handlePromise(invocationOnMock, 3);
            return null;
        }).when(writer).writeRstStream(eq(ctx), anyInt(), anyLong(), any(Promise.class));
        doAnswer(invocationOnMock -> {
            ReferenceCountUtil.release(invocationOnMock.getArgument(3));
            Promise<Void> promise = invocationOnMock.getArgument(4);
            goAwayPromises.offer(promise);
            return null;
        }).when(writer).writeGoAway(any(ChannelHandlerContext.class), anyInt(), anyLong(), any(ByteBuf.class),
                any(Promise.class));
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
        when(ctx.alloc()).thenReturn(UnpooledByteBufAllocator.DEFAULT);
        when(channel.alloc()).thenReturn(UnpooledByteBufAllocator.DEFAULT);
        when(executor.inEventLoop()).thenReturn(true);
        doAnswer(new Answer<>() {
            @Override
            public Promise<Void> answer(InvocationOnMock invocation) throws Throwable {
                return newPromise();
            }
        }).when(ctx).newPromise();
        when(ctx.executor()).thenReturn(executor);
        when(channel.isActive()).thenReturn(false);
        when(channel.config()).thenReturn(config);
        when(channel.isWritable()).thenReturn(true);
        when(channel.bytesBeforeUnwritable()).thenReturn(Long.MAX_VALUE);
        when(config.getWriteBufferWaterMark()).thenReturn(new WriteBufferWaterMark(0, Integer.MAX_VALUE));
        when(config.getMessageSizeEstimator()).thenReturn(DefaultMessageSizeEstimator.DEFAULT);
        handler.handlerAdded(ctx);
    }

    private Promise<Void> handlePromise(InvocationOnMock invocationOnMock, int promiseIdx) {
        Promise<Void> promise = invocationOnMock.getArgument(promiseIdx);
        return promise.setSuccess(null);
    }

    @AfterEach
    public void teardown() {
        // Close and release any buffered frames.
        encoder.close();

        // Notify all goAway Promise<Void> instances now as these will also release the retained ByteBuf for the
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
        Promise<Void> promise = newPromise();
        encoder.writeRstStream(ctx, 1, error.code(), promise);
        assertTrue(promise.isSuccess());
        promise = newPromise();
        encoder.writeRstStream(ctx, 1, error.code(), promise);
        assertTrue(promise.isSuccess());
        verifyFlushAndClose(0, false);
        promise = newPromise();
        encoder.writeRstStream(ctx, 1, error.code(), promise);
        assertTrue(promise.isSuccess());
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
        Promise<Void> promise = newPromise();
        encoder.writeRstStream(ctx, 1, error.code(), promise);
        assertTrue(promise.isSuccess());
        promise = newPromise();
        encoder.writeRstStream(ctx, 1, error.code(), promise);
        assertTrue(promise.isSuccess());
        verifyFlushAndClose(0, false);
        ticker.advance(1, TimeUnit.SECONDS);
        promise = newPromise();
        encoder.writeRstStream(ctx, 1, error.code(), promise);
        assertTrue(promise.isSuccess());
        verifyFlushAndClose(0, false);
    }

    private void verifyFlushAndClose(int invocations, boolean failed) {
        verify(ctx, atLeast(invocations)).flush();
        verify(ctx, times(invocations)).close();
        if (failed) {
            verify(writer, times(1)).writeGoAway(eq(ctx), eq(Integer.MAX_VALUE), eq(ENHANCE_YOUR_CALM.code()),
                    any(ByteBuf.class), any(Promise.class));
        }
    }

    private Promise<Void> newPromise() {
        return ImmediateEventExecutor.INSTANCE.newPromise();
    }
}
