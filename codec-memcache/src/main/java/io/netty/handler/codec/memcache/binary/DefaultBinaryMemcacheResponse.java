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
import io.netty.util.internal.UnstableApi;

/**
 * The default implementation of the {@link BinaryMemcacheResponse}.
 */
@UnstableApi
public class DefaultBinaryMemcacheResponse extends AbstractBinaryMemcacheMessage implements BinaryMemcacheResponse {

    /**
     * Default magic byte for a request.
     */
    public static final byte RESPONSE_MAGIC_BYTE = (byte) 0x81;

    private short status;

    /**
     * Create a new {@link DefaultBinaryMemcacheResponse} with the header only.
     */
    public DefaultBinaryMemcacheResponse() {
        this(null, null);
    }

    /**
     * Create a new {@link DefaultBinaryMemcacheResponse} with the header and key.
     *
     * @param key    the key to use
     */
    public DefaultBinaryMemcacheResponse(ByteBuf key) {
        this(key, null);
    }

    /**
     * Create a new {@link DefaultBinaryMemcacheResponse} with the header, key and extras.
     *
     * @param key    the key to use.
     * @param extras the extras to use.
     */
    public DefaultBinaryMemcacheResponse(ByteBuf key, ByteBuf extras) {
        super(key, extras);
        setMagic(RESPONSE_MAGIC_BYTE);
    }

    @Override
    public short status() {
        return status;
    }

    @Override
    public BinaryMemcacheResponse setStatus(short status) {
        this.status = status;
        return this;
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

    @Override
    public BinaryMemcacheResponse touch(Object hint) {
        super.touch(hint);
        return this;
    }
}
