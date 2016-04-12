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
import io.netty.handler.codec.memcache.AbstractMemcacheObjectAggregator;
import io.netty.handler.codec.memcache.FullMemcacheMessage;
import io.netty.handler.codec.memcache.MemcacheContent;
import io.netty.handler.codec.memcache.MemcacheObject;
import io.netty.util.internal.UnstableApi;

/**
 * An object aggregator for the memcache binary protocol.
 *
 * It aggregates {@link BinaryMemcacheMessage}s and {@link MemcacheContent} into {@link FullBinaryMemcacheRequest}s
 * or {@link FullBinaryMemcacheResponse}s.
 */
@UnstableApi
public class BinaryMemcacheObjectAggregator extends AbstractMemcacheObjectAggregator<BinaryMemcacheMessage> {

    public BinaryMemcacheObjectAggregator(int maxContentLength) {
        super(maxContentLength);
    }

    @Override
    protected boolean isStartMessage(MemcacheObject msg) throws Exception {
        return msg instanceof BinaryMemcacheMessage;
    }

    @Override
    protected FullMemcacheMessage beginAggregation(BinaryMemcacheMessage start, ByteBuf content) throws Exception {
        if (start instanceof BinaryMemcacheRequest) {
            return toFullRequest((BinaryMemcacheRequest) start, content);
        }

        if (start instanceof BinaryMemcacheResponse) {
            return toFullResponse((BinaryMemcacheResponse) start, content);
        }

        // Should not reach here.
        throw new Error();
    }

    private static FullBinaryMemcacheRequest toFullRequest(BinaryMemcacheRequest request, ByteBuf content) {
        ByteBuf key = request.key() == null ? null : request.key().retain();
        ByteBuf extras = request.extras() == null ? null : request.extras().retain();
        DefaultFullBinaryMemcacheRequest fullRequest =
                new DefaultFullBinaryMemcacheRequest(key, extras, content);

        fullRequest.setMagic(request.magic());
        fullRequest.setOpcode(request.opcode());
        fullRequest.setKeyLength(request.keyLength());
        fullRequest.setExtrasLength(request.extrasLength());
        fullRequest.setDataType(request.dataType());
        fullRequest.setTotalBodyLength(request.totalBodyLength());
        fullRequest.setOpaque(request.opaque());
        fullRequest.setCas(request.cas());
        fullRequest.setReserved(request.reserved());

        return fullRequest;
    }

    private static FullBinaryMemcacheResponse toFullResponse(BinaryMemcacheResponse response, ByteBuf content) {
        ByteBuf key = response.key() == null ? null : response.key().retain();
        ByteBuf extras = response.extras() == null ? null : response.extras().retain();
        DefaultFullBinaryMemcacheResponse fullResponse =
                new DefaultFullBinaryMemcacheResponse(key, extras, content);

        fullResponse.setMagic(response.magic());
        fullResponse.setOpcode(response.opcode());
        fullResponse.setKeyLength(response.keyLength());
        fullResponse.setExtrasLength(response.extrasLength());
        fullResponse.setDataType(response.dataType());
        fullResponse.setTotalBodyLength(response.totalBodyLength());
        fullResponse.setOpaque(response.opaque());
        fullResponse.setCas(response.cas());
        fullResponse.setStatus(response.status());

        return fullResponse;
    }
}
