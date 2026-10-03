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
import io.netty.handler.codec.PrematureChannelClosureException;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.UnstableApi;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Aggregates {@link RedisMessage} parts into {@link ArrayRedisMessage}. This decoder
 * should be used together with {@link RedisDecoder}.
 */
@UnstableApi
public final class RedisArrayAggregator extends MessageToMessageDecoder<RedisMessage> {

    private static final int DEFAULT_MAX_ARRAY_LENGTH = RedisConstants.REDIS_MAX_ARRAY_LENGTH;

    // Bound the eagerly reserved backing capacity of an aggregate's child list, regardless of the
    // length declared in the array header. The list can still grow to the declared length as
    // elements actually arrive.
    private static final int INITIAL_CHILDREN_CAPACITY = 32;

    private final int maxNestedArrayDepth;
    private final Deque<AggregateState> depths = new ArrayDeque<AggregateState>(4);
    private final int maxElements;

    // Sum of the declared lengths of all currently active (nested) aggregate states. Bounding this
    // total, rather than each header individually, prevents an attacker from multiplying the
    // per-header element cap by the nesting depth cap to reserve far more backing capacity than
    // maxElements alone would suggest.
    private long pendingElements;

    /**
     * Create a new instance that will aggregate an {@link ArrayHeaderRedisMessage}
     * and its subsequent elements into an {@link ArrayRedisMessage}.
     * <p>
     * This constructor specifies a maximum number of elements of 1.000.000,
     * but this default can be increased with the {@value RedisConstants#PROP_REDIS_MAX_ARRAY_LENGTH} system property.
     *
     * @deprecated Use {@link #RedisArrayAggregator(int, int)} instead to define a max size of the array to aggregate.
     */
    @Deprecated
    public RedisArrayAggregator() {
        // Let's impose some limit at least by default.
        this(DEFAULT_MAX_ARRAY_LENGTH, 1024);
    }

    /**
     * Create a new instance that will aggregate an {@link ArrayHeaderRedisMessage}
     * and its subsequent elements into an {@link ArrayRedisMessage}.
     * <p>
     * A {@link CodecException} will be thrown if the array header specify a length greater than
     * the given number of max elements.
     * @param maxElements The maximum number of elements to aggregate in a single message. This applies cumulatively.
     * @param maxNestedArrayDepth   the maximum depth of the nested array before an exception will be thrown
     */
    public RedisArrayAggregator(int maxElements, int maxNestedArrayDepth) {
        super(RedisMessage.class);
        this.maxElements = ObjectUtil.checkPositive(maxElements, "maxElements");
        this.maxNestedArrayDepth = ObjectUtil.checkPositive(maxNestedArrayDepth, "maxNestedArrayDepth");
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, RedisMessage msg, List<Object> out) throws Exception {
        if (msg instanceof ArrayHeaderRedisMessage) {
            msg = decodeRedisArrayHeader((ArrayHeaderRedisMessage) msg);
            if (msg == null) {
                return;
            }
        } else {
            ReferenceCountUtil.retain(msg);
        }

        while (!depths.isEmpty()) {
            AggregateState current = depths.peek();
            current.children.add(msg);

            // if current aggregation completed, go to parent aggregation.
            if (current.children.size() == current.length) {
                msg = new ArrayRedisMessage(current.children);
                depths.pop();
                pendingElements -= current.length;
            } else {
                // not aggregated yet. try next time.
                return;
            }
        }

        out.add(msg);
    }

    private CodecException clearAndCreateException(String msg) {
        releaseAndClearDepths();
        return new CodecException(msg);
    }

    private RedisMessage decodeRedisArrayHeader(ArrayHeaderRedisMessage header) {
        if (header.isNull()) {
            return ArrayRedisMessage.NULL_INSTANCE;
        } else if (header.length() == 0L) {
            return ArrayRedisMessage.EMPTY_INSTANCE;
        } else if (header.length() > 0L) {
            // Currently, this codec doesn't support `long` length for arrays because Java's List.size() is int.
            if (header.length() > maxElements) {
                throw clearAndCreateException("this codec doesn't support longer length than " + maxElements);
            }

            if (depths.size() >= maxNestedArrayDepth) {
                throw clearAndCreateException("max nested array depth exceeded: "  + maxNestedArrayDepth);
            }

            // Bound the total number of elements declared across all currently active (nested)
            // aggregate states, not just the length of this one header. Without this, a header's
            // declared length could be repeated at every nesting level, letting maxElements and
            // maxNestedArrayDepth multiply into a far larger reserved capacity than either limit
            // implies on its own.
            long newPendingElements = pendingElements + header.length();
            if (newPendingElements > maxElements) {
                throw clearAndCreateException(
                        "total outstanding array elements exceeds " + maxElements);
            }
            pendingElements = newPendingElements;

            // start aggregating array
            depths.push(new AggregateState((int) header.length()));
            return null;
        } else {
            throw clearAndCreateException("bad length: " + header.length());
        }
    }

    private static final class AggregateState {
        private final int length;
        private final List<RedisMessage> children;
        AggregateState(int length) {
            this.length = length;
            this.children = new ArrayList<RedisMessage>(Math.min(length, INITIAL_CHILDREN_CAPACITY));
        }
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        super.handlerRemoved(ctx);
        releaseAndClearDepths();
    }

    private void releaseAndClearDepths() {
        for (AggregateState state : depths) {
            for (RedisMessage message : state.children) {
                ReferenceCountUtil.safeRelease(message);
            }
        }
        depths.clear();
        pendingElements = 0;
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);

        if (!depths.isEmpty()) {
            ctx.fireExceptionCaught(new PrematureChannelClosureException(
                    "channel gone inactive with " + depths.size() +
                            " messages still incomplete"));
        }
    }
}
