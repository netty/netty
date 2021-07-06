/*
 * Copyright 2016 The Netty Project
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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.DecoderException;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static io.netty.handler.codec.redis.RedisCodecTestUtil.byteBufOf;
import static io.netty.handler.codec.redis.RedisCodecTestUtil.bytesOf;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the correct functionality of the {@link RedisEncoder}.
 */
public class RedisEncoderTest {

    private EmbeddedChannel channel;

    @BeforeEach
    public void setup() throws Exception {
        channel = new EmbeddedChannel(new RedisEncoder());
    }

    @AfterEach
    public void teardown() throws Exception {
        assertFalse(channel.finish());
    }

    @Test
    public void shouldEncodeInlineCommand() {
        RedisMessage msg = new InlineCommandRedisMessage("ping");

        boolean result = channel.writeOutbound(msg);
        assertThat(result, is(true));

        ByteBuf written = readAll(channel);
        assertThat(bytesOf(written), is(bytesOf("ping\r\n")));
        written.release();
    }

    @Test
    public void shouldEncodeSimpleString() {
        RedisMessage msg = new SimpleStringRedisMessage("simple");

        boolean result = channel.writeOutbound(msg);
        assertThat(result, is(true));

        ByteBuf written = readAll(channel);
        assertThat(bytesOf(written), is(bytesOf("+simple\r\n")));
        written.release();
    }

    @Test
    public void shouldEncodeError() {
        RedisMessage msg = new ErrorRedisMessage("error1");

        boolean result = channel.writeOutbound(msg);
        assertThat(result, is(true));

        ByteBuf written = readAll(channel);
        assertThat(bytesOf(written), is(equalTo(bytesOf("-error1\r\n"))));
        written.release();
    }

    @Test
    public void shouldEncodeInteger() {
        RedisMessage msg = new IntegerRedisMessage(1234L);

        boolean result = channel.writeOutbound(msg);
        assertThat(result, is(true));

        ByteBuf written = readAll(channel);
        assertThat(bytesOf(written), is(equalTo(bytesOf(":1234\r\n"))));
        written.release();
    }

    @Test
    public void shouldEncodeBulkStringContent() {
        RedisMessage header = new BulkStringHeaderRedisMessage(16);
        RedisMessage body1 = new DefaultBulkStringRedisContent(byteBufOf("bulk\nstr").retain());
        RedisMessage body2 = new DefaultLastBulkStringRedisContent(byteBufOf("ing\ntest").retain());

        assertThat(channel.writeOutbound(header), is(true));
        assertThat(channel.writeOutbound(body1), is(true));
        assertThat(channel.writeOutbound(body2), is(true));

        ByteBuf written = readAll(channel);
        assertThat(bytesOf(written), is(equalTo(bytesOf("$16\r\nbulk\nstring\ntest\r\n"))));
        written.release();
    }

    @Test
    public void shouldEncodeFullBulkString() {
        ByteBuf bulkString = byteBufOf("bulk\nstring\ntest").retain();
        int length = bulkString.readableBytes();
        RedisMessage msg = new FullBulkStringRedisMessage(bulkString);

        boolean result = channel.writeOutbound(msg);
        assertThat(result, is(true));

        ByteBuf written = readAll(channel);
        assertThat(bytesOf(written), is(equalTo(bytesOf("$" + length + "\r\nbulk\nstring\ntest\r\n"))));
        written.release();
    }

    @Test
    public void shouldEncodeSimpleArray() {
        List<RedisMessage> children = new ArrayList<RedisMessage>();
        children.add(new FullBulkStringRedisMessage(byteBufOf("foo").retain()));
        children.add(new FullBulkStringRedisMessage(byteBufOf("bar").retain()));
        RedisMessage msg = new ArrayRedisMessage(children);

        boolean result = channel.writeOutbound(msg);
        assertThat(result, is(true));

        ByteBuf written = readAll(channel);
        assertThat(bytesOf(written), is(equalTo(bytesOf("*2\r\n$3\r\nfoo\r\n$3\r\nbar\r\n"))));
        written.release();
    }

    @Test
    public void shouldEncodeNullArray() {
        RedisMessage msg = ArrayRedisMessage.NULL_INSTANCE;

        boolean result = channel.writeOutbound(msg);
        assertThat(result, is(true));

        ByteBuf written = readAll(channel);
        assertThat(bytesOf(written), is(equalTo(bytesOf("*-1\r\n"))));
        written.release();
    }

    @Test
    public void shouldEncodeEmptyArray() {
        RedisMessage msg = ArrayRedisMessage.EMPTY_INSTANCE;

        boolean result = channel.writeOutbound(msg);
        assertThat(result, is(true));

        ByteBuf written = readAll(channel);
        assertThat(bytesOf(written), is(equalTo(bytesOf("*0\r\n"))));
        written.release();
    }

    @Test
    public void shouldEncodeNestedArray() {
        List<RedisMessage> grandChildren = new ArrayList<RedisMessage>();
        grandChildren.add(new FullBulkStringRedisMessage(byteBufOf("bar")));
        grandChildren.add(new IntegerRedisMessage(-1234L));
        List<RedisMessage> children = new ArrayList<RedisMessage>();
        children.add(new SimpleStringRedisMessage("foo"));
        children.add(new ArrayRedisMessage(grandChildren));
        RedisMessage msg = new ArrayRedisMessage(children);

        boolean result = channel.writeOutbound(msg);
        assertThat(result, is(true));

        ByteBuf written = readAll(channel);
        assertThat(bytesOf(written), is(equalTo(bytesOf("*2\r\n+foo\r\n*2\r\n$3\r\nbar\r\n:-1234\r\n"))));
        written.release();
    }

    @Test
    public void shouldEncodeNull() {
        boolean result = channel.writeOutbound(NullRedisMessage.INSTANCE);
        assertThat(result, is(true));

        ByteBuf written = readAll(channel);
        assertThat(bytesOf(written), is(bytesOf("_\r\n")));
        written.release();
    }

    @Test
    public void shouldEncodeBoolean() {
        boolean result = channel.writeOutbound(BooleanRedisMessage.TRUE_BOOLEAN_INSTANCE);
        assertThat(result, is(true));

        ByteBuf written = readAll(channel);
        assertThat(bytesOf(written), is(bytesOf("#t\r\n")));
        written.release();

        result = channel.writeOutbound(BooleanRedisMessage.FALSE_BOOLEAN_INSTANCE);
        assertThat(result, is(true));

        written = readAll(channel);
        assertThat(bytesOf(written), is(bytesOf("#f\r\n")));
        written.release();
    }

    @Test
    public void shouldEncodeDouble() {
        boolean result = channel.writeOutbound(new DoubleRedisMessage(1.23d));
        assertThat(result, is(true));

        ByteBuf written = readAll(channel);
        assertThat(bytesOf(written), is(bytesOf(",1.23\r\n")));
        written.release();

        result = channel.writeOutbound(DoubleRedisMessage.POSITIVE_INFINITY_DOUBLE_INSTANCE);
        assertThat(result, is(true));

        written = readAll(channel);
        assertThat(bytesOf(written), is(bytesOf(",inf\r\n")));
        written.release();
    }

    @Test
    public void shouldEncodeBigNumber() {
        BigNumberRedisMessage message =
            new BigNumberRedisMessage(bytesOf("3492890328409238509324850943850943825024385"));
        boolean result = channel.writeOutbound(message);
        assertThat(result, is(true));

        ByteBuf written = readAll(channel);
        assertThat(bytesOf(written), is(equalTo(bytesOf("(3492890328409238509324850943850943825024385\r\n"))));

        written.release();
    }

    private static ByteBuf readAll(EmbeddedChannel channel) {
        ByteBuf buf = Unpooled.buffer();
        ByteBuf read;
        while ((read = channel.readOutbound()) != null) {
            buf.writeBytes(read);
            read.release();
        }
        return buf;
    }

    @Test
    public void shouldEncodeFullBulkErrorString() {
        ByteBuf bulkString = byteBufOf("bulk\nstring\ntest").retain();
        int length = bulkString.readableBytes();
        RedisMessage msg = new FullBulkErrorStringRedisMessage(bulkString);

        boolean result = channel.writeOutbound(msg);
        assertThat(result, is(true));

        ByteBuf written = readAll(channel);
        assertThat(bytesOf(written), is(equalTo(bytesOf("!" + length + "\r\nbulk\nstring\ntest\r\n"))));
        written.release();
    }

    @Test
    public void shouldEncodeBulkErrorStringContent() {
        RedisMessage header = new BulkErrorStringHeaderRedisMessage(16);
        RedisMessage body1 = new DefaultBulkStringRedisContent(byteBufOf("bulk\nstr").retain());
        RedisMessage body2 = new DefaultLastBulkStringRedisContent(byteBufOf("ing\ntest").retain());

        assertThat(channel.writeOutbound(header), is(true));
        assertThat(channel.writeOutbound(body1), is(true));
        assertThat(channel.writeOutbound(body2), is(true));

        ByteBuf written = readAll(channel);
        assertThat(bytesOf(written), is(equalTo(bytesOf("!16\r\nbulk\nstring\ntest\r\n"))));
        written.release();
    }

    @Test
    public void shouldEncodeFullBulkVerbatimString() {
        ByteBuf bulkString = byteBufOf("txt:bulk\nstring\ntest").retain();
        int length = bulkString.readableBytes();
        RedisMessage msg = new FullBulkVerbatimStringRedisMessage(bulkString);

        boolean result = channel.writeOutbound(msg);
        assertThat(result, is(true));

        ByteBuf written = readAll(channel);
        assertThat(bytesOf(written), is(equalTo(bytesOf("=" + length + "\r\ntxt:bulk\nstring\ntest\r\n"))));
        written.release();
    }

    @Test
    public void shouldEncodeBulkVerbatimStringContent() {
        RedisMessage header = new BulkVerbatimStringHeaderRedisMessage(20);
        RedisMessage body1 = new DefaultBulkStringRedisContent(byteBufOf("txt:bulk\nstr").retain());
        RedisMessage body2 = new DefaultLastBulkStringRedisContent(byteBufOf("ing\ntest").retain());

        assertThat(channel.writeOutbound(header), is(true));
        assertThat(channel.writeOutbound(body1), is(true));
        assertThat(channel.writeOutbound(body2), is(true));

        ByteBuf written = readAll(channel);
        assertThat(bytesOf(written), is(equalTo(bytesOf("=20\r\ntxt:bulk\nstring\ntest\r\n"))));
        written.release();
    }

    @Test
    public void shouldEncodeSet() {
        Set<RedisMessage> children = new HashSet<RedisMessage>();
        children.add(new SimpleStringRedisMessage("apple"));
        children.add(new FullBulkStringRedisMessage(byteBufOf("orange").retain()));
        children.add(BooleanRedisMessage.TRUE_BOOLEAN_INSTANCE);
        children.add(new IntegerRedisMessage(100));
        RedisMessage msg = new SetRedisMessage(children);

        boolean result = channel.writeOutbound(msg);
        assertThat(result, is(true));

        ByteBuf written = readAll(channel);

        String encodeResult = new String(bytesOf(written));
        assertThat(encodeResult, is(startsWith("~4\r\n")));
        // out-of-order
        assertThat(encodeResult, is(containsString("$6\r\norange\r\n")));
        assertThat(encodeResult, is(containsString("#t\r\n")));
        assertThat(encodeResult, is(containsString(":100\r\n")));
        assertThat(encodeResult, is(containsString("+apple\r\n")));

        written.release();
    }

    @Test
    public void shouldEncodeMap() {
        HashMap<RedisMessage, RedisMessage> map = new HashMap<RedisMessage, RedisMessage>();
        map.put(new SimpleStringRedisMessage("first"), new IntegerRedisMessage(1));
        map.put(new SimpleStringRedisMessage("second"), new IntegerRedisMessage(2));
        MapRedisMessage mapMsg = new MapRedisMessage(map);

        boolean result = channel.writeOutbound(mapMsg);
        assertThat(result, is(true));

        ByteBuf written = readAll(channel);

        String encodeResult = new String(bytesOf(written));
        assertThat(encodeResult, is(startsWith("%2\r\n")));

        assertThat(encodeResult, is(containsString("+first\r\n:1\r\n")));
        assertThat(encodeResult, is(containsString("+second\r\n:2\r\n")));

        written.release();
    }

    @Test
    public void shouldEncodePush() {
        List<RedisMessage> children = new ArrayList<RedisMessage>();
        children.add(new FullBulkStringRedisMessage(byteBufOf("foo").retain()));
        children.add(new FullBulkStringRedisMessage(byteBufOf("bar").retain()));
        RedisMessage msg = new PushRedisMessage(children);

        boolean result = channel.writeOutbound(msg);
        assertThat(result, is(true));

        ByteBuf written = readAll(channel);
        assertThat(bytesOf(written), is(equalTo(bytesOf(">2\r\n$3\r\nfoo\r\n$3\r\nbar\r\n"))));
        written.release();
    }

}
