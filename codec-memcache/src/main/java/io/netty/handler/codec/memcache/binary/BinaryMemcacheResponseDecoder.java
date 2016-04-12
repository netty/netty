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
import io.netty.buffer.Unpooled;
import io.netty.util.internal.UnstableApi;

/**
 * The decoder which takes care of decoding the response headers.
 */
@UnstableApi
public class BinaryMemcacheResponseDecoder
    extends AbstractBinaryMemcacheDecoder<BinaryMemcacheResponse> {

    public BinaryMemcacheResponseDecoder() {
        this(DEFAULT_MAX_CHUNK_SIZE);
    }

    public BinaryMemcacheResponseDecoder(int chunkSize) {
        super(chunkSize);
    }

    @Override
    protected BinaryMemcacheResponse decodeHeader(ByteBuf in) {
        DefaultBinaryMemcacheResponse header = new DefaultBinaryMemcacheResponse();
        header.setMagic(in.readByte());
        header.setOpcode(in.readByte());
        header.setKeyLength(in.readShort());
        header.setExtrasLength(in.readByte());
        header.setDataType(in.readByte());
        header.setStatus(in.readShort());
        header.setTotalBodyLength(in.readInt());
        header.setOpaque(in.readInt());
        header.setCas(in.readLong());
        return header;
    }

    @Override
    protected BinaryMemcacheResponse buildInvalidMessage() {
        return new DefaultBinaryMemcacheResponse(Unpooled.EMPTY_BUFFER, Unpooled.EMPTY_BUFFER);
    }
}
