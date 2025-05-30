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

import io.netty.util.AbstractReferenceCounted;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.UnstableApi;

import java.util.Collections;
import java.util.Map;

@UnstableApi
public abstract class AbstractMapRedisMessage extends AbstractReferenceCounted
        implements AggregatedRedisMessage {

    private final Map<RedisMessage, RedisMessage> children;

    protected AbstractMapRedisMessage(Map<RedisMessage, RedisMessage> children) {
        this.children = Collections.unmodifiableMap(children);
    }

    /**
     * Get children of this Map. It can be null or empty.
     *
     * @return Map of {@link RedisMessage}s.
     */
    public Map<RedisMessage, RedisMessage> children() {
        return children;
    }

    @Override
    protected void deallocate() {
        for (Map.Entry<RedisMessage, RedisMessage> messageEntry : children.entrySet()) {
            ReferenceCountUtil.release(messageEntry.getKey());
            ReferenceCountUtil.release(messageEntry.getValue());
        }
    }

    @Override
    public AbstractMapRedisMessage touch(Object hint) {
        for (Map.Entry<RedisMessage, RedisMessage> messageEntry : children.entrySet()) {
            ReferenceCountUtil.touch(messageEntry.getKey());
            ReferenceCountUtil.touch(messageEntry.getValue());
        }
        return this;
    }

    /**
     * Returns whether the content of this message is {@code null}.
     *
     * @return indicates whether the content of this message is {@code null}.
     */
    public boolean isNull() {
        return false;
    }

    @Override
    public String toString() {
        return new StringBuilder(StringUtil.simpleClassName(this))
                .append('[')
                .append("children=")
                .append(children.size())
                .append(']').toString();
    }

}
