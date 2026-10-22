/*
 * Copyright 2026 The Netty Project
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
package io.netty.channel.unix;

import java.nio.ByteBuffer;

/**
 * A {@link GenericControlMessage} which uses an {@link Integer} as value.
 * <p>
 * When sending, the value is encoded as {@code int}. When receiving, data of the size of an {@code int},
 * an {@code uint16_t} or an {@code unsigned char} is accepted.
 */
public final class IntegerControlMessage extends GenericControlMessage<Integer> {

    /**
     * Creates a new instance.
     *
     * @param name  the name that is used.
     * @param level the {@code cmsg_level}.
     * @param type  the {@code cmsg_type}.
     */
    public IntegerControlMessage(String name, int level, int type) {
        super(name, level, type);
    }

    @Override
    int dataLength(Integer value) {
        return 4;
    }

    @Override
    void encode(Integer value, ByteBuffer buffer, int offset) {
        buffer.putInt(offset, value);
    }

    @Override
    int maxDataLength() {
        return 4;
    }

    @Override
    Integer decode(ByteBuffer buffer, int offset, int length) {
        switch (length) {
            case 4:
                return buffer.getInt(offset);
            case 2:
                return buffer.getShort(offset) & 0xFFFF;
            case 1:
                return buffer.get(offset) & 0xFF;
            default:
                return null;
        }
    }
}
