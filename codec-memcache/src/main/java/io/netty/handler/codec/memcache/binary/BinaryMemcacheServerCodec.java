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

import io.netty.channel.ChannelHandlerAppender;

/**
 * The full server codec that combines the correct encoder and decoder.
 * <p/>
 * Use this codec if you need to implement a server that speaks the memcache binary protocol.
 * Internally, it combines the {@link BinaryMemcacheRequestDecoder} and the
 * {@link BinaryMemcacheResponseEncoder} to request decoding and response encoding.
 */
public class BinaryMemcacheServerCodec extends ChannelHandlerAppender {

    public BinaryMemcacheServerCodec() {
        this(AbstractBinaryMemcacheDecoder.DEFAULT_MAX_CHUNK_SIZE);
    }

    public BinaryMemcacheServerCodec(int decodeChunkSize) {
        add(new BinaryMemcacheRequestDecoder(decodeChunkSize));
        add(new BinaryMemcacheResponseEncoder());
    }
}
