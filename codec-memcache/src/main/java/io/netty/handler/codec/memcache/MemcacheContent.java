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
package io.netty.handler.codec.memcache;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelPipeline;

/**
 * An Memcache content chunk.
 * <p/>
 * A implementation of a {@link AbstractMemcacheObjectDecoder} generates {@link MemcacheContent} after
 * {@link MemcacheMessage} when the content is large. If you prefer not to receive {@link MemcacheContent}
 * in your handler, place a aggregator after an implementation of the {@link AbstractMemcacheObjectDecoder}
 * in the {@link ChannelPipeline}.
 */
public interface MemcacheContent extends MemcacheObject {

    /**
     * Return the data which is held by this {@link MemcacheContent}.
     */
    ByteBuf content();

    /**
     * Create a deep copy of this {@link MemcacheContent}.
     */
    MemcacheContent copy();

    /**
     * Duplicate the {@link MemcacheContent}. Be aware that this will not automatically call {@link #retain()}.
     */
    MemcacheContent duplicate();
}
