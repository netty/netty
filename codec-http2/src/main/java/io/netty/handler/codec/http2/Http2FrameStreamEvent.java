/*
 * Copyright 2017 The Netty Project
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

@UnstableApi
public final class Http2FrameStreamEvent {

    private final Http2FrameStream stream;
    private final Type type;

    @UnstableApi
    public enum Type {
        State,
        Writability
    }

    private Http2FrameStreamEvent(Http2FrameStream stream, Type type) {
        this.stream = stream;
        this.type = type;
    }

    public Http2FrameStream stream() {
        return stream;
    }

    public Type type() {
        return type;
    }

    static Http2FrameStreamEvent stateChanged(Http2FrameStream stream) {
        return new Http2FrameStreamEvent(stream, Type.State);
    }

    static Http2FrameStreamEvent writabilityChanged(Http2FrameStream stream) {
        return new Http2FrameStreamEvent(stream, Type.Writability);
    }
}
