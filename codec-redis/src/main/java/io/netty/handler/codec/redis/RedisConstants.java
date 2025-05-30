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

/**
 * Constant values for Redis encoder/decoder.
 */
final class RedisConstants {

    private RedisConstants() {
    }

    static final int TYPE_LENGTH = 1;

    static final int EOL_LENGTH = 2;

    static final int NULL_LENGTH = 2;

    static final int NULL_VALUE = -1;

    static final int BOOLEAN_LENGTH = 1;

    static final byte BOOLEAN_TRUE_CONTENT = 't';

    static final byte BOOLEAN_FALSE_CONTENT = 'f';

    static final String DOUBLE_POSITIVE_INF_CONTENT = "inf";

    static final String DOUBLE_NEGATIVE_INF_CONTENT = "-inf";

    static final int REDIS_MESSAGE_MAX_LENGTH = 512 * 1024 * 1024; // 512MB

    // 64KB is max inline length of current Redis server implementation.
    static final int REDIS_INLINE_MESSAGE_MAX_LENGTH = 64 * 1024;

    static final int POSITIVE_LONG_MAX_LENGTH = 19; // length of Long.MAX_VALUE

    static final int POSITIVE_DOUBLE_MAX_LENGTH = 16; // length of DOUBLE.MAX_VALUE

    static final int LONG_MAX_LENGTH = POSITIVE_LONG_MAX_LENGTH + 1; // +1 is sign

    static final int DOUBLE_MAX_LENGTH = POSITIVE_DOUBLE_MAX_LENGTH + 1; // +1 is sign

    static final short NULL_SHORT = RedisCodecUtil.makeShort('-', '1');

    static final short EOL_SHORT = RedisCodecUtil.makeShort('\r', '\n');
}
