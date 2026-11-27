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

import io.netty.util.concurrent.Ticker;

import static io.netty.util.internal.ObjectUtil.checkPositive;

/**
 * Enforce a limit on the maximum number of inbound SETTINGS frames allowed
 * per window before the connection is closed.
 */
final class Http2MaxSettingsFrameDecoder extends DecoratingHttp2ConnectionDecoder {

    private final int maxSettingsFramesPerWindow;
    private final int secondsPerWindow;

    Http2MaxSettingsFrameDecoder(
        Http2ConnectionDecoder delegate,
        int maxSettingsFramesPerWindow,
        int secondsPerWindow) {

        super(delegate);

        this.maxSettingsFramesPerWindow =
            checkPositive(maxSettingsFramesPerWindow, "maxSettingsFramesPerWindow");
        this.secondsPerWindow = checkPositive(secondsPerWindow, "secondsPerWindow");
    }

    @Override
    public void frameListener(Http2FrameListener listener) {
        if (listener != null) {
            super.frameListener(new Http2MaxSettingsFrameListener(
                listener,
                maxSettingsFramesPerWindow,
                secondsPerWindow,
                Ticker.systemTicker()));
        } else {
            super.frameListener(null);
        }
    }

    @Override
    public Http2FrameListener frameListener() {
        Http2FrameListener frameListener = frameListener0();

        // Unwrap the original Http2FrameListener as we add this decoder under the hood.
        if (frameListener instanceof Http2MaxSettingsFrameListener) {
            return ((Http2MaxSettingsFrameListener) frameListener).listener;
        }

        return frameListener;
    }

    // Package-private for testing
    Http2FrameListener frameListener0() {
        return super.frameListener();
    }
}
