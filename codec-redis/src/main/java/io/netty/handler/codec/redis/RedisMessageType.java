/*
 * Copyright 2016 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.netty.handler.codec.redis;

import io.netty.util.internal.UnstableApi;

/**
 * Type of <a href="http://redis.io/topics/protocol">RESP (REdis Serialization Protocol)</a>.
 */
@UnstableApi
public enum RedisMessageType {

    SIMPLE_STRING((byte) '+', true),
    ERROR((byte) '-', true),
    INTEGER((byte) ':', true),
    BULK_STRING((byte) '$', false),
    ARRAY_HEADER((byte) '*', false),
    ARRAY((byte) '*', false); // for aggregated

    private final byte value;
    private final boolean inline;

    RedisMessageType(byte value, boolean inline) {
        this.value = value;
        this.inline = inline;
    }

    /**
     * Returns prefix {@code byte} for this type.
     */
    public byte value() {
        return value;
    }

    /**
     * Returns {@code true} if this type is inline type, or returns {@code false}. If this is {@code true},
     * this type doesn't have length field.
     */
    public boolean isInline() {
        return inline;
    }

    /**
     * Return {@link RedisMessageType} for this type prefix {@code byte}.
     */
    public static RedisMessageType valueOf(byte value) {
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
            throw new RedisCodecException("Unknown RedisMessageType: " + value);
        }
    }
}
