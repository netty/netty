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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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

    @Test
    public void shouldEncodeExtrasLongerThan127Bytes() {
        ByteBuf key = Unpooled.copiedBuffer("netty", CharsetUtil.UTF_8);
        ByteBuf extras = Unpooled.buffer().writeZero(200);
        ByteBuf content = Unpooled.copiedBuffer("memcache", CharsetUtil.UTF_8);
        FullBinaryMemcacheRequest request = new DefaultFullBinaryMemcacheRequest(key, extras, content);
        assertEquals(200 + 5 + 8, request.totalBodyLength());

        assertTrue(channel.writeOutbound(request));
        ByteBuf written = Unpooled.buffer();
        for (;;) {
            ByteBuf buf = channel.readOutbound();
            if (buf == null) {
                break;
            }
            written.writeBytes(buf);
            buf.release();
        }
        assertEquals(DEFAULT_HEADER_SIZE + 200 + 5 + 8, written.readableBytes());

        EmbeddedChannel decoder = new EmbeddedChannel(
                new BinaryMemcacheRequestDecoder(), new BinaryMemcacheObjectAggregator(Integer.MAX_VALUE));
        assertTrue(decoder.writeInbound(written));
        FullBinaryMemcacheRequest decoded = decoder.readInbound();
        assertEquals(200, decoded.extras().readableBytes());
        assertEquals("netty", decoded.key().toString(CharsetUtil.UTF_8));
        assertEquals("memcache", decoded.content().toString(CharsetUtil.UTF_8));
        decoded.release();
        assertNull(decoder.readInbound());
        assertFalse(decoder.finish());
    }

    @Test
    public void shouldRejectKeyLongerThan65535Bytes() {
        final ByteBuf key = Unpooled.buffer().writeZero(65536);
        try {
            assertThrows(IllegalArgumentException.class, new Executable() {
                @Override
                public void execute() {
                    new DefaultBinaryMemcacheRequest(key);
                }
            });
            final BinaryMemcacheRequest request = new DefaultBinaryMemcacheRequest();
            assertThrows(IllegalArgumentException.class, new Executable() {
                @Override
                public void execute() {
                    request.setKey(key);
                }
            });
            assertEquals(0, request.keyLength());
            assertEquals(0, request.totalBodyLength());
        } finally {
            key.release();
        }
    }

    @Test
    public void shouldRejectExtrasLongerThan255Bytes() {
        final ByteBuf extras = Unpooled.buffer().writeZero(256);
        try {
            assertThrows(IllegalArgumentException.class, new Executable() {
                @Override
                public void execute() {
                    new DefaultBinaryMemcacheRequest(Unpooled.EMPTY_BUFFER, extras);
                }
            });
            final BinaryMemcacheRequest request = new DefaultBinaryMemcacheRequest();
            assertThrows(IllegalArgumentException.class, new Executable() {
                @Override
                public void execute() {
                    request.setExtras(extras);
                }
            });
            assertEquals(0, request.extrasLength());
            assertEquals(0, request.totalBodyLength());
        } finally {
            extras.release();
        }
    }

    @Test
    public void shouldRejectKeyModifiedAfterSetKey() {
        ByteBuf key = Unpooled.copiedBuffer("k", CharsetUtil.UTF_8);
        BinaryMemcacheRequest request = new DefaultBinaryMemcacheRequest();
        request.setKey(key);
        // Append a smuggled FLUSH request header: magic 0x80, opcode FLUSH and 22 zero bytes.
        key.writeByte(0x80).writeByte(BinaryMemcacheOpcodes.FLUSH).writeZero(22);

        assertThrows(EncoderException.class, () -> channel.writeOutbound(request));
        assertEquals(0, key.refCnt());
        assertEquals(0, request.refCnt());
        assertNull(channel.readOutbound());
    }

    @Test
    public void shouldRejectExtrasModifiedAfterSetExtras() {
        ByteBuf extras = Unpooled.buffer().writeZero(8);
        BinaryMemcacheRequest request = new DefaultBinaryMemcacheRequest();
        request.setExtras(extras);
        extras.writeZero(4);

        assertThrows(EncoderException.class, () -> channel.writeOutbound(request));
        assertEquals(0, extras.refCnt());
        assertEquals(0, request.refCnt());
        assertNull(channel.readOutbound());
    }

    @Test
    public void shouldKeepOldKeyAndExtrasWhenRejectingOversizedReplacement() {
        ByteBuf key = Unpooled.copiedBuffer("netty", CharsetUtil.UTF_8);
        ByteBuf extras = Unpooled.buffer().writeZero(8);
        final BinaryMemcacheRequest request = new DefaultBinaryMemcacheRequest(key, extras);
        final ByteBuf oversizedKey = Unpooled.buffer().writeZero(65536);
        final ByteBuf oversizedExtras = Unpooled.buffer().writeZero(256);
        try {
            assertThrows(IllegalArgumentException.class, () -> request.setKey(oversizedKey));
            assertThrows(IllegalArgumentException.class, () -> request.setExtras(oversizedExtras));

            assertEquals(1, key.refCnt());
            assertEquals(1, extras.refCnt());
            assertSame(key, request.key());
            assertSame(extras, request.extras());
            assertEquals(5, request.keyLength());
            assertEquals(8, request.extrasLength());
            assertEquals(13, request.totalBodyLength());
        } finally {
            oversizedKey.release();
            oversizedExtras.release();
            request.release();
        }
    }

    @Test
    public void shouldEncodeMaximumKeyAndExtrasLengths() {
        ByteBuf key = Unpooled.buffer().writeZero(65535);
        ByteBuf extras = Unpooled.buffer().writeZero(255);
        BinaryMemcacheRequest request = new DefaultBinaryMemcacheRequest(key, extras);
        assertEquals(65535 + 255, request.totalBodyLength());

        assertTrue(channel.writeOutbound(request));
        ByteBuf written = channel.readOutbound();
        assertEquals(DEFAULT_HEADER_SIZE + 65535 + 255, written.readableBytes());

        EmbeddedChannel decoder = new EmbeddedChannel(
                new BinaryMemcacheRequestDecoder(), new BinaryMemcacheObjectAggregator(Integer.MAX_VALUE));
        assertTrue(decoder.writeInbound(written));
        FullBinaryMemcacheRequest decoded = decoder.readInbound();
        assertEquals(65535, decoded.key().readableBytes());
        assertEquals(255, decoded.extras().readableBytes());
        assertEquals(0, decoded.content().readableBytes());
        decoded.release();
        assertNull(decoder.readInbound());
        assertFalse(decoder.finish());
    }

    @Test
    public void shouldRejectOversizedKeyAndExtrasInFullResponse() {
        final ByteBuf key = Unpooled.buffer().writeZero(65536);
        final ByteBuf extras = Unpooled.buffer().writeZero(256);
        try {
            assertThrows(IllegalArgumentException.class,
                    () -> new DefaultFullBinaryMemcacheResponse(key, Unpooled.EMPTY_BUFFER));
            assertThrows(IllegalArgumentException.class,
                    () -> new DefaultFullBinaryMemcacheResponse(Unpooled.EMPTY_BUFFER, extras));
        } finally {
            key.release();
            extras.release();
        }
    }

    @Test
    public void shouldReleaseContentOfRejectedFullRequest() {
        ByteBuf key = Unpooled.copiedBuffer("k", CharsetUtil.UTF_8);
        ByteBuf content = Unpooled.copiedBuffer("memcache", CharsetUtil.UTF_8);
        FullBinaryMemcacheRequest request = new DefaultFullBinaryMemcacheRequest(key, null, content);
        key.writeZero(4);

        assertThrows(EncoderException.class, () -> channel.writeOutbound(request));
        assertEquals(0, key.refCnt());
        assertEquals(0, content.refCnt());
        assertEquals(0, request.refCnt());
        assertNull(channel.readOutbound());
    }
}
