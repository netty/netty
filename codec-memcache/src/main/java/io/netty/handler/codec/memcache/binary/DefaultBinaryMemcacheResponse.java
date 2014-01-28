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

/**
 * The default implementation of the {@link BinaryMemcacheResponse}.
 */
public class DefaultBinaryMemcacheResponse extends AbstractBinaryMemcacheMessage<BinaryMemcacheResponseHeader>
    implements BinaryMemcacheResponse {

    /**
     * Create a new {@link DefaultBinaryMemcacheResponse} with the header only.
     *
     * @param header the header to use.
     */
    public DefaultBinaryMemcacheResponse(BinaryMemcacheResponseHeader header) {
        this(header, null, null);
    }

    /**
     * Create a new {@link DefaultBinaryMemcacheResponse} with the header and key.
     *
     * @param header the header to use.
     * @param key    the key to use
     */
    public DefaultBinaryMemcacheResponse(BinaryMemcacheResponseHeader header, String key) {
        this(header, key, null);
    }

    /**
     * Create a new {@link DefaultBinaryMemcacheResponse} with the header and extras.
     *
     * @param header the header to use.
     * @param extras the extras to use.
     */
    public DefaultBinaryMemcacheResponse(BinaryMemcacheResponseHeader header, ByteBuf extras) {
        this(header, null, extras);
    }

    /**
     * Create a new {@link DefaultBinaryMemcacheResponse} with the header, key and extras.
     *
     * @param header the header to use.
     * @param key    the key to use.
     * @param extras the extras to use.
     */
    public DefaultBinaryMemcacheResponse(BinaryMemcacheResponseHeader header, String key, ByteBuf extras) {
        super(header, key, extras);
    }

    @Override
    public BinaryMemcacheResponse retain() {
        super.retain();
        return this;
    }

    @Override
    public BinaryMemcacheResponse retain(int increment) {
        super.retain(increment);
        return this;
    }

    @Override
    public BinaryMemcacheResponse touch() {
        super.touch();
        return this;
    }
}
