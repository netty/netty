/*
 * Copyright 2021 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.netty.handler.codec.redis;

import org.junit.jupiter.api.Test;

import static io.netty.handler.codec.redis.RedisCodecTestUtil.byteBufOf;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the correct functionality of the {@link FixedRedisMessagePool}.
 */
public class FixedRedisMessagePoolTest {

    @Test
    public void shouldGetSameMessageObject() {
        FixedRedisMessagePool pool = FixedRedisMessagePool.INSTANCE;

        SimpleStringRedisMessage fromStr = pool.getSimpleString("OK");
        SimpleStringRedisMessage fromEnum = pool.getSimpleString(FixedRedisMessagePool.RedisReplyKey.OK);
        SimpleStringRedisMessage fromByteBuf = pool.getSimpleString(byteBufOf("OK"));

        assertEquals(fromStr.content(), "OK");
        assertEquals(fromStr, fromEnum);
        assertEquals(fromStr, fromByteBuf);

        ErrorRedisMessage errorFromStr = pool.getError("NOAUTH Authentication required.");
        ErrorRedisMessage errorFromEnum = pool.getError(FixedRedisMessagePool.RedisErrorKey.NOT_AUTH);
        ErrorRedisMessage errorFromByteBuf = pool.getError(byteBufOf("NOAUTH Authentication required."));

        assertEquals(errorFromStr.content(), "NOAUTH Authentication required.");
        assertEquals(errorFromStr, errorFromEnum);
        assertEquals(errorFromStr, errorFromByteBuf);
    }

    @Test
    public void shouldReturnNullByNotExistKey() {
        FixedRedisMessagePool pool = FixedRedisMessagePool.INSTANCE;

        SimpleStringRedisMessage message1 = pool.getSimpleString("Not exist");
        SimpleStringRedisMessage message2 = pool.getSimpleString(byteBufOf("Not exist"));

        assertNull(message1);
        assertNull(message2);

        ErrorRedisMessage error1 = pool.getError("Not exist");
        ErrorRedisMessage error2 = pool.getError(byteBufOf("Not exist"));

        assertNull(error1);
        assertNull(error2);
    }

    @Test
    public void shouldReturnDifferentMessage() {
        FixedRedisMessagePool pool = FixedRedisMessagePool.INSTANCE;

        SimpleStringRedisMessage okMessage = pool.getSimpleString(FixedRedisMessagePool.RedisReplyKey.OK);
        SimpleStringRedisMessage pongMessage = pool.getSimpleString(FixedRedisMessagePool.RedisReplyKey.PONG);

        assertNotEquals(okMessage, pongMessage);
    }
}
