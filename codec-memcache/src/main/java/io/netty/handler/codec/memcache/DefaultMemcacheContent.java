/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.memcache;

import io.netty.buffer.ByteBuf;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.UnstableApi;

/**
 * The default {@link MemcacheContent} implementation.
 */
@UnstableApi
public class DefaultMemcacheContent extends AbstractMemcacheObject implements MemcacheContent {

    private final ByteBuf content;

    /**
     * Creates a new instance with the specified content.
     */
    public DefaultMemcacheContent(ByteBuf content) {
        this.content = ObjectUtil.checkNotNull(content, "content");
    }

    @Override
    public ByteBuf content() {
        return content;
    }

    @Override
    public MemcacheContent copy() {
        return replace(content.copy());
    }

    @Override
    public MemcacheContent duplicate() {
        return replace(content.duplicate());
    }

    @Override
    public MemcacheContent retainedDuplicate() {
        return replace(content.retainedDuplicate());
    }

    @Override
    public MemcacheContent replace(ByteBuf content) {
        return new DefaultMemcacheContent(content);
    }

    @Override
    public MemcacheContent retain() {
        super.retain();
        return this;
    }

    @Override
    public MemcacheContent retain(int increment) {
        super.retain(increment);
        return this;
    }

    @Override
    public MemcacheContent touch() {
        super.touch();
        return this;
    }

    @Override
    public MemcacheContent touch(Object hint) {
        content.touch(hint);
        return this;
    }

    @Override
    protected void deallocate() {
        content.release();
    }

    @Override
    public String toString() {
        return StringUtil.simpleClassName(this) +
               "(data: " + content() + ", decoderResult: " + decoderResult() + ')';
    }
}
