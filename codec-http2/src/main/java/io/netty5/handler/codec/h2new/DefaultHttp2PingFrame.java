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
package io.netty.handler.codec.h2new;

/**
 * Default implementation of {@link Http2PingFrame}.
 */
public final class DefaultHttp2PingFrame implements Http2PingFrame {
    private final boolean ack;
    private final long data;

    /**
     * Creates a new instance.
     *
     * @param ack {@code true} if this is a response to a ping frame.
     * @param data opaque data for this frame.
     */
    public DefaultHttp2PingFrame(boolean ack, long data) {
        this.ack = ack;
        this.data = data;
    }

    @Override
    public Type frameType() {
        return Type.Ping;
    }

    @Override
    public int streamId() {
        return 0;
    }

    @Override
    public long data() {
        return data;
    }

    @Override
    public boolean ack() {
        return ack;
    }
}
