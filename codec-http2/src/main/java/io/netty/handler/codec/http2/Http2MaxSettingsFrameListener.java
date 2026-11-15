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
import io.netty.util.concurrent.Ticker;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.concurrent.TimeUnit;

final class Http2MaxSettingsFrameListener extends Http2FrameListenerDecorator {

    private static final InternalLogger logger =
        InternalLoggerFactory.getInstance(Http2MaxSettingsFrameListener.class);

    private static final Http2Exception SETTINGS_FRAME_RATE_EXCEEDED =
        Http2Exception.newStatic(
            Http2Error.ENHANCE_YOUR_CALM,
            "Maximum number of SETTINGS frames reached",
            Http2Exception.ShutdownHint.HARD_SHUTDOWN,
            Http2MaxSettingsFrameListener.class,
            "onSettingsRead(..)");

    private final long nanosPerWindow;
    private final int maxSettingsFramesPerWindow;
    private final Ticker ticker;

    private long lastSettingsFrameNano;
    private int receivedSettingsInWindow;

    Http2MaxSettingsFrameListener(
        Http2FrameListener listener,
        int maxSettingsFramesPerWindow,
        int secondsPerWindow,
        Ticker ticker) {

        super(listener);

        this.maxSettingsFramesPerWindow = maxSettingsFramesPerWindow;
        this.nanosPerWindow = TimeUnit.SECONDS.toNanos(secondsPerWindow);
        this.ticker = ticker;
        this.lastSettingsFrameNano = ticker.nanoTime() - nanosPerWindow;
    }

    @Override
    public void onSettingsRead(
        ChannelHandlerContext ctx,
        Http2Settings settings) throws Http2Exception {

        long currentNano = ticker.nanoTime();

        if (currentNano - lastSettingsFrameNano >= nanosPerWindow) {
            lastSettingsFrameNano = currentNano;
            receivedSettingsInWindow = 1;
        } else {
            receivedSettingsInWindow++;

            if (receivedSettingsInWindow > maxSettingsFramesPerWindow) {
                logger.debug(
                    "{} Maximum number {} of SETTINGS frames reached within {} seconds, " +
                        "closing connection with {} error",
                    ctx.channel(),
                    maxSettingsFramesPerWindow,
                    TimeUnit.NANOSECONDS.toSeconds(nanosPerWindow),
                    SETTINGS_FRAME_RATE_EXCEEDED.error(),
                    SETTINGS_FRAME_RATE_EXCEEDED);

                throw SETTINGS_FRAME_RATE_EXCEEDED;
            }
        }

        super.onSettingsRead(ctx, settings);
    }
}
