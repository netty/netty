/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
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
import io.netty.handler.codec.memcache.LastMemcacheContent;
import io.netty.handler.codec.memcache.MemcacheContent;
import io.netty.util.CharsetUtil;
import org.hamcrest.CoreMatchers;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.Charset;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.hamcrest.core.IsNull.nullValue;

/**
 * Verifies the correct functionality of the {@link BinaryMemcacheObjectAggregator}.
 */
public class BinaryMemcacheObjectAggregatorTest {

    private static final byte[] SET_REQUEST_WITH_CONTENT = new byte[]{
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

        FullBinaryMemcacheRequest request = (FullBinaryMemcacheRequest) channel.readInbound();

        assertThat(request, instanceOf(FullBinaryMemcacheRequest.class));
        assertThat(request, notNullValue());
        assertThat(request.getHeader(), notNullValue());
        assertThat(request.getKey(), notNullValue());
        assertThat(request.getExtras(), nullValue());

        assertThat(request.content().readableBytes(), is(8));
        assertThat(request.content().readByte(), is((byte) 0x01));
        assertThat(request.content().readByte(), is((byte) 0x02));

        assertThat(channel.readInbound(), nullValue());
    }

}
