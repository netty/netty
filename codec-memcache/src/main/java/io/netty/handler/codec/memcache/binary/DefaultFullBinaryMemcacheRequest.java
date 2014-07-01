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

/**
 * The default implementation of a {@link FullBinaryMemcacheRequest}.
 */
public class DefaultFullBinaryMemcacheRequest extends DefaultBinaryMemcacheRequest
    implements FullBinaryMemcacheRequest {

    private final ByteBuf content;

    /**
     * Create a new {@link DefaultBinaryMemcacheRequest} with the header, key and extras.
     *
     * @param key    the key to use.
     * @param extras the extras to use.
     */
    public DefaultFullBinaryMemcacheRequest(String key, ByteBuf extras) {
        this(key, extras, Unpooled.buffer(0));
    }

    /**
     * Create a new {@link DefaultBinaryMemcacheRequest} with the header, key, extras and content.
     *
     * @param key     the key to use.
     * @param extras  the extras to use.
     * @param content the content of the full request.
     */
    public DefaultFullBinaryMemcacheRequest(String key, ByteBuf extras,
                                            ByteBuf content) {
        super(key, extras);
        if (content == null) {
            throw new NullPointerException("Supplied content is null.");
        }

        this.content = content;
    }

    @Override
    public ByteBuf content() {
        return content;
    }

    @Override
    public int refCnt() {
        return content.refCnt();
    }

    @Override
    public FullBinaryMemcacheRequest retain() {
        super.retain();
        content.retain();
        return this;
    }

    @Override
    public FullBinaryMemcacheRequest retain(int increment) {
        super.retain(increment);
        content.retain(increment);
        return this;
    }

    @Override
    public FullBinaryMemcacheRequest touch() {
        super.touch();
        content.touch();
        return this;
    }

    @Override
    public FullBinaryMemcacheRequest touch(Object hint) {
        super.touch(hint);
        content.touch(hint);
        return this;
    }

    @Override
    public boolean release() {
        super.release();
        return content.release();
    }

    @Override
    public boolean release(int decrement) {
        super.release(decrement);
        return content.release(decrement);
    }

    @Override
    public FullBinaryMemcacheRequest copy() {
        ByteBuf extras = extras();
        if (extras != null) {
            extras = extras.copy();
        }
        return new DefaultFullBinaryMemcacheRequest(key(), extras, content().copy());
    }

    @Override
    public FullBinaryMemcacheRequest duplicate() {
        ByteBuf extras = extras();
        if (extras != null) {
            extras = extras.duplicate();
        }
        return new DefaultFullBinaryMemcacheRequest(key(), extras, content().duplicate());
    }
}
