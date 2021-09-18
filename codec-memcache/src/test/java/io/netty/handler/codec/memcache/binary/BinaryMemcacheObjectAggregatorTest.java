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
import io.netty.handler.codec.memcache.DefaultLastMemcacheContent;
import io.netty.handler.codec.memcache.DefaultMemcacheContent;
import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the correct functionality of the {@link BinaryMemcacheObjectAggregator}.
 */
public class BinaryMemcacheObjectAggregatorTest {

    private static final byte[] SET_REQUEST_WITH_CONTENT = {
        (byte) 0x80, 0x01, 0x00, 0x03,
        0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x0B,
        0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00,
        0x66, 0x6f, 0x6f,
        0x01, 0x02, 0x03, 0x04,
        0x05, 0x06, 0x07, 0x08
    };

    public static final int MAX_CONTENT_SIZE = 2 << 10;

    private EmbeddedChannel channel;

    @Test
    public void shouldAggregateChunksOnDecode() {
        int smallBatchSize = 2;
        channel = new EmbeddedChannel(
            new BinaryMemcacheRequestDecoder(smallBatchSize),
            new BinaryMemcacheObjectAggregator(MAX_CONTENT_SIZE));

        ByteBuf incoming = Unpooled.buffer();
        incoming.writeBytes(SET_REQUEST_WITH_CONTENT);
        channel.writeInbound(incoming);

        FullBinaryMemcacheRequest request = channel.readInbound();

        assertThat(request, instanceOf(FullBinaryMemcacheRequest.class));
        assertThat(request, notNullValue());
        assertThat(request.key(), notNullValue());
        assertThat(request.extras(), nullValue());

        assertThat(request.content().readableBytes(), is(8));
        assertThat(request.content().readByte(), is((byte) 0x01));
        assertThat(request.content().readByte(), is((byte) 0x02));
        request.release();

        assertThat(channel.readInbound(), nullValue());

        assertFalse(channel.finish());
    }

    @Test
    public void shouldRetainByteBufWhenAggregating() {
        channel = new EmbeddedChannel(
                new BinaryMemcacheRequestEncoder(),
                new BinaryMemcacheRequestDecoder(),
                new BinaryMemcacheObjectAggregator(MAX_CONTENT_SIZE));

        ByteBuf key = Unpooled.copiedBuffer("Netty", CharsetUtil.UTF_8);
        ByteBuf extras = Unpooled.copiedBuffer("extras", CharsetUtil.UTF_8);
        BinaryMemcacheRequest request = new DefaultBinaryMemcacheRequest(key, extras);

        DefaultMemcacheContent content1 =
                new DefaultMemcacheContent(Unpooled.copiedBuffer("Netty", CharsetUtil.UTF_8));
        DefaultLastMemcacheContent content2 =
                new DefaultLastMemcacheContent(Unpooled.copiedBuffer(" Rocks!", CharsetUtil.UTF_8));
        int totalBodyLength = key.readableBytes() + extras.readableBytes() +
                content1.content().readableBytes() + content2.content().readableBytes();
        request.setTotalBodyLength(totalBodyLength);

        assertTrue(channel.writeOutbound(request, content1, content2));

        assertThat(channel.outboundMessages().size(), is(3));
        assertTrue(channel.writeInbound(channel.readOutbound(), channel.readOutbound(), channel.readOutbound()));

        FullBinaryMemcacheRequest read = channel.readInbound();
        assertThat(read, notNullValue());
        assertThat(read.key().toString(CharsetUtil.UTF_8), is("Netty"));
        assertThat(read.extras().toString(CharsetUtil.UTF_8), is("extras"));
        assertThat(read.content().toString(CharsetUtil.UTF_8), is("Netty Rocks!"));

        read.release();
        assertFalse(channel.finish());
    }
}
