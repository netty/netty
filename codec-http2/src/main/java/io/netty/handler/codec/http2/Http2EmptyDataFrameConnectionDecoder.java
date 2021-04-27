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

import io.netty.util.internal.ObjectUtil;

/**
 * Enforce a limit on the maximum number of consecutive empty DATA frames (without end_of_stream flag) that are allowed
 * before the connection will be closed.
 */
final class Http2EmptyDataFrameConnectionDecoder extends DecoratingHttp2ConnectionDecoder {

    private final int maxConsecutiveEmptyFrames;

    Http2EmptyDataFrameConnectionDecoder(Http2ConnectionDecoder delegate, int maxConsecutiveEmptyFrames) {
        super(delegate);
        this.maxConsecutiveEmptyFrames = ObjectUtil.checkPositive(
                maxConsecutiveEmptyFrames, "maxConsecutiveEmptyFrames");
    }

    @Override
    public void frameListener(Http2FrameListener listener) {
        if (listener != null) {
            super.frameListener(new Http2EmptyDataFrameListener(listener, maxConsecutiveEmptyFrames));
        } else {
            super.frameListener(null);
        }
    }

    @Override
    public Http2FrameListener frameListener() {
        Http2FrameListener frameListener = frameListener0();
        // Unwrap the original Http2FrameListener as we add this decoder under the hood.
        if (frameListener instanceof Http2EmptyDataFrameListener) {
            return ((Http2EmptyDataFrameListener) frameListener).listener;
        }
        return frameListener;
    }

    // Package-private for testing
    Http2FrameListener frameListener0() {
        return super.frameListener();
    }
}
