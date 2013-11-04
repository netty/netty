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
 * Contains all common header fields in a {@link BinaryMemcacheMessage}.
 * <p/>
 * <p>Since the header is different for a {@link BinaryMemcacheRequest} and {@link BinaryMemcacheResponse}, see
 * {@link BinaryMemcacheRequestHeader} and {@link BinaryMemcacheResponseHeader}.</p>
 * <p/>
 * <p>The {@link BinaryMemcacheMessageHeader} is always 24 bytes in length and needs to be filled up if values are
 * not set.</p>
 * <p/>
 * <p>Fore more information, see the official protocol specification
 * <a href="https://code.google.com/p/memcached/wiki/MemcacheBinaryProtocol">here</a>.</p>
 */
public interface BinaryMemcacheMessageHeader {

    /**
     * Returns the magic byte for the message.
     *
     * @return the magic byte.
     */
    byte getMagic();

    /**
     * Sets the magic byte.
     *
     * @param magic the magic byte to use.
     * @see io.netty.handler.codec.memcache.binary.util.BinaryMemcacheOpcodes for typesafe opcodes.
     */
    BinaryMemcacheMessageHeader setMagic(byte magic);

    /**
     * Returns the opcode for the message.
     *
     * @return the opcode.
     */
    byte getOpcode();

    /**
     * Sets the opcode for the message.
     *
     * @param code the opcode to use.
     */
    BinaryMemcacheMessageHeader setOpcode(byte code);

    /**
     * Returns the key length of the message.
     * <p/>
     * This may return 0, since the key is optional.
     *
     * @return the key length.
     */
    short getKeyLength();

    /**
     * Set the key length of the message.
     * <p/>
     * This may be 0, since the key is optional.
     *
     * @param keyLength the key length to use.
     */
    BinaryMemcacheMessageHeader setKeyLength(short keyLength);

    /**
     * Return the extras length of the message.
     * <p/>
     * This may be 0, since the extras content is optional.
     *
     * @return the extras length.
     */
    byte getExtrasLength();

    /**
     * Set the extras length of the message.
     * <p/>
     * This may be 0, since the extras content is optional.
     *
     * @param extrasLength the extras length.
     */
    BinaryMemcacheMessageHeader setExtrasLength(byte extrasLength);

    /**
     * Returns the data type of the message.
     *
     * @return the data type of the message.
     */
    byte getDataType();

    /**
     * Sets the data type of the message.
     *
     * @param dataType the data type of the message.
     */
    BinaryMemcacheMessageHeader setDataType(byte dataType);

    /**
     * Returns the total body length.
     * <p/>
     * Note that this may be 0, since the body is optional.
     *
     * @return the total body length.
     */
    int getTotalBodyLength();

    /**
     * Sets the total body length.
     * <p/>
     * Note that this may be 0, since the body length is optional.
     *
     * @param totalBodyLength the total body length.
     */
    BinaryMemcacheMessageHeader setTotalBodyLength(int totalBodyLength);

    /**
     * Returns the opaque value.
     *
     * @return the opaque value.
     */
    int getOpaque();

    /**
     * Sets the opaque value.
     *
     * @param opaque the opqaue value to use.
     */
    BinaryMemcacheMessageHeader setOpaque(int opaque);

    /**
     * Returns the CAS identifier.
     *
     * @return the CAS identifier.
     */
    long getCAS();

    /**
     * Sets the CAS identifier.
     *
     * @param cas the CAS identifier to use.
     */
    BinaryMemcacheMessageHeader setCAS(long cas);

}
