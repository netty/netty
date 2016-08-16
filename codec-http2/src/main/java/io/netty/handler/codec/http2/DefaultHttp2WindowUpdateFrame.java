/*
 * Copyright 2016 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.http2;

import io.netty.util.internal.UnstableApi;

import static io.netty.util.internal.ObjectUtil.checkPositive;

/**
 * The default {@link Http2WindowUpdateFrame} implementation.
 */
@UnstableApi
public class DefaultHttp2WindowUpdateFrame extends AbstractHttp2StreamFrame implements Http2WindowUpdateFrame {

    private final int windowUpdateIncrement;

    public DefaultHttp2WindowUpdateFrame(int windowUpdateIncrement) {
        this.windowUpdateIncrement = checkPositive(windowUpdateIncrement, "windowUpdateIncrement");
    }

    @Override
    public DefaultHttp2WindowUpdateFrame streamId(int streamId) {
        super.streamId(streamId);
        return this;
    }

    @Override
    public String name() {
        return "WINDOW_UPDATE";
    }

    @Override
    public int windowSizeIncrement() {
        return windowUpdateIncrement;
    }
}
