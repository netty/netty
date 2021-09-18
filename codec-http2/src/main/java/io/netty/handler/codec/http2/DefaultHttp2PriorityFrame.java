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
public final class DefaultHttp2PriorityFrame extends AbstractHttp2StreamFrame implements Http2PriorityFrame {

    private final int streamDependency;
    private final short weight;
    private final boolean exclusive;

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
    public DefaultHttp2PriorityFrame stream(Http2FrameStream stream) {
        super.stream(stream);
        return this;
    }

    @Override
    public String name() {
        return "PRIORITY_FRAME";
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DefaultHttp2PriorityFrame)) {
            return false;
        }
        DefaultHttp2PriorityFrame other = (DefaultHttp2PriorityFrame) o;
        boolean same = super.equals(other);
        return same && streamDependency == other.streamDependency
                && weight == other.weight && exclusive == other.exclusive;
    }

    @Override
    public int hashCode() {
        int hash = super.hashCode();
        hash = hash * 31 + streamDependency;
        hash = hash * 31 + weight;
        hash = hash * 31 + (exclusive ? 1 : 0);
        return hash;
    }

    @Override
    public String toString() {
        return "DefaultHttp2PriorityFrame(" +
                "stream=" + stream() +
                ", streamDependency=" + streamDependency +
                ", weight=" + weight +
                ", exclusive=" + exclusive +
                ')';
    }
}
