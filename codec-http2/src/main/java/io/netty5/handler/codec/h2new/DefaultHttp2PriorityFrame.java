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

import static io.netty5.util.internal.ObjectUtil.checkInRange;
import static io.netty5.util.internal.ObjectUtil.checkPositive;
import static io.netty5.util.internal.ObjectUtil.checkPositiveOrZero;

/**
 * Default implementation of {@link Http2PriorityFrame}.
 */
public class DefaultHttp2PriorityFrame implements Http2PriorityFrame {
    private final int streamId;
    private final int streamDependency;
    private final boolean isExclusive;
    private final short weight;

    /**
     * Creates a new instance.
     *
     * @param streamId the identifier for the stream on which this frame was sent/received.
     * @param streamDependency positive number indicating the stream dependency.
     * @param isExclusive {@code true} if the stream dependency is exclusive.
     * @param weight number between 1 and 256 (both inclusive) indicating the stream weight.
     */
    public DefaultHttp2PriorityFrame(int streamId, int streamDependency, boolean isExclusive, short weight) {
        this.streamId = checkPositiveOrZero(streamId, "streamId");
        this.streamDependency = checkPositive(streamDependency, "streamDependency");
        this.isExclusive = isExclusive;
        this.weight = checkInRange(weight, (short) 1, (short) 256, "weight");
    }

    @Override
    public Type frameType() {
        return Type.Priority;
    }

    @Override
    public int streamId() {
        return streamId;
    }

    @Override
    public boolean isExclusiveDependency() {
        return isExclusive;
    }

    @Override
    public int streamDependency() {
        return streamDependency;
    }

    @Override
    public short weight() {
        return weight;
    }
}
