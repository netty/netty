/*
 * Copyright 2013 The Netty Project
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
package io.netty.handler.codec.memcache.binary;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.EncoderException;
import io.netty.handler.codec.memcache.DefaultLastMemcacheContent;
import io.netty.handler.codec.memcache.DefaultMemcacheContent;
import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the correct functionality of the {@link AbstractBinaryMemcacheEncoder}.
 */
public class BinaryMemcacheEncoderTest {

    public static final int DEFAULT_HEADER_SIZE = 24;

    private EmbeddedChannel channel;

    @BeforeEach
    public void setup() {
        channel = new EmbeddedChannel(new BinaryMemcacheRequestEncoder());
    }

    @AfterEach
    public void teardown() {
        channel.finishAndReleaseAll();
    }

    @Test
    public void shouldEncodeDefaultHeader() {
        BinaryMemcacheRequest request = new DefaultBinaryMemcacheRequest();

        boolean result = channel.writeOutbound(request);
        assertTrue(result);

        ByteBuf written = channel.readOutbound();
        assertEquals(DEFAULT_HEADER_SIZE, written.readableBytes());
        assertEquals((byte) 0x80, written.readByte());
        assertEquals((byte) 0x00, written.readByte());
        written.release();
    }

    @Test
    public void shouldEncodeCustomHeader() {
        BinaryMemcacheRequest request = new DefaultBinaryMemcacheRequest();
        request.setMagic((byte) 0xAA);
        request.setOpcode(BinaryMemcacheOpcodes.GET);

        boolean result = channel.writeOutbound(request);
        assertTrue(result);

        ByteBuf written = channel.readOutbound();
        assertEquals(DEFAULT_HEADER_SIZE, written.readableBytes());
        assertEquals((byte) 0xAA, written.readByte());
        assertEquals(BinaryMemcacheOpcodes.GET, written.readByte());
        written.release();
    }

    @Test
    public void shouldEncodeExtras() {
        String extrasContent = "netty<3memcache";
        ByteBuf extras = Unpooled.copiedBuffer(extrasContent, CharsetUtil.UTF_8);
        int extrasLength = extras.readableBytes();

        BinaryMemcacheRequest request = new DefaultBinaryMemcacheRequest(Unpooled.EMPTY_BUFFER, extras);

        boolean result = channel.writeOutbound(request);
        assertTrue(result);

        ByteBuf written = channel.readOutbound();
        assertEquals(DEFAULT_HEADER_SIZE + extrasLength, written.readableBytes());
        written.skipBytes(DEFAULT_HEADER_SIZE);
        assertEquals(extrasContent, written.readSlice(extrasLength).toString(CharsetUtil.UTF_8));
        written.release();
    }

    @Test
    public void shouldEncodeKey() {
        ByteBuf key = Unpooled.copiedBuffer("netty", CharsetUtil.UTF_8);
        int keyLength = key.readableBytes();

        BinaryMemcacheRequest request = new DefaultBinaryMemcacheRequest(key);

        boolean result = channel.writeOutbound(request);
        assertTrue(result);

        ByteBuf written = channel.readOutbound();
        assertEquals(DEFAULT_HEADER_SIZE + keyLength, written.readableBytes());
        written.skipBytes(DEFAULT_HEADER_SIZE);
        assertEquals("netty", written.readSlice(keyLength).toString(CharsetUtil.UTF_8));
        written.release();
    }

    @Test
    public void shouldEncodeContent() {
        DefaultMemcacheContent content1 =
            new DefaultMemcacheContent(Unpooled.copiedBuffer("Netty", CharsetUtil.UTF_8));
        DefaultLastMemcacheContent content2 =
            new DefaultLastMemcacheContent(Unpooled.copiedBuffer(" Rocks!", CharsetUtil.UTF_8));
        int totalBodyLength = content1.content().readableBytes() + content2.content().readableBytes();

        BinaryMemcacheRequest request = new DefaultBinaryMemcacheRequest();
        request.setTotalBodyLength(totalBodyLength);

        boolean result = channel.writeOutbound(request);
        assertTrue(result);
        result = channel.writeOutbound(content1);
        assertTrue(result);
        result = channel.writeOutbound(content2);
        assertTrue(result);

        ByteBuf written = channel.readOutbound();
        assertEquals(DEFAULT_HEADER_SIZE, written.readableBytes());
        written.release();

        written = channel.readOutbound();
        assertEquals(content1.content().readableBytes(), written.readableBytes());
        assertEquals("Netty",
                written.readSlice(content1.content().readableBytes()).toString(CharsetUtil.UTF_8)
        );
        written.release();

        written = channel.readOutbound();
        assertEquals(content2.content().readableBytes(), written.readableBytes());
        assertEquals(" Rocks!",
                written.readSlice(content2.content().readableBytes()).toString(CharsetUtil.UTF_8)
        );
        written.release();
    }

    @Test
    public void shouldFailWithoutLastContent() {
        channel.writeOutbound(new DefaultMemcacheContent(Unpooled.EMPTY_BUFFER));
        assertThrows(EncoderException.class, new Executable() {
            @Override
            public void execute() {
                channel.writeOutbound(new DefaultBinaryMemcacheRequest());
            }
        });
    }
}
