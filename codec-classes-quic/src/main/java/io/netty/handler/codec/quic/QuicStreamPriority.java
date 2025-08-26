/*
 * Copyright 2021 The Netty Project
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
package io.netty.handler.codec.quic;

import io.netty.util.internal.ObjectUtil;

import java.util.Objects;

/**
 * The priority of a {@link QuicStreamChannel}.
 */
public final class QuicStreamPriority {

    private final int urgency;
    private final boolean incremental;

    /**
     * Create a new instance
     *
     * @param urgency       the urgency of the stream.
     * @param incremental   {@code true} if incremental.
     */
    public QuicStreamPriority(int urgency, boolean incremental) {
        this.urgency = ObjectUtil.checkInRange(urgency, 0, Byte.MAX_VALUE, "urgency");
        this.incremental = incremental;
    }

    /**
     * The urgency of the stream. Smaller number means more urgent and so data will be send earlier.
     *
     * @return  the urgency.
     */
    public int urgency() {
        return urgency;
    }

    /**
     * {@code true} if incremental, {@code false} otherwise.
     *
     * @return  if incremental.
     */
    public boolean isIncremental() {
        return incremental;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        QuicStreamPriority that = (QuicStreamPriority) o;
        return urgency == that.urgency && incremental == that.incremental;
    }

    @Override
    public int hashCode() {
        return Objects.hash(urgency, incremental);
    }

    @Override
    public String toString() {
        return "QuicStreamPriority{" +
                "urgency=" + urgency +
                ", incremental=" + incremental +
                '}';
    }
}
