/*
 * Copyright 2021 The Netty Project
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
package io.netty5.handler.codec.h2new;

import static io.netty5.util.internal.ObjectUtil.checkPositive;
import static io.netty5.util.internal.ObjectUtil.checkPositiveOrZero;

/**
 * Default implementation of {@link Http2WindowUpdateFrame}.
 */
public class DefaultHttp2WindowUpdateFrame implements Http2WindowUpdateFrame {
    private final int streamId;
    private final int windowSizeIncrement;

    /**
     * Creates a new instance.
     *
     * @param streamId the identifier for the stream on which this frame was sent/received.
     * @param windowSizeIncrement the number of bytes to increment the flow control window, must be positive.
     */
    public DefaultHttp2WindowUpdateFrame(int streamId, int windowSizeIncrement) {
        this.streamId = checkPositiveOrZero(streamId, "streamId");
        this.windowSizeIncrement = checkPositive(windowSizeIncrement, "windowSizeIncrement");
    }

    @Override
    public Type frameType() {
        return Type.WindowUpdate;
    }

    @Override
    public int streamId() {
        return streamId;
    }

    @Override
    public int windowSizeIncrement() {
        return windowSizeIncrement;
    }
}
