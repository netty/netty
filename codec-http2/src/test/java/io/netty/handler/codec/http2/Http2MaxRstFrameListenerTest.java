/*
 * Copyright 2023 The Netty Project
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

import io.netty.channel.ChannelHandlerContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

public class Http2MaxRstFrameListenerTest {

    @Mock
    private Http2FrameListener frameListener;
    @Mock
    private ChannelHandlerContext ctx;

    private Http2MaxRstFrameListener listener;

    @BeforeEach
    public void setUp() {
        initMocks(this);
    }

    @Test
    public void testMaxRstFramesReached() throws Http2Exception {
        listener = new Http2MaxRstFrameListener(frameListener, 1, 10);
        listener.onRstStreamRead(ctx, 1, Http2Error.STREAM_CLOSED.code());

        Http2Exception ex = assertThrows(Http2Exception.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                listener.onRstStreamRead(ctx, 2, Http2Error.STREAM_CLOSED.code());
            }
        });
        assertEquals(Http2Error.ENHANCE_YOUR_CALM, ex.error());
        verify(frameListener, times(1)).onRstStreamRead(eq(ctx), anyInt(), eq(Http2Error.STREAM_CLOSED.code()));
    }

    @Test
    public void testRstFrames() throws Exception {
        listener = new Http2MaxRstFrameListener(frameListener, 1, 1);
        listener.onRstStreamRead(ctx, 1, Http2Error.STREAM_CLOSED.code());
        Thread.sleep(1100);
        listener.onRstStreamRead(ctx, 1, Http2Error.STREAM_CLOSED.code());
        verify(frameListener, times(2)).onRstStreamRead(eq(ctx), anyInt(), eq(Http2Error.STREAM_CLOSED.code()));
    }
}
