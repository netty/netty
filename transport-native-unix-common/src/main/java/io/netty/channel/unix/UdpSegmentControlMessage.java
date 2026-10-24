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
 * The {@link NativeControlMessage} for {@code UDP_SEGMENT}, which carries the segment size as {@code uint16_t}.
 */
final class UdpSegmentControlMessage extends NativeControlMessage<Integer> {

    UdpSegmentControlMessage() {
        super("UDP_SEGMENT");
    }

    @Override
    public void validate(Integer value) {
        super.validate(value);
        ObjectUtil.checkInRange(value.intValue(), 1, 0xFFFF, "value");
    }

    @Override
    int dataLength(Integer value) {
        return descriptor().sendDataLength;
    }

    @Override
    void encode(Integer value, ByteBuffer buffer, int offset) {
        buffer.putShort(offset, value.shortValue());
    }

    @Override
    int maxDataLength() {
        // Can't be received.
        return 0;
    }

    @Override
    Integer decode(ByteBuffer buffer, int offset, int length) {
        // Can't be received.
        return null;
    }
}
