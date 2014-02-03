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

/**
 * The default implementation of a {@link BinaryMemcacheMessageHeader}.
 */
public abstract class AbstractBinaryMemcacheMessageHeader implements BinaryMemcacheMessageHeader {

    private byte magic;
    private byte opcode;
    private short keyLength;
    private byte extrasLength;
    private byte dataType;
    private int totalBodyLength;
    private int opaque;
    private long cas;

    @Override
    public byte getMagic() {
        return magic;
    }

    @Override
    public BinaryMemcacheMessageHeader setMagic(byte magic) {
        this.magic = magic;
        return this;
    }

    @Override
    public long getCAS() {
        return cas;
    }

    @Override
    public BinaryMemcacheMessageHeader setCAS(long cas) {
        this.cas = cas;
        return this;
    }

    @Override
    public int getOpaque() {
        return opaque;
    }

    @Override
    public BinaryMemcacheMessageHeader setOpaque(int opaque) {
        this.opaque = opaque;
        return this;
    }

    @Override
    public int getTotalBodyLength() {
        return totalBodyLength;
    }

    @Override
    public BinaryMemcacheMessageHeader setTotalBodyLength(int totalBodyLength) {
        this.totalBodyLength = totalBodyLength;
        return this;
    }

    @Override
    public byte getDataType() {
        return dataType;
    }

    @Override
    public BinaryMemcacheMessageHeader setDataType(byte dataType) {
        this.dataType = dataType;
        return this;
    }

    @Override
    public byte getExtrasLength() {
        return extrasLength;
    }

    @Override
    public BinaryMemcacheMessageHeader setExtrasLength(byte extrasLength) {
        this.extrasLength = extrasLength;
        return this;
    }

    @Override
    public short getKeyLength() {
        return keyLength;
    }

    @Override
    public BinaryMemcacheMessageHeader setKeyLength(short keyLength) {
        this.keyLength = keyLength;
        return this;
    }

    @Override
    public byte getOpcode() {
        return opcode;
    }

    @Override
    public BinaryMemcacheMessageHeader setOpcode(byte opcode) {
        this.opcode = opcode;
        return this;
    }
}
