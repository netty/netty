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
import io.netty.handler.codec.memcache.DefaultMemcacheObject;

/**
 * Default implementation of a {@link BinaryMemcacheMessage}.
 */
public abstract class DefaultBinaryMemcacheMessage<H extends BinaryMemcacheMessageHeader>
    extends DefaultMemcacheObject
    implements BinaryMemcacheMessage<H> {

    /**
     * Contains the message header.
     */
    private final H header;

    /**
     * Contains the optional key.
     */
    private final String key;

    /**
     * Contains the optional extras.
     */
    private final ByteBuf extras;

    /**
     * Create a new instance with all properties set.
     *
     * @param header the message header.
     * @param key    the message key.
     * @param extras the message extras.
     */
    protected DefaultBinaryMemcacheMessage(H header, String key, ByteBuf extras) {
        this.header = header;
        this.key = key;
        this.extras = extras;
    }

    @Override
    public H getHeader() {
        return header;
    }

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public ByteBuf getExtras() {
        return extras;
    }

    @Override
    public int refCnt() {
        if (extras != null) {
            return extras.refCnt();
        }
        return 1;
    }

    @Override
    public BinaryMemcacheMessage<H> retain() {
        if (extras != null) {
            extras.retain();
        }
        return this;
    }

    @Override
    public BinaryMemcacheMessage<H> retain(int increment) {
        if (extras != null) {
            extras.retain(increment);
        }
        return this;
    }

    @Override
    public boolean release() {
        if (extras != null) {
            return extras.release();
        }
        return false;
    }

    @Override
    public boolean release(int decrement) {
        if (extras != null) {
            return extras.release(decrement);
        }
        return false;
    }
}
