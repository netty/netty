/*
 * Copyright 2020 The Netty Project
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

import io.netty.util.internal.UnstableApi;

/**
 * Default implementation of {@linkplain Http2PriorityFrame}
 */
@UnstableApi
public final class DefaultHttp2PriorityFrame implements Http2PriorityFrame {

    private final int streamDependency;
    private final short weight;
    private final boolean exclusive;
    private Http2FrameStream http2FrameStream;

    public DefaultHttp2PriorityFrame(int streamDependency, short weight, boolean exclusive) {
        this.streamDependency = streamDependency;
        this.weight = weight;
        this.exclusive = exclusive;
    }

    @Override
    public int streamDependency() {
        return streamDependency;
    }

    @Override
    public short weight() {
        return weight;
    }

    @Override
    public boolean exclusive() {
        return exclusive;
    }

    @Override
    public Http2PriorityFrame stream(Http2FrameStream stream) {
        http2FrameStream = stream;
        return this;
    }

    @Override
    public Http2FrameStream stream() {
        return http2FrameStream;
    }

    @Override
    public String name() {
        return "PRIORITY_FRAME";
    }

    @Override
    public String toString() {
        return "DefaultHttp2PriorityFrame(" +
                "stream=" + http2FrameStream +
                ", streamDependency=" + streamDependency +
                ", weight=" + weight +
                ", exclusive=" + exclusive +
                ')';
    }
}
