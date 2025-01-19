/*
 * Copyright 2025 The Netty Project
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
package io.netty.channel.uring;

/**
 * Event that is fired when a cqe`s res is NO_BUFFER
 */
public final class BufferRingExhaustedEvent {
    private short bufferGroupId;

    public BufferRingExhaustedEvent(short bufferGroupId) {
        this.bufferGroupId = bufferGroupId;
    }

    public short bufferGroupId() {
        return bufferGroupId;
    }

    @Override
    public int hashCode() {
        return Short.hashCode(bufferGroupId);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof BufferRingExhaustedEvent) {
            return bufferGroupId == ((BufferRingExhaustedEvent) obj).bufferGroupId;
        }
        return false;
    }
}
