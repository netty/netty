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

import static io.netty.util.internal.ObjectUtil.checkPositive;


/**
 * Enforce a limit on the maximum number of RST frames that are allowed per a window
 * before the connection will be closed with a GO_AWAY frame.
 */
final class Http2MaxRstFrameDecoder extends DecoratingHttp2ConnectionDecoder {
    private final int maxRstFramesPerWindow;
    private final int secondsPerWindow;

    Http2MaxRstFrameDecoder(Http2ConnectionDecoder delegate, int maxRstFramesPerWindow, int secondsPerWindow) {
        super(delegate);
        this.maxRstFramesPerWindow = checkPositive(maxRstFramesPerWindow, "maxRstFramesPerWindow");
        this.secondsPerWindow = checkPositive(secondsPerWindow, "secondsPerWindow");
    }

    @Override
    public void frameListener(Http2FrameListener listener) {
        if (listener != null) {
            super.frameListener(new Http2MaxRstFrameListener(listener, maxRstFramesPerWindow, secondsPerWindow));
        } else {
            super.frameListener(null);
        }
    }

    @Override
    public Http2FrameListener frameListener() {
        Http2FrameListener frameListener = frameListener0();
        // Unwrap the original Http2FrameListener as we add this decoder under the hood.
        if (frameListener instanceof Http2MaxRstFrameListener) {
            return ((Http2MaxRstFrameListener) frameListener).listener;
        }
        return frameListener;
    }

    // Package-private for testing
    Http2FrameListener frameListener0() {
        return super.frameListener();
    }
}
