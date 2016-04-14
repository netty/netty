/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.netty.handler.codec.memcache;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.internal.UnstableApi;

/**
 * The default implementation for the {@link LastMemcacheContent}.
 */
@UnstableApi
public class DefaultLastMemcacheContent extends DefaultMemcacheContent implements LastMemcacheContent {

    public DefaultLastMemcacheContent() {
        super(Unpooled.buffer());
    }

    public DefaultLastMemcacheContent(ByteBuf content) {
        super(content);
    }

    @Override
    public LastMemcacheContent retain() {
        super.retain();
        return this;
    }

    @Override
    public LastMemcacheContent retain(int increment) {
        super.retain(increment);
        return this;
    }

    @Override
    public LastMemcacheContent touch() {
        super.touch();
        return this;
    }

    @Override
    public LastMemcacheContent touch(Object hint) {
        super.touch(hint);
        return this;
    }

    @Override
    public LastMemcacheContent copy() {
        return replace(content().copy());
    }

    @Override
    public LastMemcacheContent duplicate() {
        return replace(content().duplicate());
    }

    @Override
    public LastMemcacheContent retainedDuplicate() {
        return replace(content().retainedDuplicate());
    }

    @Override
    public LastMemcacheContent replace(ByteBuf content) {
        return new DefaultLastMemcacheContent(content);
    }
}
