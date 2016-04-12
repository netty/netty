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
import io.netty.handler.codec.memcache.MemcacheMessage;
import io.netty.util.internal.UnstableApi;

/**
 * An interface that defines a binary Memcache message, providing common properties for
 * {@link BinaryMemcacheRequest} and {@link BinaryMemcacheResponse}.
 * <p/>
 * A {@link BinaryMemcacheMessage} always consists of a header and optional extras or/and
 * a key.
 *
 * @see BinaryMemcacheRequest
 * @see BinaryMemcacheResponse
 */
@UnstableApi
public interface BinaryMemcacheMessage extends MemcacheMessage {

    /**
     * Returns the magic byte for the message.
     *
     * @return the magic byte.
     */
    byte magic();

    /**
     * Sets the magic byte.
     *
     * @param magic the magic byte to use.
     * @see BinaryMemcacheOpcodes for typesafe opcodes.
     */
    BinaryMemcacheMessage setMagic(byte magic);

    /**
     * Returns the opcode for the message.
     *
     * @return the opcode.
     */
    byte opcode();

    /**
     * Sets the opcode for the message.
     *
     * @param code the opcode to use.
     */
    BinaryMemcacheMessage setOpcode(byte code);

    /**
     * Returns the key length of the message.
     * <p/>
     * This may return 0, since the key is optional.
     *
     * @return the key length.
     */
    short keyLength();

    /**
     * Return the extras length of the message.
     * <p/>
     * This may be 0, since the extras content is optional.
     *
     * @return the extras length.
     */
    byte extrasLength();

    /**
     * Returns the data type of the message.
     *
     * @return the data type of the message.
     */
    byte dataType();

    /**
     * Sets the data type of the message.
     *
     * @param dataType the data type of the message.
     */
    BinaryMemcacheMessage setDataType(byte dataType);

    /**
     * Returns the total body length.
     * <p/>
     * Note that this may be 0, since the body is optional.
     *
     * @return the total body length.
     */
    int totalBodyLength();

    /**
     * Sets the total body length.
     * <p/>
     * Note that this may be 0, since the body length is optional.
     *
     * @param totalBodyLength the total body length.
     */
    BinaryMemcacheMessage setTotalBodyLength(int totalBodyLength);

    /**
     * Returns the opaque value.
     *
     * @return the opaque value.
     */
    int opaque();

    /**
     * Sets the opaque value.
     *
     * @param opaque the opaque value to use.
     */
    BinaryMemcacheMessage setOpaque(int opaque);

    /**
     * Returns the CAS identifier.
     *
     * @return the CAS identifier.
     */
    long cas();

    /**
     * Sets the CAS identifier.
     *
     * @param cas the CAS identifier to use.
     */
    BinaryMemcacheMessage setCas(long cas);

    /**
     * Returns the optional key of the document.
     *
     * @return the key of the document.
     */
    ByteBuf key();

    /**
     * Sets the key of the document. {@link ByteBuf#release()} ownership of {@code key}
     * is transferred to this {@link BinaryMemcacheMessage}.
     *
     * @param key the key of the message. {@link ByteBuf#release()} ownership is transferred
     *            to this {@link BinaryMemcacheMessage}.
     */
    BinaryMemcacheMessage setKey(ByteBuf key);

    /**
     * Returns a {@link ByteBuf} representation of the optional extras.
     *
     * @return the optional extras.
     */
    ByteBuf extras();

    /**
     * Sets the extras buffer on the message. {@link ByteBuf#release()} ownership of {@code extras}
     * is transferred to this {@link BinaryMemcacheMessage}.
     *
     * @param extras the extras buffer of the document. {@link ByteBuf#release()} ownership is transferred
     *               to this {@link BinaryMemcacheMessage}.
     */
    BinaryMemcacheMessage setExtras(ByteBuf extras);

    /**
     * Increases the reference count by {@code 1}.
     */
    @Override
    BinaryMemcacheMessage retain();

    /**
     * Increases the reference count by the specified {@code increment}.
     */
    @Override
    BinaryMemcacheMessage retain(int increment);

    @Override
    BinaryMemcacheMessage touch();

    @Override
    BinaryMemcacheMessage touch(Object hint);
}
