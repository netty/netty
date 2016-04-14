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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.DefaultByteBufHolder;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.UnstableApi;

/**
 * A default implementation of {@link BulkStringRedisContent}.
 */
@UnstableApi
public class DefaultBulkStringRedisContent extends DefaultByteBufHolder implements BulkStringRedisContent {

    /**
     * Creates a {@link DefaultBulkStringRedisContent} for the given {@code content}.
     *
     * @param content the content, can be {@code null}.
     */
    public DefaultBulkStringRedisContent(ByteBuf content) {
        super(content);
    }

    @Override
    public BulkStringRedisContent copy() {
        return (BulkStringRedisContent) super.copy();
    }

    @Override
    public BulkStringRedisContent duplicate() {
        return (BulkStringRedisContent) super.duplicate();
    }

    @Override
    public BulkStringRedisContent retainedDuplicate() {
        return (BulkStringRedisContent) super.retainedDuplicate();
    }

    @Override
    public BulkStringRedisContent replace(ByteBuf content) {
        return new DefaultBulkStringRedisContent(content);
    }

    @Override
    public BulkStringRedisContent retain() {
        super.retain();
        return this;
    }

    @Override
    public BulkStringRedisContent retain(int increment) {
        super.retain(increment);
        return this;
    }

    @Override
    public BulkStringRedisContent touch() {
        super.touch();
        return this;
    }

    @Override
    public BulkStringRedisContent touch(Object hint) {
        super.touch(hint);
        return this;
    }

    @Override
    public String toString() {
        return new StringBuilder(StringUtil.simpleClassName(this))
                .append('[')
                .append("content=")
                .append(content())
                .append(']').toString();
    }
}
