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
package io.netty.channel;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;

/**
 * Default implementation of a {@link Packet} that holds it's data in a {@link ByteBuf}.
 *
 */
public class DefaultPacket implements Packet {
    private final ByteBuf data;

    public DefaultPacket(ByteBuf data) {
        if (data == null) {
            throw new NullPointerException("data");
        }
        this.data = data;
    }

    @Override
    public ByteBuf data() {
        return data;
    }

    @Override
    public void free() {
        if (!data.isFreed()) {
            data.free();
        }
    }

    @Override
    public Packet copy() {
        return new DefaultPacket(data().copy());
    }

    @Override
    public String toString() {
        return "packet(" + ByteBufUtil.hexDump(data()) + ')';
    }
}
