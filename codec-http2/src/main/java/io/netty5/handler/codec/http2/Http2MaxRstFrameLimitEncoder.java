/*
 * Copyright 2025 The Netty Project
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

import io.netty5.channel.ChannelHandlerContext;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.Ticker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * {@link DecoratingHttp2ConnectionEncoder} which guards against a remote peer that will trigger a massive amount
 * of RST frames on an existing connection.
 * This encoder will tear-down the connection once we reached the configured limit to reduce the risk of DDOS.
 */
final class Http2MaxRstFrameLimitEncoder extends DecoratingHttp2ConnectionEncoder {
    private static final Logger logger = LoggerFactory.getLogger(Http2MaxRstFrameLimitEncoder.class);

    private final long nanosPerWindow;
    private final int maxRstFramesPerWindow;
    private final Ticker ticker;
    private long lastRstFrameNano;
    private int sendRstInWindow;
    private Http2LifecycleManager lifecycleManager;

    Http2MaxRstFrameLimitEncoder(Http2ConnectionEncoder delegate, int maxRstFramesPerWindow, int secondsPerWindow) {
        this(delegate, maxRstFramesPerWindow, secondsPerWindow, Ticker.systemTicker());
    }

    Http2MaxRstFrameLimitEncoder(Http2ConnectionEncoder delegate, int maxRstFramesPerWindow, int secondsPerWindow,
                                 Ticker ticker) {
        super(delegate);
        this.maxRstFramesPerWindow = maxRstFramesPerWindow;
        this.nanosPerWindow = TimeUnit.SECONDS.toNanos(secondsPerWindow);
        this.ticker = ticker;
        lastRstFrameNano = ticker.nanoTime();
    }

    @Override
    public void lifecycleManager(Http2LifecycleManager lifecycleManager) {
        this.lifecycleManager = lifecycleManager;
        super.lifecycleManager(lifecycleManager);
    }

    @Override
    public Future<Void> writeRstStream(ChannelHandlerContext ctx, int streamId, long errorCode) {
        Future<Void> future = super.writeRstStream(ctx, streamId, errorCode);
        if (countRstFrameErrorCode(errorCode)) {
            long currentNano = ticker.nanoTime();
            if (currentNano - lastRstFrameNano >= nanosPerWindow) {
                lastRstFrameNano = currentNano;
                sendRstInWindow = 1;
            } else {
                sendRstInWindow++;
                if (sendRstInWindow > maxRstFramesPerWindow) {
                    Http2Exception exception = Http2Exception.connectionError(Http2Error.ENHANCE_YOUR_CALM,
                            "Maximum number %d of RST frames frames reached within %d seconds", maxRstFramesPerWindow,
                            TimeUnit.NANOSECONDS.toSeconds(nanosPerWindow));

                    logger.debug("{} Maximum number {} of RST frames reached within {} seconds, " +
                                    "closing connection with {} error", ctx.channel(), maxRstFramesPerWindow,
                            TimeUnit.NANOSECONDS.toSeconds(nanosPerWindow), exception.error(),
                            exception);
                    // First notify the Http2LifecycleManager and then close the connection.
                    lifecycleManager.onError(ctx, true, exception);
                    ctx.close();
                }
            }
        }

        return future;
    }

    private boolean countRstFrameErrorCode(long errorCode) {
        // Don't count CANCEL and NO_ERROR as these might be ok.
        return errorCode != Http2Error.CANCEL.code() && errorCode != Http2Error.NO_ERROR.code();
    }
}
