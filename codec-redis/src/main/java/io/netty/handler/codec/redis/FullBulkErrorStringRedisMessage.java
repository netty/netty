/*
 * Copyright 2021 The Netty Project
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

public class FullBulkErrorStringRedisMessage extends FullBulkStringRedisMessage {

    /**
     * Creates a {@link FullBulkErrorStringRedisMessage} for the given {@code content}.
     *
     * @param content the content, must not be {@code null}. If content is null or empty,
     *                use {@link NullRedisMessage#INSTANCE instead of constructor.
     */
    public FullBulkErrorStringRedisMessage(ByteBuf content) {
        super(content);
    }
}
