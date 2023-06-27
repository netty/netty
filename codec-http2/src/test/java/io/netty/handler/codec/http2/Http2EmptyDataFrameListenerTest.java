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
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mock;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class Http2EmptyDataFrameListenerTest {

    @Mock
    private Http2FrameListener frameListener;
    @Mock
    private ChannelHandlerContext ctx;

    @Mock
    private ByteBuf nonEmpty;

    private Http2EmptyDataFrameListener listener;

    @BeforeEach
    public void setUp() {
        initMocks(this);
        when(nonEmpty.isReadable()).thenReturn(true);
        listener = new Http2EmptyDataFrameListener(frameListener, 2);
    }

    @Test
    public void testEmptyDataFrames() throws Http2Exception {
        listener.onDataRead(ctx, 1, Unpooled.EMPTY_BUFFER, 0, false);
        listener.onDataRead(ctx, 1, Unpooled.EMPTY_BUFFER, 0, false);

        assertThrows(Http2Exception.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                listener.onDataRead(ctx, 1, Unpooled.EMPTY_BUFFER, 0, false);
            }
        });
        verify(frameListener, times(2)).onDataRead(eq(ctx), eq(1), any(ByteBuf.class), eq(0), eq(false));
    }

    @Test
    public void testEmptyDataFramesWithNonEmptyInBetween() throws Http2Exception {
        final Http2EmptyDataFrameListener listener = new Http2EmptyDataFrameListener(frameListener, 2);
        listener.onDataRead(ctx, 1, Unpooled.EMPTY_BUFFER, 0, false);
        listener.onDataRead(ctx, 1, nonEmpty, 0, false);

        listener.onDataRead(ctx, 1, Unpooled.EMPTY_BUFFER, 0, false);
        listener.onDataRead(ctx, 1, Unpooled.EMPTY_BUFFER, 0, false);

        assertThrows(Http2Exception.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                listener.onDataRead(ctx, 1, Unpooled.EMPTY_BUFFER, 0, false);
            }
        });
        verify(frameListener, times(4)).onDataRead(eq(ctx), eq(1), any(ByteBuf.class), eq(0), eq(false));
    }

    @Test
    public void testEmptyDataFramesWithEndOfStreamInBetween() throws Http2Exception {
        final Http2EmptyDataFrameListener listener = new Http2EmptyDataFrameListener(frameListener, 2);
        listener.onDataRead(ctx, 1, Unpooled.EMPTY_BUFFER, 0, false);
        listener.onDataRead(ctx, 1, Unpooled.EMPTY_BUFFER, 0, true);

        listener.onDataRead(ctx, 1, Unpooled.EMPTY_BUFFER, 0, false);
        listener.onDataRead(ctx, 1, Unpooled.EMPTY_BUFFER, 0, false);

        assertThrows(Http2Exception.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                listener.onDataRead(ctx, 1, Unpooled.EMPTY_BUFFER, 0, false);
            }
        });

        verify(frameListener, times(1)).onDataRead(eq(ctx), eq(1), any(ByteBuf.class), eq(0), eq(true));
        verify(frameListener, times(3)).onDataRead(eq(ctx), eq(1), any(ByteBuf.class), eq(0), eq(false));
    }

    @Test
    public void testEmptyDataFramesWithHeaderFrameInBetween() throws Http2Exception {
        final Http2EmptyDataFrameListener listener = new Http2EmptyDataFrameListener(frameListener, 2);
        listener.onDataRead(ctx, 1, Unpooled.EMPTY_BUFFER, 0, false);
        listener.onHeadersRead(ctx, 1, EmptyHttp2Headers.INSTANCE, 0, true);

        listener.onDataRead(ctx, 1, Unpooled.EMPTY_BUFFER, 0, false);
        listener.onDataRead(ctx, 1, Unpooled.EMPTY_BUFFER, 0, false);

        assertThrows(Http2Exception.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                listener.onDataRead(ctx, 1, Unpooled.EMPTY_BUFFER, 0, false);
            }
        });

        verify(frameListener, times(1)).onHeadersRead(eq(ctx), eq(1), eq(EmptyHttp2Headers.INSTANCE), eq(0), eq(true));
        verify(frameListener, times(3)).onDataRead(eq(ctx), eq(1), any(ByteBuf.class), eq(0), eq(false));
    }

    @Test
    public void testEmptyDataFramesWithHeaderFrameInBetween2() throws Http2Exception {
        final Http2EmptyDataFrameListener listener = new Http2EmptyDataFrameListener(frameListener, 2);
        listener.onDataRead(ctx, 1, Unpooled.EMPTY_BUFFER, 0, false);
        listener.onHeadersRead(ctx, 1, EmptyHttp2Headers.INSTANCE, 0, (short) 0, false, 0, true);

        listener.onDataRead(ctx, 1, Unpooled.EMPTY_BUFFER, 0, false);
        listener.onDataRead(ctx, 1, Unpooled.EMPTY_BUFFER, 0, false);

        assertThrows(Http2Exception.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                listener.onDataRead(ctx, 1, Unpooled.EMPTY_BUFFER, 0, false);
            }
        });

        verify(frameListener, times(1)).onHeadersRead(eq(ctx), eq(1),
                eq(EmptyHttp2Headers.INSTANCE), eq(0), eq((short) 0), eq(false), eq(0), eq(true));
        verify(frameListener, times(3)).onDataRead(eq(ctx), eq(1), any(ByteBuf.class), eq(0), eq(false));
    }
}
