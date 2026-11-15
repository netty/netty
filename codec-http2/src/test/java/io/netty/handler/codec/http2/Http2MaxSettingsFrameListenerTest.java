/*
 * Copyright 2026 The Netty Project
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
package io.netty.handler.codec.http2;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.MockTicker;
import io.netty.util.concurrent.Ticker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

public class Http2MaxSettingsFrameListenerTest {

    @Mock
    private Http2FrameListener frameListener;

    @Mock
    private ChannelHandlerContext ctx;

    private final MockTicker ticker = Ticker.newMockTicker();

    private Http2MaxSettingsFrameListener listener;

    @BeforeEach
    public void setUp() {
        initMocks(this);
    }

    @Test
    public void testMaxSettingsFramesReached() throws Http2Exception {
        listener = new Http2MaxSettingsFrameListener(frameListener, 1, 10, ticker);

        listener.onSettingsRead(ctx, new Http2Settings());

        Http2Exception ex = assertThrows(
            Http2Exception.class,
            () -> listener.onSettingsRead(ctx, new Http2Settings()));

        assertEquals(Http2Error.ENHANCE_YOUR_CALM, ex.error());

        verify(frameListener, times(1)).onSettingsRead(eq(ctx), eq(new Http2Settings()));
    }

    @Test
    public void testSettingsFramesWindowReset() throws Exception {
        listener = new Http2MaxSettingsFrameListener(frameListener, 1, 1, ticker);

        listener.onSettingsRead(ctx, new Http2Settings());

        ticker.advance(1, TimeUnit.SECONDS);

        listener.onSettingsRead(ctx, new Http2Settings());

        verify(frameListener, times(2)).onSettingsRead(eq(ctx), eq(new Http2Settings()));
    }

    @Test
    public void testSettingsAckDoesNotCountTowardsLimit() throws Exception {
        listener = new Http2MaxSettingsFrameListener(frameListener, 1, 10, ticker);

        listener.onSettingsAckRead(ctx);
        listener.onSettingsRead(ctx, new Http2Settings());

        Http2Exception ex = assertThrows(
            Http2Exception.class,
            () -> listener.onSettingsRead(ctx, new Http2Settings()));

        assertEquals(Http2Error.ENHANCE_YOUR_CALM, ex.error());

        verify(frameListener, times(1)).onSettingsAckRead(ctx);
        verify(frameListener, times(1)).onSettingsRead(eq(ctx), eq(new Http2Settings()));
    }

    @Test
    public void testAllSettingsFramesWithinLimitAreForwarded() throws Exception {
        listener = new Http2MaxSettingsFrameListener(frameListener, 3, 10, ticker);

        listener.onSettingsRead(ctx, new Http2Settings());
        listener.onSettingsRead(ctx, new Http2Settings());
        listener.onSettingsRead(ctx, new Http2Settings());

        verify(frameListener, times(3)).onSettingsRead(eq(ctx), eq(new Http2Settings()));
    }

    @Test
    public void testSettingsFramesAreCountedAcrossMultipleBursts() throws Exception {
        listener = new Http2MaxSettingsFrameListener(frameListener, 5, 10, ticker);

        listener.onSettingsRead(ctx, new Http2Settings());
        listener.onSettingsRead(ctx, new Http2Settings());

        ticker.advance(1, TimeUnit.SECONDS);

        listener.onSettingsRead(ctx, new Http2Settings());
        listener.onSettingsRead(ctx, new Http2Settings());
        listener.onSettingsRead(ctx, new Http2Settings());

        Http2Exception ex = assertThrows(
            Http2Exception.class,
            () -> listener.onSettingsRead(ctx, new Http2Settings()));

        assertEquals(Http2Error.ENHANCE_YOUR_CALM, ex.error());

        verify(frameListener, times(5)).onSettingsRead(eq(ctx), eq(new Http2Settings()));
    }
}
