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
import io.netty.handler.codec.memcache.FullMemcacheMessage;
import io.netty.util.internal.UnstableApi;

/**
 * A {@link BinaryMemcacheResponse} that also includes the content.
 */
@UnstableApi
public interface FullBinaryMemcacheResponse extends BinaryMemcacheResponse, FullMemcacheMessage {

    @Override
    FullBinaryMemcacheResponse copy();

    @Override
    FullBinaryMemcacheResponse duplicate();

    @Override
    FullBinaryMemcacheResponse retainedDuplicate();

    @Override
    FullBinaryMemcacheResponse replace(ByteBuf content);

    @Override
    FullBinaryMemcacheResponse retain(int increment);

    @Override
    FullBinaryMemcacheResponse retain();

    @Override
    FullBinaryMemcacheResponse touch();

    @Override
    FullBinaryMemcacheResponse touch(Object hint);
}
