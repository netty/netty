/*
 * Copyright 2023 The Netty Project
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
import java.util.Arrays;

public final class ControlMessage {

    /* Set GSO segmentation size */
    private static final int UDP_SEGMENT = 103;
    private static final int SOL_UDP = 17;

    private final int level;
    private final int type;
    private final byte[] data;

    private ControlMessage(int level, int type, byte[] data) {
        this.level = level;
        this.type = type;
        this.data = data;
    }

    public int length() {
        return data().remaining();
    }

    public int level() {
        return level;
    }

    public int type() {
        return type;
    }

    public ByteBuffer data() {
        return ByteBuffer.wrap(data);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ControlMessage that = (ControlMessage) o;
        if (level != that.level) {
            return false;
        }
        if (type != that.type) {
            return false;
        }
        return Arrays.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        int result = level;
        result = 31 * result + type;
        result = 31 * result + Arrays.hashCode(data);
        return result;
    }

    public static ControlMessage udpSegment(int segmentSize) {
        ByteBuffer buffer = Buffer.toNativeOrder(ByteBuffer.wrap(new byte[4]));
        buffer.putInt(segmentSize).flip();
        return new ControlMessage(SOL_UDP, UDP_SEGMENT, buffer.array());
    }
}
