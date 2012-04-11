/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.handler.codec.redis;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.embedder.DecoderEmbedder;
import org.jboss.netty.util.CharsetUtil;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.jboss.netty.buffer.ChannelBuffers.wrappedBuffer;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class RedisCodecTest {

    private DecoderEmbedder<ChannelBuffer> embedder;

    @Before
    public void setUp() {
        embedder = new DecoderEmbedder<ChannelBuffer>(new RedisReplyDecoder());
    }

    @Test
    public void decodeReplies() throws IOException {
        {
            Object receive = decode("+OK\r\n".getBytes());
            assertTrue(receive instanceof StatusReply);
            assertEquals("OK", ((StatusReply) receive).data().toString(CharsetUtil.UTF_8));
        }
        {
            Object receive = decode("-ERROR\r\n".getBytes());
            assertTrue(receive instanceof ErrorReply);
            assertEquals("ERROR", ((ErrorReply) receive).data().toString(CharsetUtil.UTF_8));
        }
        {
            Object receive = decode(":123\r\n".getBytes());
            assertTrue(receive instanceof IntegerReply);
            assertEquals(123, ((IntegerReply) receive).value());
        }
        {
            Object receive = decode("$5\r\nnetty\r\n".getBytes());
            assertTrue(receive instanceof BulkReply);
            assertEquals("netty", ((BulkReply) receive).data().toString(CharsetUtil.UTF_8));
        }
        {
            Object receive = decode("*2\r\n$5\r\nnetty\r\n$5\r\nrules\r\n".getBytes());
            assertTrue(receive instanceof MultiBulkReply);
            assertEquals("netty", ((ChannelBuffer) ((MultiBulkReply) receive).values()[0]).toString(CharsetUtil.UTF_8));
            assertEquals("rules", ((ChannelBuffer) ((MultiBulkReply) receive).values()[1]).toString(CharsetUtil.UTF_8));
        }
    }

    private Object decode(byte[] bytes) {
        embedder.offer(wrappedBuffer(bytes));
        return embedder.poll();
    }

    @Test
    public void encodeCommands() throws IOException {
        String setCommand = "*3\r\n" +
                "$3\r\n" +
                "SET\r\n" +
                "$5\r\n" +
                "mykey\r\n" +
                "$7\r\n" +
                "myvalue\r\n";
        Command command = new Command("SET", new Object[] { "mykey", "myvalue" } );
        ChannelBuffer cb = ChannelBuffers.dynamicBuffer();
        command.write(cb);
        assertEquals(setCommand, cb.toString(CharsetUtil.US_ASCII));
        command = new Command("SET", "mykey", "myvalue");
        cb = ChannelBuffers.dynamicBuffer();
        command.write(cb);
        assertEquals(setCommand, cb.toString(CharsetUtil.US_ASCII));
    }

    @Test
    public void testReplayDecoding() {
        {
            embedder.offer(wrappedBuffer("*2\r\n$5\r\nnetty\r\n".getBytes()));
            Object receive = embedder.poll();
            assertNull(receive);
            embedder.offer(wrappedBuffer("$5\r\nrules\r\n".getBytes()));
            receive = embedder.poll();
            assertTrue(receive instanceof MultiBulkReply);
            assertEquals("netty", ((ChannelBuffer) ((MultiBulkReply) receive).values()[0]).toString(CharsetUtil.UTF_8));
            assertEquals("rules", ((ChannelBuffer) ((MultiBulkReply) receive).values()[1]).toString(CharsetUtil.UTF_8));
        }
        {
            embedder.offer(wrappedBuffer("*2\r\n$5\r\nnetty\r\n$5\r\nr".getBytes()));
            Object receive = embedder.poll();
            assertNull(receive);
            embedder.offer(wrappedBuffer("ules\r\n".getBytes()));
            receive = embedder.poll();
            assertTrue(receive instanceof MultiBulkReply);
            assertEquals("netty", ((ChannelBuffer) ((MultiBulkReply) receive).values()[0]).toString(CharsetUtil.UTF_8));
            assertEquals("rules", ((ChannelBuffer) ((MultiBulkReply) receive).values()[1]).toString(CharsetUtil.UTF_8));
        }
        {
            embedder.offer(wrappedBuffer("*2".getBytes()));
            Object receive = embedder.poll();
            assertNull(receive);
            embedder.offer(wrappedBuffer("\r\n$5\r\nnetty\r\n$5\r\nrules\r\n".getBytes()));
            receive = embedder.poll();
            assertTrue(receive instanceof MultiBulkReply);
            assertEquals("netty", ((ChannelBuffer) ((MultiBulkReply) receive).values()[0]).toString(CharsetUtil.UTF_8));
            assertEquals("rules", ((ChannelBuffer) ((MultiBulkReply) receive).values()[1]).toString(CharsetUtil.UTF_8));
        }
        {
            embedder.offer(wrappedBuffer("*2\r\n$5\r\nnetty\r\n$5\r\nrules\r".getBytes()));
            Object receive = embedder.poll();
            assertNull(receive);
            embedder.offer(wrappedBuffer("\n".getBytes()));
            receive = embedder.poll();
            assertTrue(receive instanceof MultiBulkReply);
            assertEquals("netty", ((ChannelBuffer) ((MultiBulkReply) receive).values()[0]).toString(CharsetUtil.UTF_8));
            assertEquals("rules", ((ChannelBuffer) ((MultiBulkReply) receive).values()[1]).toString(CharsetUtil.UTF_8));
        }
    }
}
