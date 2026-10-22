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

/**
 * A {@link NativeControlMessage} which carries an integer value within a given range, like {@code IP_TOS}.
 */
final class NativeIntegerControlMessage extends NativeControlMessage<Integer> {

    private final int min;
    private final int max;

    NativeIntegerControlMessage(String name, int min, int max) {
        super(name);
        this.min = min;
        this.max = max;
    }

    @Override
    public void validate(Integer value) {
        super.validate(value);
        ObjectUtil.checkInRange(value.intValue(), min, max, "value");
    }

    @Override
    int dataLength(Integer value) {
        return descriptor().sendDataLength;
    }

    @Override
    void encode(Integer value, ByteBuffer buffer, int offset) {
        if (descriptor().sendDataLength == 1) {
            buffer.put(offset, value.byteValue());
        } else {
            buffer.putInt(offset, value);
        }
    }

    @Override
    int maxDataLength() {
        return 4;
    }

    @Override
    Integer decode(ByteBuffer buffer, int offset, int length) {
        // Depending on the platform and the control message the value is either an int or an unsigned char.
        if (length == 4) {
            return buffer.getInt(offset);
        }
        if (length == 1) {
            return buffer.get(offset) & 0xFF;
        }
        return null;
    }
}
