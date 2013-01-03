/*
 * Copyright 2012 The Netty Project
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
package io.netty.buffer;

/**
 * Default implementation of a {@link ByteBufHolder} that holds it's data in a {@link ByteBuf}.
 *
 */
public class DefaultByteBufHolder implements ByteBufHolder {
    private final ByteBuf data;
    private boolean freed;
    public DefaultByteBufHolder(ByteBuf data) {
        if (data == null) {
            throw new NullPointerException("data");
        }
        this.data = data;
    }

    @Override
    public ByteBuf data() {
        if (freed) {
            throw new IllegalBufferAccessException("Packet was freed already");
        }
        return data;
    }

    @Override
    public void free() {
        if (!freed) {
            freed = true;
            if (!data.isFreed()) {
                try {
                    data.free();
                } catch (UnsupportedOperationException e) {
                    // free not supported
                }
            }
        }
    }

    @Override
    public boolean isFreed() {
        return freed;
    }

    @Override
    public ByteBufHolder copy() {
        return new DefaultByteBufHolder(data().copy());
    }

    @Override
    public String toString() {
        if (isFreed()) {
            return "Message{data=(FREED)}";
        }
        return "Message{data=" + ByteBufUtil.hexDump(data()) + '}';
    }
}
