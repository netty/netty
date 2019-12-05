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
package io.netty.handler.codec.memcache.binary;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.UnstableApi;

/**
 * The default implementation of a {@link FullBinaryMemcacheRequest}.
 */
@UnstableApi
public class DefaultFullBinaryMemcacheRequest extends DefaultBinaryMemcacheRequest
    implements FullBinaryMemcacheRequest {

    private final ByteBuf content;

    /**
     * Create a new {@link DefaultBinaryMemcacheRequest} with the header, key and extras.
     *
     * @param key    the key to use.
     * @param extras the extras to use.
     */
    public DefaultFullBinaryMemcacheRequest(ByteBuf key, ByteBuf extras) {
        this(key, extras, Unpooled.buffer(0));
    }

    /**
     * Create a new {@link DefaultBinaryMemcacheRequest} with the header, key, extras and content.
     *
     * @param key     the key to use.
     * @param extras  the extras to use.
     * @param content the content of the full request.
     */
    public DefaultFullBinaryMemcacheRequest(ByteBuf key, ByteBuf extras,
                                            ByteBuf content) {
        super(key, extras);
        this.content = ObjectUtil.checkNotNull(content, "content");
        setTotalBodyLength(keyLength() + extrasLength() + content.readableBytes());
    }

    @Override
    public ByteBuf content() {
        return content;
    }

    @Override
    public FullBinaryMemcacheRequest retain() {
        super.retain();
        return this;
    }

    @Override
    public FullBinaryMemcacheRequest retain(int increment) {
        super.retain(increment);
        return this;
    }

    @Override
    public FullBinaryMemcacheRequest touch() {
        super.touch();
        return this;
    }

    @Override
    public FullBinaryMemcacheRequest touch(Object hint) {
        super.touch(hint);
        content.touch(hint);
        return this;
    }

    @Override
    protected void deallocate() {
        super.deallocate();
        content.release();
    }

    @Override
    public FullBinaryMemcacheRequest copy() {
        ByteBuf key = key();
        if (key != null) {
            key = key.copy();
        }
        ByteBuf extras = extras();
        if (extras != null) {
            extras = extras.copy();
        }
        return newInstance(key, extras, content().copy());
    }

    @Override
    public FullBinaryMemcacheRequest duplicate() {
        ByteBuf key = key();
        if (key != null) {
            key = key.duplicate();
        }
        ByteBuf extras = extras();
        if (extras != null) {
            extras = extras.duplicate();
        }
        return newInstance(key, extras, content().duplicate());
    }

    @Override
    public FullBinaryMemcacheRequest retainedDuplicate() {
        return replace(content().retainedDuplicate());
    }

    @Override
    public FullBinaryMemcacheRequest replace(ByteBuf content) {
        ByteBuf key = key();
        if (key != null) {
            key = key.retainedDuplicate();
        }
        ByteBuf extras = extras();
        if (extras != null) {
            extras = extras.retainedDuplicate();
        }
        return newInstance(key, extras, content);
    }

    private DefaultFullBinaryMemcacheRequest newInstance(ByteBuf key, ByteBuf extras, ByteBuf content) {
        DefaultFullBinaryMemcacheRequest newInstance = new DefaultFullBinaryMemcacheRequest(key, extras, content);
        copyMeta(newInstance);
        return newInstance;
    }
}
