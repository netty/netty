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
 * Describes the layout of {@code struct cmsghdr} and implements the {@code CMSG_*} macros of the platform, so control
 * messages can be encoded and decoded in Java.
 *
 * <pre>{@code
 * struct cmsghdr {
 *     socklen_t cmsg_len;    // data byte count, including header (size_t on linux)
 *     int       cmsg_level;  // originating protocol
 *     int       cmsg_type;   // protocol-specific type
 *     // followed by unsigned char cmsg_data[];
 * };
 * }</pre>
 */
final class ControlMessageLayout {

    private final int headerSize;
    private final int lenOffset;
    private final int lenSize;
    private final int levelOffset;
    private final int typeOffset;
    private final int dataOffset;
    private final int alignment;

    /**
     * Creates a new instance.
     *
     * @param headerSize    {@code sizeof(struct cmsghdr)}.
     * @param lenOffset     {@code offsetof(struct cmsghdr, cmsg_len)}.
     * @param lenSize       the size of {@code cmsg_len}.
     * @param levelOffset   {@code offsetof(struct cmsghdr, cmsg_level)}.
     * @param typeOffset    {@code offsetof(struct cmsghdr, cmsg_type)}.
     * @param dataOffset    {@code CMSG_LEN(0)}, which is the offset of the data.
     * @param alignment     the alignment used by {@code CMSG_ALIGN(...)}.
     */
    ControlMessageLayout(int headerSize, int lenOffset, int lenSize, int levelOffset, int typeOffset,
                         int dataOffset, int alignment) {
        if (lenSize != 4 && lenSize != 8) {
            throw new IllegalArgumentException("lenSize: " + lenSize + " (expected: 4 or 8)");
        }
        if (alignment <= 0 || (alignment & (alignment - 1)) != 0) {
            throw new IllegalArgumentException("alignment: " + alignment + " (expected: power of two)");
        }
        if (dataOffset < headerSize) {
            throw new IllegalArgumentException(
                    "dataOffset: " + dataOffset + " (expected: >= headerSize(" + headerSize + "))");
        }
        this.headerSize = headerSize;
        this.lenOffset = lenOffset;
        this.lenSize = lenSize;
        this.levelOffset = levelOffset;
        this.typeOffset = typeOffset;
        this.dataOffset = dataOffset;
        this.alignment = alignment;
    }

    /**
     * Returns {@code sizeof(struct cmsghdr)}.
     */
    int headerSize() {
        return headerSize;
    }

    /**
     * Returns the offset of the data of a control message, which is the same as {@code CMSG_LEN(0)}.
     */
    int dataOffset() {
        return dataOffset;
    }

    /**
     * See {@code CMSG_ALIGN(...)}.
     */
    int align(int length) {
        return (length + alignment - 1) & -alignment;
    }

    /**
     * See {@code CMSG_SPACE(...)}, which is the number of bytes a control message with the given data length
     * occupies (including the header and any padding).
     */
    int space(int dataLength) {
        return dataOffset + align(dataLength);
    }

    /**
     * See {@code CMSG_LEN(...)}, which is the value that is stored in {@code cmsg_len}.
     */
    int length(int dataLength) {
        return dataOffset + dataLength;
    }

    /**
     * Write the header of a control message to the given offset. All the padding, as well as the data, will be
     * zeroed out.
     */
    void writeHeader(ByteBuffer buffer, int offset, int level, int type, int dataLength) {
        int space = space(dataLength);
        int i = 0;
        for (; i + 8 <= space; i += 8) {
            buffer.putLong(offset + i, 0);
        }
        for (; i < space; i++) {
            buffer.put(offset + i, (byte) 0);
        }
        int length = length(dataLength);
        if (lenSize == 8) {
            buffer.putLong(offset + lenOffset, length);
        } else {
            buffer.putInt(offset + lenOffset, length);
        }
        buffer.putInt(offset + levelOffset, level);
        buffer.putInt(offset + typeOffset, type);
    }

    /**
     * Returns the value of {@code cmsg_len} of the control message at the given offset.
     */
    long cmsgLen(ByteBuffer buffer, int offset) {
        if (lenSize == 8) {
            return buffer.getLong(offset + lenOffset);
        }
        return buffer.getInt(offset + lenOffset) & 0xFFFFFFFFL;
    }

    /**
     * Returns the value of {@code cmsg_level} of the control message at the given offset.
     */
    int cmsgLevel(ByteBuffer buffer, int offset) {
        return buffer.getInt(offset + levelOffset);
    }

    /**
     * Returns the value of {@code cmsg_type} of the control message at the given offset.
     */
    int cmsgType(ByteBuffer buffer, int offset) {
        return buffer.getInt(offset + typeOffset);
    }
}
