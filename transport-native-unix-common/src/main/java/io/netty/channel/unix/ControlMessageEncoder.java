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
 * Encodes {@link ControlMessages} so these can be passed to {@code sendmsg(...)} as {@code msg_control}.
 * <p>
 * <strong>Internal usage only!</strong>
 */
public final class ControlMessageEncoder {

    private ControlMessageEncoder() { }

    /**
     * Returns the number of bytes that are needed to encode the given {@link ControlMessages}.
     *
     * @throws UnsupportedOperationException if one of the {@link ControlMessage}s can't be sent on this platform.
     */
    public static int encodedLength(ControlMessages controlMessages) {
        return encodedLength(ControlMessageConstants.LAYOUT, controlMessages);
    }

    /**
     * Encodes the given {@link ControlMessages} into the given direct {@link ByteBuffer}, starting at the given
     * offset. The {@link ByteBuffer} must use the native byte order.
     *
     * @return the number of bytes that were written, which is the same as {@link #encodedLength(ControlMessages)}.
     * @throws UnsupportedOperationException if one of the {@link ControlMessage}s can't be sent on this platform.
     */
    public static int encode(ControlMessages controlMessages, ByteBuffer buffer, int offset) {
        return encode(ControlMessageConstants.LAYOUT, controlMessages, buffer, offset);
    }

    /**
     * Returns the number of bytes a control message with the given data length occupies, which is the same as
     * {@code CMSG_SPACE(dataLength)}.
     */
    public static int space(int dataLength) {
        return ControlMessageConstants.LAYOUT.space(ObjectUtil.checkPositiveOrZero(dataLength, "dataLength"));
    }

    @SuppressWarnings("unchecked")
    static int encodedLength(ControlMessageLayout layout, ControlMessages controlMessages) {
        int length = 0;
        for (int i = 0; i < controlMessages.size(); i++) {
            ControlMessage<Object> type = (ControlMessage<Object>) controlMessages.type(i);
            checkSendSupported(type);
            length += layout.space(type.dataLength(controlMessages.value(i)));
        }
        return length;
    }

    @SuppressWarnings("unchecked")
    static int encode(ControlMessageLayout layout, ControlMessages controlMessages, ByteBuffer buffer, int offset) {
        int position = offset;
        for (int i = 0; i < controlMessages.size(); i++) {
            ControlMessage<Object> type = (ControlMessage<Object>) controlMessages.type(i);
            checkSendSupported(type);
            Object value = controlMessages.value(i);
            int dataLength = type.dataLength(value);
            layout.writeHeader(buffer, position, type.sendLevel(), type.sendType(), dataLength);
            type.encode(value, buffer, position + layout.dataOffset());
            position += layout.space(dataLength);
        }
        return position - offset;
    }

    private static void checkSendSupported(ControlMessage<?> type) {
        if (!type.isSendSupported()) {
            throw new UnsupportedOperationException("Sending " + type + " is not supported on this platform");
        }
    }
}
