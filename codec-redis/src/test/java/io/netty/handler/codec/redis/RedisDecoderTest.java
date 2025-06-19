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
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.DecoderException;
import io.netty.util.IllegalReferenceCountException;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.List;

import static io.netty.handler.codec.redis.RedisCodecTestUtil.*;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the correct functionality of the {@link RedisDecoder} and {@link RedisArrayAggregator}.
 */
public class RedisDecoderTest {

    private EmbeddedChannel channel;

    @BeforeEach
    public void setup() throws Exception {
        channel = newChannel(false);
    }

    private static EmbeddedChannel newChannel(boolean decodeInlineCommands) {
        return new EmbeddedChannel(
                new RedisDecoder(decodeInlineCommands),
                new RedisBulkStringAggregator(),
                new RedisArrayAggregator());
    }

    @AfterEach
    public void teardown() throws Exception {
        assertFalse(channel.finish());
    }

    @Test
    public void splitEOLDoesNotInfiniteLoop() throws Exception {
        assertFalse(channel.writeInbound(byteBufOf("$6\r\nfoobar\r")));
        assertTrue(channel.writeInbound(byteBufOf("\n")));

        RedisMessage msg = channel.readInbound();
        assertTrue(msg instanceof FullBulkStringRedisMessage);
        ReferenceCountUtil.release(msg);
    }

    @Test
    public void shouldNotDecodeInlineCommandByDefault() {
        assertThrows(DecoderException.class, new Executable() {
            @Override
            public void execute() {
                assertFalse(channel.writeInbound(byteBufOf("P")));
                assertFalse(channel.writeInbound(byteBufOf("I")));
                assertFalse(channel.writeInbound(byteBufOf("N")));
                assertFalse(channel.writeInbound(byteBufOf("G")));
                assertTrue(channel.writeInbound(byteBufOf("\r\n")));

                channel.readInbound();
            }
        });
    }

    @Test
    public void shouldDecodeInlineCommand() {
        channel = newChannel(true);

        assertFalse(channel.writeInbound(byteBufOf("P")));
        assertFalse(channel.writeInbound(byteBufOf("I")));
        assertFalse(channel.writeInbound(byteBufOf("N")));
        assertFalse(channel.writeInbound(byteBufOf("G")));
        assertTrue(channel.writeInbound(byteBufOf("\r\n")));

        InlineCommandRedisMessage msg = channel.readInbound();

        assertEquals("PING", msg.content());

        ReferenceCountUtil.release(msg);
    }

    @Test
    public void shouldDecodeSimpleString() {
        assertFalse(channel.writeInbound(byteBufOf("+")));
        assertFalse(channel.writeInbound(byteBufOf("O")));
        assertFalse(channel.writeInbound(byteBufOf("K")));
        assertTrue(channel.writeInbound(byteBufOf("\r\n")));

        SimpleStringRedisMessage msg = channel.readInbound();

        assertEquals("OK", msg.content());

        ReferenceCountUtil.release(msg);
    }

    @Test
    public void shouldDecodeTwoSimpleStrings() {
        assertFalse(channel.writeInbound(byteBufOf("+")));
        assertFalse(channel.writeInbound(byteBufOf("O")));
        assertFalse(channel.writeInbound(byteBufOf("K")));
        assertTrue(channel.writeInbound(byteBufOf("\r\n+SEC")));
        assertTrue(channel.writeInbound(byteBufOf("OND\r\n")));

        SimpleStringRedisMessage msg1 = channel.readInbound();
        assertEquals("OK", msg1.content());
        ReferenceCountUtil.release(msg1);

        SimpleStringRedisMessage msg2 = channel.readInbound();
        assertEquals("SECOND", msg2.content());
        ReferenceCountUtil.release(msg2);
    }

    @Test
    public void shouldDecodeError() {
        String content = "ERROR sample message";
        assertFalse(channel.writeInbound(byteBufOf("-")));
        assertFalse(channel.writeInbound(byteBufOf(content)));
        assertFalse(channel.writeInbound(byteBufOf("\r")));
        assertTrue(channel.writeInbound(byteBufOf("\n")));

        ErrorRedisMessage msg = channel.readInbound();

        assertEquals(content, msg.content());

        ReferenceCountUtil.release(msg);
    }

    @Test
    public void shouldDecodeInteger() {
        long value = 1234L;
        byte[] content = bytesOf(value);
        assertFalse(channel.writeInbound(byteBufOf(":")));
        assertFalse(channel.writeInbound(byteBufOf(content)));
        assertTrue(channel.writeInbound(byteBufOf("\r\n")));

        IntegerRedisMessage msg = channel.readInbound();

        assertEquals(value, msg.value());

        ReferenceCountUtil.release(msg);
    }

    @Test
    public void shouldDecodeBulkString() {
        String buf1 = "bulk\nst";
        String buf2 = "ring\ntest\n1234";
        byte[] content = bytesOf(buf1 + buf2);
        assertFalse(channel.writeInbound(byteBufOf("$")));
        assertFalse(channel.writeInbound(byteBufOf(Integer.toString(content.length))));
        assertFalse(channel.writeInbound(byteBufOf("\r\n")));
        assertFalse(channel.writeInbound(byteBufOf(buf1)));
        assertFalse(channel.writeInbound(byteBufOf(buf2)));
        assertTrue(channel.writeInbound(byteBufOf("\r\n")));

        FullBulkStringRedisMessage msg = channel.readInbound();

        assertArrayEquals(content, bytesOf(msg.content()));

        ReferenceCountUtil.release(msg);
    }

    @Test
    public void shouldDecodeEmptyBulkString() {
        byte[] content = bytesOf("");
        assertFalse(channel.writeInbound(byteBufOf("$")));
        assertFalse(channel.writeInbound(byteBufOf(Integer.toString(content.length))));
        assertFalse(channel.writeInbound(byteBufOf("\r\n")));
        assertFalse(channel.writeInbound(byteBufOf(content)));
        assertTrue(channel.writeInbound(byteBufOf("\r\n")));

        FullBulkStringRedisMessage msg = channel.readInbound();

        assertArrayEquals(content, bytesOf(msg.content()));

        ReferenceCountUtil.release(msg);
    }

    @Test
    public void shouldDecodeNullBulkString() {
        assertFalse(channel.writeInbound(byteBufOf("$")));
        assertFalse(channel.writeInbound(byteBufOf(Integer.toString(-1))));
        assertTrue(channel.writeInbound(byteBufOf("\r\n")));

        assertTrue(channel.writeInbound(byteBufOf("$")));
        assertTrue(channel.writeInbound(byteBufOf(Integer.toString(-1))));
        assertTrue(channel.writeInbound(byteBufOf("\r\n")));

        FullBulkStringRedisMessage msg1 = channel.readInbound();
        assertTrue(msg1.isNull());
        ReferenceCountUtil.release(msg1);

        FullBulkStringRedisMessage msg2 = channel.readInbound();
        assertTrue(msg2.isNull());
        ReferenceCountUtil.release(msg2);

        FullBulkStringRedisMessage msg3 = channel.readInbound();
        assertNull(msg3);
    }

    @Test
    public void shouldDecodeSimpleArray() throws Exception {
        assertFalse(channel.writeInbound(byteBufOf("*3\r\n")));
        assertFalse(channel.writeInbound(byteBufOf(":1234\r\n")));
        assertFalse(channel.writeInbound(byteBufOf("+sim")));
        assertFalse(channel.writeInbound(byteBufOf("ple\r\n-err")));
        assertTrue(channel.writeInbound(byteBufOf("or\r\n")));

        ArrayRedisMessage msg = channel.readInbound();
        List<RedisMessage> children = msg.children();

        assertEquals(3, msg.children().size());

        assertInstanceOf(IntegerRedisMessage.class, children.get(0));
        assertEquals(1234L, ((IntegerRedisMessage) children.get(0)).value());
        assertInstanceOf(SimpleStringRedisMessage.class, children.get(1));
        assertEquals("simple", ((SimpleStringRedisMessage) children.get(1)).content());
        assertInstanceOf(ErrorRedisMessage.class, children.get(2));
        assertEquals("error", ((ErrorRedisMessage) children.get(2)).content());

        ReferenceCountUtil.release(msg);
    }

    @Test
    public void shouldDecodeNestedArray() throws Exception {
        ByteBuf buf = Unpooled.buffer();
        buf.writeBytes(byteBufOf("*2\r\n"));
        buf.writeBytes(byteBufOf("*3\r\n:1\r\n:2\r\n:3\r\n"));
        buf.writeBytes(byteBufOf("*2\r\n+Foo\r\n-Bar\r\n"));
        assertTrue(channel.writeInbound(buf));

        ArrayRedisMessage msg = channel.readInbound();
        List<RedisMessage> children = msg.children();

        assertEquals(2, msg.children().size());

        ArrayRedisMessage intArray = (ArrayRedisMessage) children.get(0);
        ArrayRedisMessage strArray = (ArrayRedisMessage) children.get(1);

        assertEquals(3, intArray.children().size());
        assertEquals(1L, ((IntegerRedisMessage) intArray.children().get(0)).value());
        assertEquals(2L, ((IntegerRedisMessage) intArray.children().get(1)).value());
        assertEquals(3L, ((IntegerRedisMessage) intArray.children().get(2)).value());

        assertEquals(2, strArray.children().size());
        assertEquals("Foo", ((SimpleStringRedisMessage) strArray.children().get(0)).content());
        assertEquals("Bar", ((ErrorRedisMessage) strArray.children().get(1)).content());

        ReferenceCountUtil.release(msg);
    }

    @Test
    public void shouldErrorOnDoubleReleaseArrayReferenceCounted() {
        ByteBuf buf = Unpooled.buffer();
        buf.writeBytes(byteBufOf("*2\r\n"));
        buf.writeBytes(byteBufOf("*3\r\n:1\r\n:2\r\n:3\r\n"));
        buf.writeBytes(byteBufOf("*2\r\n+Foo\r\n-Bar\r\n"));
        assertTrue(channel.writeInbound(buf));

        final ArrayRedisMessage msg = channel.readInbound();

        ReferenceCountUtil.release(msg);
        assertThrows(IllegalReferenceCountException.class, new Executable() {
            @Override
            public void execute() {
                ReferenceCountUtil.release(msg);
            }
        });
    }

    @Test
    public void shouldErrorOnReleaseArrayChildReferenceCounted() {
        ByteBuf buf = Unpooled.buffer();
        buf.writeBytes(byteBufOf("*2\r\n"));
        buf.writeBytes(byteBufOf("*3\r\n:1\r\n:2\r\n:3\r\n"));
        buf.writeBytes(byteBufOf("$3\r\nFoo\r\n"));
        assertTrue(channel.writeInbound(buf));

        ArrayRedisMessage msg = channel.readInbound();

        final List<RedisMessage> children = msg.children();
        ReferenceCountUtil.release(msg);
        assertThrows(IllegalReferenceCountException.class, new Executable() {
            @Override
            public void execute() {
                ReferenceCountUtil.release(children.get(1));
            }
        });
    }

    @Test
    public void shouldErrorOnReleasecontentOfArrayChildReferenceCounted() throws Exception {
        ByteBuf buf = Unpooled.buffer();
        buf.writeBytes(byteBufOf("*2\r\n"));
        buf.writeBytes(byteBufOf("$3\r\nFoo\r\n$3\r\nBar\r\n"));
        assertTrue(channel.writeInbound(buf));

        ArrayRedisMessage msg = channel.readInbound();

        List<RedisMessage> children = msg.children();
        final ByteBuf childBuf = ((FullBulkStringRedisMessage) children.get(0)).content();
        ReferenceCountUtil.release(msg);
        assertThrows(IllegalReferenceCountException.class, new Executable() {
            @Override
            public void execute() {
                ReferenceCountUtil.release(childBuf);
            }
        });
    }

    @Test
    public void testPredefinedMessagesNotEqual() {
        // both EMPTY_INSTANCE and NULL_INSTANCE have EMPTY_BUFFER as their 'data',
        // however we need to check that they are not equal between themselves.
        assertNotEquals(FullBulkStringRedisMessage.EMPTY_INSTANCE, FullBulkStringRedisMessage.NULL_INSTANCE);
        assertNotEquals(FullBulkStringRedisMessage.NULL_INSTANCE, FullBulkStringRedisMessage.EMPTY_INSTANCE);
    }
}
