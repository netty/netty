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
import io.netty.handler.codec.memcache.MemcacheObject;

/**
 * An interface that defines a binary Memcache message, providing common properties for
 * {@link BinaryMemcacheRequest} and {@link BinaryMemcacheResponse}.
 * <p/>
 * A {@link BinaryMemcacheMessage} always consists of a header and optional extras or/and
 * a key.
 *
 * @see BinaryMemcacheMessageHeader
 * @see BinaryMemcacheRequest
 * @see BinaryMemcacheResponse
 */
public interface BinaryMemcacheMessage<H extends BinaryMemcacheMessageHeader> extends MemcacheMessage {

    /**
     * Returns the {@link BinaryMemcacheMessageHeader} which contains the full required header.
     *
     * @return the required header.
     */
    H getHeader();

    /**
     * Returns the optional key of the document.
     *
     * @return the key of the document.
     */
    String getKey();

    /**
     * Returns a {@link ByteBuf} representation of the optional extras.
     *
     * @return the optional extras.
     */
    ByteBuf getExtras();

    /**
     * Increases the reference count by {@code 1}.
     */
    BinaryMemcacheMessage retain();

    /**
     * Increases the reference count by the specified {@code increment}.
     */
    BinaryMemcacheMessage retain(int increment);

}
