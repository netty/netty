package io.netty.handler.codec.redis;

import org.junit.Test;

import static io.netty.handler.codec.redis.RedisCodecTestUtil.byteBufOf;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNull.nullValue;

public class FixedRedisMessagePoolTest {

    @Test
    public void shouldGetSameMessageObject() {
        FixedRedisMessagePool pool = FixedRedisMessagePool.INSTANCE;

        SimpleStringRedisMessage fromStr = pool.getSimpleString("OK");
        SimpleStringRedisMessage fromEnum = pool.getSimpleString(FixedRedisMessagePool.RedisReplyKey.OK);
        SimpleStringRedisMessage fromByteBuf = pool.getSimpleString(byteBufOf("OK"));

        assertThat(fromStr.content(), is("OK"));
        assertThat(fromStr, sameInstance(fromEnum));
        assertThat(fromStr, sameInstance(fromByteBuf));

        ErrorRedisMessage errorFromStr = pool.getError("NOAUTH Authentication required.");
        ErrorRedisMessage errorFromEnum = pool.getError(FixedRedisMessagePool.RedisErrorKey.NOT_AUTH);
        ErrorRedisMessage errorFromByteBuf = pool.getError(byteBufOf("NOAUTH Authentication required."));

        assertThat(errorFromStr.content(), is("NOAUTH Authentication required."));
        assertThat(errorFromStr, sameInstance(errorFromEnum));
        assertThat(errorFromStr, sameInstance(errorFromByteBuf));
    }

    @Test
    public void shouldReturnNullByNotExistKey() {
        FixedRedisMessagePool pool = FixedRedisMessagePool.INSTANCE;

        SimpleStringRedisMessage message1 = pool.getSimpleString("Not exist");
        SimpleStringRedisMessage message2 = pool.getSimpleString(byteBufOf("Not exist"));

        assertThat(message1, is(nullValue()));
        assertThat(message2, is(nullValue()));

        ErrorRedisMessage error1 = pool.getError("Not exist");
        ErrorRedisMessage error2 = pool.getError(byteBufOf("Not exist"));

        assertThat(error1, is(nullValue()));
        assertThat(error2, is(nullValue()));
    }

    @Test
    public void shouldReturnDifferentMessage() {
        FixedRedisMessagePool pool = FixedRedisMessagePool.INSTANCE;

        SimpleStringRedisMessage okMessage = pool.getSimpleString(FixedRedisMessagePool.RedisReplyKey.OK);
        SimpleStringRedisMessage pongMessage = pool.getSimpleString(FixedRedisMessagePool.RedisReplyKey.PONG);

        assertThat(okMessage, is(not(pongMessage)));
    }

}