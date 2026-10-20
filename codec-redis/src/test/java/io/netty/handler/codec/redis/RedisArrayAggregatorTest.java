/*
 * Copyright 2026 The Netty Project
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

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.CodecException;
import io.netty.handler.codec.PrematureChannelClosureException;
import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RedisArrayAggregatorTest {

    @Test
    public void testLimitNested() {
        byte[] arrayHeader = "*1\r\n".getBytes(CharsetUtil.US_ASCII);
        int maxNestedDepth = 100;
        EmbeddedChannel channel = new EmbeddedChannel(new RedisDecoder(),
                new RedisArrayAggregator(RedisConstants.REDIS_MAX_ARRAY_LENGTH, maxNestedDepth));
        for (int i = 0; i < maxNestedDepth; i++) {
            assertFalse(channel.writeInbound(Unpooled.wrappedBuffer(arrayHeader)));
        }

        // Next write should trigger an exception.
        assertThrows(CodecException.class, () -> channel.writeInbound(Unpooled.wrappedBuffer(arrayHeader)));
        assertFalse(channel.finishAndReleaseAll());
    }

    @Test
    public void testTotalPendingElementBudgetAcrossNestedArrays() {
        // maxElements and maxNestedArrayDepth are independent limits. Each header below stays
        // within maxElements on its own, and the nesting never reaches maxNestedArrayDepth, but
        // the total number of elements declared across the active nested arrays would multiply
        // out to maxElements * maxNestedArrayDepth if the two limits were not also bounded jointly.
        int maxElements = 4096;
        int maxNestedArrayDepth = 4;
        EmbeddedChannel channel = new EmbeddedChannel(
                new RedisArrayAggregator(maxElements, maxNestedArrayDepth));

        assertFalse(channel.writeInbound(new ArrayHeaderRedisMessage(maxElements)));

        // A second header of the same declared length pushes the total outstanding element count
        // to 2 * maxElements, which must be rejected even though this header, considered alone,
        // and the resulting nesting depth of 2 both stay within their respective limits.
        assertThrows(CodecException.class,
                () -> channel.writeInbound(new ArrayHeaderRedisMessage(maxElements)));
        assertFalse(channel.finish());
    }

    @Test
    public void testPendingElementBudgetIsReclaimedOnceArrayCompletes() {
        int maxElements = 4096;
        int maxNestedArrayDepth = 4;
        EmbeddedChannel channel = new EmbeddedChannel(
                new RedisArrayAggregator(maxElements, maxNestedArrayDepth));

        // Complete a full array so its declared length is released from the outstanding budget.
        assertFalse(channel.writeInbound(new ArrayHeaderRedisMessage(1)));
        FullBulkStringRedisMessage element = new FullBulkStringRedisMessage(Unpooled.buffer());
        assertTrue(channel.writeInbound(element));
        ((ArrayRedisMessage) channel.readInbound()).release();

        // The budget should now allow a fresh header declaring up to maxElements again.
        assertFalse(channel.writeInbound(new ArrayHeaderRedisMessage(maxElements)));

        assertThrows(PrematureChannelClosureException.class, channel::finish);
    }

    @Test
    void testDoesNotLeakOnClose() {
        EmbeddedChannel ch = new EmbeddedChannel(new RedisArrayAggregator());
        assertFalse(ch.writeInbound(new ArrayHeaderRedisMessage(2)));

        FullBulkStringRedisMessage redisMessage = new FullBulkStringRedisMessage(Unpooled.buffer());
        assertEquals(1, redisMessage.refCnt());
        assertFalse(ch.writeInbound(redisMessage));
        assertEquals(1, redisMessage.refCnt());

        assertThrows(PrematureChannelClosureException.class, ch::finish);
        assertEquals(0, redisMessage.refCnt());
    }

    @Test
    void testDoesNotLeakOnRemoval() {
        EmbeddedChannel ch = new EmbeddedChannel(new RedisArrayAggregator());
        assertFalse(ch.writeInbound(new ArrayHeaderRedisMessage(2)));

        FullBulkStringRedisMessage redisMessage = new FullBulkStringRedisMessage(Unpooled.buffer());
        assertEquals(1, redisMessage.refCnt());
        assertFalse(ch.writeInbound(redisMessage));
        assertEquals(1, redisMessage.refCnt());
        ch.pipeline().remove(RedisArrayAggregator.class);
        assertEquals(0, redisMessage.refCnt());
        assertFalse(ch::finish);
    }
}
