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

import io.netty.util.internal.ObjectUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * A {@link GenericControlMessage} which uses a {@link ByteBuffer} as value, which contains the raw data of the
 * control message. The user is responsible to fill the {@link ByteBuffer} in a correct manner, so it matches what is
 * expected for the {@code cmsg_level} and {@code cmsg_type}.
 * <p>
 * When sending, the {@link ByteBuffer#remaining()} bytes of the {@link ByteBuffer} are used as data, without
 * modifying the position of the {@link ByteBuffer}.
 * When receiving, the value is a read-only {@link ByteBuffer} that uses the {@link ByteOrder#nativeOrder()}.
 */
public final class RawControlMessage extends GenericControlMessage<ByteBuffer> {

    private final int maxDataLength;

    /**
     * Creates a new instance.
     *
     * @param name          the name that is used.
     * @param level         the {@code cmsg_level}.
     * @param type          the {@code cmsg_type}.
     * @param maxDataLength the maximal length of the data. When receiving, this many bytes will be reserved to
     *                      receive the control message.
     */
    public RawControlMessage(String name, int level, int type, int maxDataLength) {
        super(name, level, type);
        this.maxDataLength = ObjectUtil.checkPositive(maxDataLength, "maxDataLength");
    }

    /**
     * Returns the maximal length of the data of the control message.
     *
     * @return the maximal length.
     */
    public int maxDataLength() {
        return maxDataLength;
    }

    @Override
    public void validate(ByteBuffer value) {
        super.validate(value);
        if (value.remaining() > maxDataLength) {
            throw new IllegalArgumentException("Length of value exceeds the maximal length. Expected <= "
                    + maxDataLength + ", but got " + value.remaining());
        }
    }

    @Override
    int dataLength(ByteBuffer value) {
        return value.remaining();
    }

    @Override
    void encode(ByteBuffer value, ByteBuffer buffer, int offset) {
        int position = value.position();
        int length = value.remaining();
        for (int i = 0; i < length; i++) {
            buffer.put(offset + i, value.get(position + i));
        }
    }

    @Override
    ByteBuffer decode(ByteBuffer buffer, int offset, int length) {
        // The data might be truncated, in which case we just return what we got.
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            bytes[i] = buffer.get(offset + i);
        }
        return ByteBuffer.wrap(bytes).asReadOnlyBuffer().order(ByteOrder.nativeOrder());
    }
}
