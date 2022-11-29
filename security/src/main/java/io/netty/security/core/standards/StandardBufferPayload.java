/*
 * Copyright 2022 The Netty Project
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
package io.netty.security.core.standards;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.security.core.payload.BufferPayload;
import io.netty.util.internal.ObjectUtil;

import static io.netty.security.core.Util.hash;

public final class StandardBufferPayload implements BufferPayload {
    private final ByteBuf buffer;

    private StandardBufferPayload(ByteBuf buffer) {
        this.buffer = ObjectUtil.checkNotNull(buffer, "Buffer");
    }

    /**
     * Create a new {@link StandardBufferPayload} with specified {@link ByteBuf}
     *
     * @param buffer {@link ByteBuf} to use as needle
     * @return New {@link StandardBufferPayload} instance
     */
    public static StandardBufferPayload create(ByteBuf buffer) {
        return new StandardBufferPayload(buffer);
    }

    @Override
    public ByteBuf needle() {
        return buffer;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        StandardBufferPayload that = (StandardBufferPayload) o;
        return ByteBufUtil.equals(buffer, that.buffer);
    }

    @Override
    public int hashCode() {
        return hash(buffer);
    }

    @Override
    public String toString() {
        return "BufferPayloadHolder{buffer=" + buffer + '}';
    }
}
