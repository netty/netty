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
