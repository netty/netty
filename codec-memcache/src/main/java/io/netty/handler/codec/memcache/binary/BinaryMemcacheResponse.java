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
 * Represents a full {@link BinaryMemcacheResponse}, which contains the header and optional key and extras.
 */
@UnstableApi
public interface BinaryMemcacheResponse extends BinaryMemcacheMessage {

    /**
     * Returns the status of the response.
     *
     * @return the status of the response.
     */
    short status();

    /**
     * Sets the status of the response.
     *
     * @param status the status to set.
     */
    BinaryMemcacheResponse setStatus(short status);

    @Override
    BinaryMemcacheResponse retain();

    @Override
    BinaryMemcacheResponse retain(int increment);

    @Override
    BinaryMemcacheResponse touch();

    @Override
    BinaryMemcacheResponse touch(Object hint);
}
