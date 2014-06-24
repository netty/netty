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
import io.netty.handler.codec.memcache.AbstractMemcacheObject;

/**
 * Default implementation of a {@link BinaryMemcacheMessage}.
 */
public abstract class AbstractBinaryMemcacheMessage
    extends AbstractMemcacheObject
    implements BinaryMemcacheMessage {

    /**
     * Contains the optional key.
     */
    private String key;

    /**
     * Contains the optional extras.
     */
    private ByteBuf extras;

    private byte magic;
    private byte opcode;
    private short keyLength;
    private byte extrasLength;
    private byte dataType;
    private int totalBodyLength;
    private int opaque;
    private long cas;

    /**
     * Create a new instance with all properties set.
     *
     * @param key    the message key.
     * @param extras the message extras.
     */
    protected AbstractBinaryMemcacheMessage(String key, ByteBuf extras) {
        this.key = key;
        this.extras = extras;
    }

    @Override
    public String key() {
        return key;
    }

    @Override
    public ByteBuf extras() {
        return extras;
    }

    @Override
    public BinaryMemcacheMessage setKey(String key) {
        this.key = key;
        return this;
    }

    @Override
    public BinaryMemcacheMessage setExtras(ByteBuf extras) {
        this.extras = extras;
        return this;
    }

    @Override
    public byte magic() {
        return magic;
    }

    @Override
    public BinaryMemcacheMessage setMagic(byte magic) {
        this.magic = magic;
        return this;
    }

    @Override
    public long cas() {
        return cas;
    }

    @Override
    public BinaryMemcacheMessage setCas(long cas) {
        this.cas = cas;
        return this;
    }

    @Override
    public int opaque() {
        return opaque;
    }

    @Override
    public BinaryMemcacheMessage setOpaque(int opaque) {
        this.opaque = opaque;
        return this;
    }

    @Override
    public int totalBodyLength() {
        return totalBodyLength;
    }

    @Override
    public BinaryMemcacheMessage setTotalBodyLength(int totalBodyLength) {
        this.totalBodyLength = totalBodyLength;
        return this;
    }

    @Override
    public byte dataType() {
        return dataType;
    }

    @Override
    public BinaryMemcacheMessage setDataType(byte dataType) {
        this.dataType = dataType;
        return this;
    }

    @Override
    public byte extrasLength() {
        return extrasLength;
    }

    @Override
    public BinaryMemcacheMessage setExtrasLength(byte extrasLength) {
        this.extrasLength = extrasLength;
        return this;
    }

    @Override
    public short keyLength() {
        return keyLength;
    }

    @Override
    public BinaryMemcacheMessage setKeyLength(short keyLength) {
        this.keyLength = keyLength;
        return this;
    }

    @Override
    public byte opcode() {
        return opcode;
    }

    @Override
    public BinaryMemcacheMessage setOpcode(byte opcode) {
        this.opcode = opcode;
        return this;
    }

    @Override
    public int refCnt() {
        if (extras != null) {
            return extras.refCnt();
        }
        return 1;
    }

    @Override
    public BinaryMemcacheMessage retain() {
        if (extras != null) {
            extras.retain();
        }
        return this;
    }

    @Override
    public BinaryMemcacheMessage retain(int increment) {
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

    @Override
    public BinaryMemcacheMessage touch() {
        return touch(null);
    }

    @Override
    public BinaryMemcacheMessage touch(Object hint) {
        if (extras != null) {
            extras.touch(hint);
        }
        return this;
    }
}
