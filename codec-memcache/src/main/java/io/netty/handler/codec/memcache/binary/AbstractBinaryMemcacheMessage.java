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
import io.netty.util.internal.UnstableApi;

/**
 * Default implementation of a {@link BinaryMemcacheMessage}.
 */
@UnstableApi
public abstract class AbstractBinaryMemcacheMessage
    extends AbstractMemcacheObject
    implements BinaryMemcacheMessage {

    /**
     * Contains the optional key.
     */
    private ByteBuf key;

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
    protected AbstractBinaryMemcacheMessage(ByteBuf key, ByteBuf extras) {
        this.key = key;
        keyLength = key == null ? 0 : (short) key.readableBytes();
        this.extras = extras;
        extrasLength = extras == null ? 0 : (byte) extras.readableBytes();
        totalBodyLength = keyLength + extrasLength;
    }

    @Override
    public ByteBuf key() {
        return key;
    }

    @Override
    public ByteBuf extras() {
        return extras;
    }

    @Override
    public BinaryMemcacheMessage setKey(ByteBuf key) {
        if (this.key != null) {
            this.key.release();
        }
        this.key = key;
        short oldKeyLength = keyLength;
        keyLength = key == null ? 0 : (short) key.readableBytes();
        totalBodyLength = totalBodyLength + keyLength - oldKeyLength;
        return this;
    }

    @Override
    public BinaryMemcacheMessage setExtras(ByteBuf extras) {
        if (this.extras != null) {
            this.extras.release();
        }
        this.extras = extras;
        short oldExtrasLength = extrasLength;
        extrasLength = extras == null ? 0 : (byte) extras.readableBytes();
        totalBodyLength = totalBodyLength + extrasLength - oldExtrasLength;
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

    /**
     * Set the extras length of the message.
     * <p/>
     * This may be 0, since the extras content is optional.
     *
     * @param extrasLength the extras length.
     */
    BinaryMemcacheMessage setExtrasLength(byte extrasLength) {
        this.extrasLength = extrasLength;
        return this;
    }

    @Override
    public short keyLength() {
        return keyLength;
    }

    /**
     * Set the key length of the message.
     * <p/>
     * This may be 0, since the key is optional.
     *
     * @param keyLength the key length to use.
     */
    BinaryMemcacheMessage setKeyLength(short keyLength) {
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
    public BinaryMemcacheMessage retain() {
        super.retain();
        return this;
    }

    @Override
    public BinaryMemcacheMessage retain(int increment) {
        super.retain(increment);
        return this;
    }

    @Override
    protected void deallocate() {
        if (key != null) {
            key.release();
        }
        if (extras != null) {
            extras.release();
        }
    }

    @Override
    public BinaryMemcacheMessage touch() {
        super.touch();
        return this;
    }

    @Override
    public BinaryMemcacheMessage touch(Object hint) {
        if (key != null) {
            key.touch(hint);
        }
        if (extras != null) {
            extras.touch(hint);
        }
        return this;
    }

    /**
     * Copies special metadata hold by this instance to the provided instance
     *
     * @param dst The instance where to copy the metadata of this instance to
     */
    void copyMeta(AbstractBinaryMemcacheMessage dst) {
        dst.magic = magic;
        dst.opcode = opcode;
        dst.keyLength = keyLength;
        dst.extrasLength = extrasLength;
        dst.dataType = dataType;
        dst.totalBodyLength = totalBodyLength;
        dst.opaque = opaque;
        dst.cas = cas;
    }
}
