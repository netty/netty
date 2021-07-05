/*
 * Copyright 2016 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.netty.handler.codec.redis;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.CodecException;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.UnstableApi;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregates {@link RedisMessage} parts into {@link MapRedisMessage}.
 * This decoder should be used together with {@link RedisDecoder}.
 */
@UnstableApi
public final class RedisMapAggregator extends MessageToMessageDecoder<RedisMessage> {

    private final Deque<AggregateState> depths = new ArrayDeque<AggregateState>(4);

    @Override
    protected void decode(ChannelHandlerContext ctx, RedisMessage msg, List<Object> out) throws Exception {
        if (msg instanceof MapHeaderRedisMessage) {
            msg = decodeRedisMapHeader((AggregatedHeaderRedisMessage) msg);
            if (msg == null) {
                return;
            }
        } else {
            ReferenceCountUtil.retain(msg);
        }

        while (!depths.isEmpty()) {
            AggregateState current = depths.peek();
            current.add(msg);

            // if current aggregation completed, go to parent aggregation.
            if (current.children.size() == current.length) {
                msg = new MapRedisMessage(current.children);
                depths.pop();
            } else {
                // not aggregated yet. try next time.
                return;
            }
        }

        out.add(msg);
    }

    private RedisMessage decodeRedisMapHeader(AggregatedHeaderRedisMessage header) {
        // encode to Null types message if map is null or empty
        if (header.isNull()) {
            return NullRedisMessage.INSTANCE;
        } else if (header.length() == 0L) {
            return MapRedisMessage.EMPTY_INSTANCE;
        } else if (header.length() > 0L) {
            // Currently, this codec doesn't support `long` length for arrays because Java's Map.size() is int.
            if (header.length() > Integer.MAX_VALUE) {
                throw new CodecException("this codec doesn't support longer length than " + Integer.MAX_VALUE);
            } else if (header.length() % 2 != 0) {
                throw new CodecException("the length must be even in Map types, but now is " + header.length());
            }

            // start aggregating array or set according header type
            depths.push(new AggregateState(header, (int) header.length()));
            return null;
        } else {
            throw new CodecException("bad length: " + header.length());
        }
    }

    private static final class AggregateState {
        private final int length;
        private final Map<RedisMessage, RedisMessage> children;
        private final RedisMessageType aggregateType;
        private RedisMessage cache;

        AggregateState(AggregatedHeaderRedisMessage headerType, int length) {
            this.length = length;
            if (headerType instanceof MapHeaderRedisMessage) {
                this.children = new HashMap<RedisMessage, RedisMessage>(length);
                this.aggregateType = RedisMessageType.MAP_HEADER;
            } else {
                throw new CodecException("bad header type: " + headerType);
            }
        }

        // aggregate msg to map key and value
        void add(RedisMessage msg) {
            if (cache == null) {
                cache = msg;
            } else {
                children.put(cache, msg);
                cache = null;
            }
        }
    }
}
