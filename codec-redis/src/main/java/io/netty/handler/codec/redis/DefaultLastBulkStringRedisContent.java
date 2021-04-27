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
 * A default implementation for {@link LastBulkStringRedisContent}.
 */
@UnstableApi
public final class DefaultLastBulkStringRedisContent extends DefaultBulkStringRedisContent
        implements LastBulkStringRedisContent {

    /**
     * Creates a {@link DefaultLastBulkStringRedisContent} for the given {@code content}.
     * @param content the content, can be {@code null}.
     */
    public DefaultLastBulkStringRedisContent(ByteBuf content) {
        super(content);
    }

    @Override
    public LastBulkStringRedisContent copy() {
        return (LastBulkStringRedisContent) super.copy();
    }

    @Override
    public LastBulkStringRedisContent duplicate() {
        return (LastBulkStringRedisContent) super.duplicate();
    }

    @Override
    public LastBulkStringRedisContent retainedDuplicate() {
        return (LastBulkStringRedisContent) super.retainedDuplicate();
    }

    @Override
    public LastBulkStringRedisContent replace(ByteBuf content) {
        return new DefaultLastBulkStringRedisContent(content);
    }

    @Override
    public LastBulkStringRedisContent retain() {
        super.retain();
        return this;
    }

    @Override
    public LastBulkStringRedisContent retain(int increment) {
        super.retain(increment);
        return this;
    }

    @Override
    public LastBulkStringRedisContent touch() {
        super.touch();
        return this;
    }

    @Override
    public LastBulkStringRedisContent touch(Object hint) {
        super.touch(hint);
        return this;
    }
}
