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
 * The header of Bulk Strings in <a href="http://redis.io/topics/protocol">RESP</a>.
 */
@UnstableApi
public class BulkStringHeaderRedisMessage implements RedisMessage {

    private final int bulkStringLength;

    /**
     * Creates a {@link BulkStringHeaderRedisMessage}.
     *
     * @param bulkStringLength follow content length.
     */
    public BulkStringHeaderRedisMessage(int bulkStringLength) {
        if (bulkStringLength <= 0) {
            throw new RedisCodecException("bulkStringLength: " + bulkStringLength + " (expected: > 0)");
        }
        this.bulkStringLength = bulkStringLength;
    }

    /**
     * Return {@code bulkStringLength} for this content.
     */
    public final int bulkStringLength() {
        return bulkStringLength;
    }

    /**
     * Returns whether the content of this message is {@code null}.
     *
     * @return indicates whether the content of this message is {@code null}.
     */
    public boolean isNull() {
        return bulkStringLength == RedisConstants.NULL_VALUE;
    }
}
