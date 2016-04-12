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

import io.netty.util.internal.UnstableApi;

/**
 * Represents a full {@link BinaryMemcacheRequest}, which contains the header and optional key and extras.
 */
@UnstableApi
public interface BinaryMemcacheRequest extends BinaryMemcacheMessage {

    /**
     * Returns the reserved field value.
     *
     * @return the reserved field value.
     */
    short reserved();

    /**
     * Sets the reserved field value.
     *
     * @param reserved the reserved field value.
     */
    BinaryMemcacheRequest setReserved(short reserved);

    @Override
    BinaryMemcacheRequest retain();

    @Override
    BinaryMemcacheRequest retain(int increment);

    @Override
    BinaryMemcacheRequest touch();

    @Override
    BinaryMemcacheRequest touch(Object hint);
}
