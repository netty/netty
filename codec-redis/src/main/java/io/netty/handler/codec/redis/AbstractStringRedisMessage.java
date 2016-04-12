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

import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.UnstableApi;

/**
 * Abstract class for Simple Strings or Errors.
 */
@UnstableApi
public abstract class AbstractStringRedisMessage implements RedisMessage {

    private final String content;

    AbstractStringRedisMessage(String content) {
        this.content = ObjectUtil.checkNotNull(content, "content");
    }

    /**
     * Get string content of this {@link AbstractStringRedisMessage}.
     *
     * @return content of this message.
     */
    public final String content() {
        return content;
    }
}
