/*
 * Copyright 2016 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.netty.handler.codec.redis;

import io.netty.buffer.ByteBuf;
import io.netty.util.internal.UnstableApi;

/**
 * Type of <a href="https://redis.io/topics/protocol">RESP (REdis Serialization Protocol)</a>.
 */
@UnstableApi
public enum RedisMessageType {

    INLINE_COMMAND(null, true),
    SIMPLE_STRING((byte) '+', true),
    ERROR((byte) '-', true),
    INTEGER((byte) ':', true),
    BULK_STRING((byte) '$', false),
    ARRAY_HEADER((byte) '*', false);

    private final Byte value;
    private final boolean inline;

    RedisMessageType(Byte value, boolean inline) {
        this.value = value;
        this.inline = inline;
    }

    /**
     * Returns length of this type.
     */
    public int length() {
        return value != null ? RedisConstants.TYPE_LENGTH : 0;
    }

    /**
     * Returns {@code true} if this type is inline type, or returns {@code false}. If this is {@code true},
     * this type doesn't have length field.
     */
    public boolean isInline() {
        return inline;
    }

    /**
     * Determine {@link RedisMessageType} based on the type prefix {@code byte} read from given the buffer.
     */
    public static RedisMessageType readFrom(ByteBuf in, boolean decodeInlineCommands) {
        final int initialIndex = in.readerIndex();
        final RedisMessageType type = valueOf(in.readByte());
        if (type == INLINE_COMMAND) {
            if (!decodeInlineCommands) {
                throw new RedisCodecException("Decoding of inline commands is disabled");
            }
            // reset index to make content readable again
            in.readerIndex(initialIndex);
        }
        return type;
    }

    /**
     * Write the message type's prefix to the given buffer.
     */
    public void writeTo(ByteBuf out) {
        if (value == null) {
            return;
        }
        out.writeByte(value.byteValue());
    }

    private static RedisMessageType valueOf(byte value) {
        switch (value) {
        case '+':
            return SIMPLE_STRING;
        case '-':
            return ERROR;
        case ':':
            return INTEGER;
        case '$':
            return BULK_STRING;
        case '*':
            return ARRAY_HEADER;
        default:
            return INLINE_COMMAND;
        }
    }
}
