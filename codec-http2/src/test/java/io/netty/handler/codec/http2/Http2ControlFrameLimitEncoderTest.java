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
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelPromise;
import io.netty.channel.DefaultMessageSizeEstimator;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.ImmediateEventExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;


import java.util.ArrayDeque;
import java.util.Queue;

import static io.netty.handler.codec.http2.Http2CodecUtil.*;
import static io.netty.handler.codec.http2.Http2Error.CANCEL;
import static io.netty.handler.codec.http2.Http2Error.ENHANCE_YOUR_CALM;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

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

    private Queue<ChannelPromise> goAwayPromises = new ArrayDeque<ChannelPromise>();

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

        when(writer.writeRstStream(eq(ctx), anyInt(), anyLong(), any(ChannelPromise.class)))
                .thenAnswer(new Answer<ChannelFuture>() {
                    @Override
                    public ChannelFuture answer(InvocationOnMock invocationOnMock) {
                        return handlePromise(invocationOnMock, 3);
                    }
                });
        when(writer.writeSettingsAck(any(ChannelHandlerContext.class), any(ChannelPromise.class)))
                .thenAnswer(new Answer<ChannelFuture>() {
                    @Override
                    public ChannelFuture answer(InvocationOnMock invocationOnMock) {
                        return handlePromise(invocationOnMock, 1);
                    }
        });
        when(writer.writePing(any(ChannelHandlerContext.class), anyBoolean(), anyLong(), any(ChannelPromise.class)))
                .thenAnswer(new Answer<ChannelFuture>() {
                    @Override
                    public ChannelFuture answer(InvocationOnMock invocationOnMock) {
                        ChannelPromise promise = handlePromise(invocationOnMock, 3);
                        if (invocationOnMock.getArgument(1) == Boolean.FALSE) {
                            promise.trySuccess();
                        }
                        return promise;
                    }
                });
        when(writer.writeGoAway(any(ChannelHandlerContext.class), anyInt(), anyLong(), any(ByteBuf.class),
                any(ChannelPromise.class))).thenAnswer(new Answer<ChannelFuture>() {
            @Override
            public ChannelFuture answer(InvocationOnMock invocationOnMock) {
                ReferenceCountUtil.release(invocationOnMock.getArgument(3));
                ChannelPromise promise = invocationOnMock.getArgument(4);
                goAwayPromises.offer(promise);
                return promise;
            }
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
        doAnswer(new Answer<ChannelPromise>() {
            @Override
            public ChannelPromise answer(InvocationOnMock invocation) throws Throwable {
                return newPromise();
            }
        }).when(ctx).newPromise();
        when(ctx.executor()).thenReturn(executor);
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

    private ChannelPromise handlePromise(InvocationOnMock invocationOnMock, int promiseIdx) {
        ChannelPromise promise = invocationOnMock.getArgument(promiseIdx);
        if (++numWrites == 2) {
            promise.setSuccess();
        }
        return promise;
    }

    @AfterEach
    public void teardown() {
        // Close and release any buffered frames.
        encoder.close();

        // Notify all goAway ChannelPromise instances now as these will also release the retained ByteBuf for the
        // debugData.
        for (;;) {
            ChannelPromise promise = goAwayPromises.poll();
            if (promise == null) {
                break;
            }
            promise.setSuccess();
        }
    }

    @Test
    public void testLimitSettingsAck() {
        assertFalse(encoder.writeSettingsAck(ctx, newPromise()).isDone());
        // The second write is always marked as success by our mock, which means it will also not be queued and so
        // not count to the number of queued frames.
        assertTrue(encoder.writeSettingsAck(ctx, newPromise()).isSuccess());
        assertFalse(encoder.writeSettingsAck(ctx, newPromise()).isDone());

        verifyFlushAndClose(0, false);

        assertFalse(encoder.writeSettingsAck(ctx, newPromise()).isDone());
        assertFalse(encoder.writeSettingsAck(ctx, newPromise()).isDone());

        verifyFlushAndClose(1, true);
    }

    @Test
    public void testLimitPingAck() {
        assertFalse(encoder.writePing(ctx, true, 8, newPromise()).isDone());
        // The second write is always marked as success by our mock, which means it will also not be queued and so
        // not count to the number of queued frames.
        assertTrue(encoder.writePing(ctx, true, 8, newPromise()).isSuccess());
        assertFalse(encoder.writePing(ctx, true, 8, newPromise()).isDone());

        verifyFlushAndClose(0, false);

        assertFalse(encoder.writePing(ctx, true, 8, newPromise()).isDone());
        assertFalse(encoder.writePing(ctx, true, 8, newPromise()).isDone());

        verifyFlushAndClose(1, true);
    }

    @Test
    public void testNotLimitPing() {
        assertTrue(encoder.writePing(ctx, false, 8, newPromise()).isSuccess());
        assertTrue(encoder.writePing(ctx, false, 8, newPromise()).isSuccess());
        assertTrue(encoder.writePing(ctx, false, 8, newPromise()).isSuccess());
        assertTrue(encoder.writePing(ctx, false, 8, newPromise()).isSuccess());

        verifyFlushAndClose(0, false);
    }

    @Test
    public void testLimitRst() {
        assertFalse(encoder.writeRstStream(ctx, 1, CANCEL.code(), newPromise()).isDone());
        // The second write is always marked as success by our mock, which means it will also not be queued and so
        // not count to the number of queued frames.
        assertTrue(encoder.writeRstStream(ctx, 1, CANCEL.code(), newPromise()).isSuccess());
        assertFalse(encoder.writeRstStream(ctx, 1, CANCEL.code(), newPromise()).isDone());

        verifyFlushAndClose(0, false);

        assertFalse(encoder.writeRstStream(ctx, 1, CANCEL.code(), newPromise()).isDone());
        assertFalse(encoder.writeRstStream(ctx, 1, CANCEL.code(), newPromise()).isDone());

        verifyFlushAndClose(1, true);
    }

    @Test
    public void testLimit() {
        assertFalse(encoder.writeRstStream(ctx, 1, CANCEL.code(), newPromise()).isDone());
        // The second write is always marked as success by our mock, which means it will also not be queued and so
        // not count to the number of queued frames.
        assertTrue(encoder.writePing(ctx, false, 8, newPromise()).isSuccess());
        assertFalse(encoder.writePing(ctx, true, 8, newPromise()).isSuccess());

        verifyFlushAndClose(0, false);

        assertFalse(encoder.writeSettingsAck(ctx, newPromise()).isDone());
        assertFalse(encoder.writeRstStream(ctx, 1, CANCEL.code(), newPromise()).isDone());
        assertFalse(encoder.writePing(ctx, true, 8, newPromise()).isSuccess());

        verifyFlushAndClose(1, true);
    }

    private void verifyFlushAndClose(int invocations, boolean failed) {
        verify(ctx, atLeast(invocations)).flush();
        verify(ctx, times(invocations)).close();
        if (failed) {
            verify(writer, times(1)).writeGoAway(eq(ctx), eq(Integer.MAX_VALUE), eq(ENHANCE_YOUR_CALM.code()),
                    any(ByteBuf.class), any(ChannelPromise.class));
        }
    }

    private ChannelPromise newPromise() {
        return new DefaultChannelPromise(channel, ImmediateEventExecutor.INSTANCE);
    }
}
