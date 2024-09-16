/*
 * Copyright 2023 The Netty Project
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
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.concurrent.TimeUnit;


final class Http2MaxRstFrameListener extends Http2FrameListenerDecorator {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(Http2MaxRstFrameListener.class);
    private static final Http2Exception RST_FRAME_RATE_EXCEEDED = Http2Exception.newStatic(Http2Error.ENHANCE_YOUR_CALM,
            "Maximum number of RST frames reached",
            Http2Exception.ShutdownHint.HARD_SHUTDOWN, Http2MaxRstFrameListener.class, "onRstStreamRead(..)");

    private final long nanosPerWindow;
    private final int maxRstFramesPerWindow;
    private long lastRstFrameNano = System.nanoTime();
    private int receivedRstInWindow;

    Http2MaxRstFrameListener(Http2FrameListener listener, int maxRstFramesPerWindow, int secondsPerWindow) {
        super(listener);
        this.maxRstFramesPerWindow = maxRstFramesPerWindow;
        this.nanosPerWindow = TimeUnit.SECONDS.toNanos(secondsPerWindow);
    }

    @Override
    public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode) throws Http2Exception {
        long currentNano = System.nanoTime();
        if (currentNano - lastRstFrameNano >= nanosPerWindow) {
            lastRstFrameNano = currentNano;
            receivedRstInWindow = 1;
        } else {
            receivedRstInWindow++;
            if (receivedRstInWindow > maxRstFramesPerWindow) {
                logger.debug("{} Maximum number {} of RST frames reached within {} seconds, " +
                                "closing connection with {} error", ctx.channel(), maxRstFramesPerWindow,
                        TimeUnit.NANOSECONDS.toSeconds(nanosPerWindow), RST_FRAME_RATE_EXCEEDED.error(),
                        RST_FRAME_RATE_EXCEEDED);
                throw RST_FRAME_RATE_EXCEEDED;
            }
        }
        super.onRstStreamRead(ctx, streamId, errorCode);
    }
}
