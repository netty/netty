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
import io.netty.handler.codec.EncoderException;
import io.netty.handler.codec.memcache.DefaultLastMemcacheContent;
import io.netty.handler.codec.memcache.DefaultMemcacheContent;
import io.netty.util.CharsetUtil;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.*;

/**
 * Verifies the correct functionality of the {@link BinaryMemcacheEncoder}.
 */
public class BinaryMemcacheEncoderTest {

    public static final int DEFAULT_HEADER_SIZE = 24;

    private EmbeddedChannel channel;

    @Before
    public void setup() throws Exception {
        channel = new EmbeddedChannel(new BinaryMemcacheRequestEncoder());
    }

    @Test
    public void shouldEncodeDefaultHeader() {
        BinaryMemcacheRequestHeader header = new DefaultBinaryMemcacheRequestHeader();
        BinaryMemcacheRequest request = new DefaultBinaryMemcacheRequest(header);

        boolean result = channel.writeOutbound(request);
        assertThat(result, is(true));

        ByteBuf written = (ByteBuf) channel.readOutbound();
        assertThat(written.readableBytes(), is(DEFAULT_HEADER_SIZE));
        assertThat(written.readByte(), is((byte) 0x80));
        assertThat(written.readByte(), is((byte) 0x00));
    }

    @Test
    public void shouldEncodeCustomHeader() {
        BinaryMemcacheRequestHeader header = new DefaultBinaryMemcacheRequestHeader();
        header.setMagic((byte) 0xAA);
        header.setOpcode(BinaryMemcacheOpcodes.GET);
        BinaryMemcacheRequest request = new DefaultBinaryMemcacheRequest(header);

        boolean result = channel.writeOutbound(request);
        assertThat(result, is(true));

        ByteBuf written = (ByteBuf) channel.readOutbound();
        assertThat(written.readableBytes(), is(DEFAULT_HEADER_SIZE));
        assertThat(written.readByte(), is((byte) 0xAA));
        assertThat(written.readByte(), is(BinaryMemcacheOpcodes.GET));
    }

    @Test
    public void shouldEncodeExtras() {
        String extrasContent = "netty<3memcache";
        ByteBuf extras = Unpooled.copiedBuffer(extrasContent, CharsetUtil.UTF_8);
        int extrasLength = extras.readableBytes();
        BinaryMemcacheRequestHeader header = new DefaultBinaryMemcacheRequestHeader();
        header.setExtrasLength((byte) extrasLength);
        BinaryMemcacheRequest request = new DefaultBinaryMemcacheRequest(header, extras);

        boolean result = channel.writeOutbound(request);
        assertThat(result, is(true));

        ByteBuf written = (ByteBuf) channel.readOutbound();
        assertThat(written.readableBytes(), is(DEFAULT_HEADER_SIZE + extrasLength));
        written.readBytes(DEFAULT_HEADER_SIZE);
        assertThat(written.readBytes(extrasLength).toString(CharsetUtil.UTF_8), equalTo(extrasContent));
    }

    @Test
    public void shouldEncodeKey() {
        String key = "netty";
        int keyLength = key.length();
        BinaryMemcacheRequestHeader header = new DefaultBinaryMemcacheRequestHeader();
        header.setKeyLength((byte) keyLength);
        BinaryMemcacheRequest request = new DefaultBinaryMemcacheRequest(header, key);

        boolean result = channel.writeOutbound(request);
        assertThat(result, is(true));

        ByteBuf written = (ByteBuf) channel.readOutbound();
        assertThat(written.readableBytes(), is(DEFAULT_HEADER_SIZE + keyLength));
        written.readBytes(DEFAULT_HEADER_SIZE);
        assertThat(written.readBytes(keyLength).toString(CharsetUtil.UTF_8), equalTo(key));
    }

    @Test
    public void shouldEncodeContent() {
        DefaultMemcacheContent content1 =
            new DefaultMemcacheContent(Unpooled.copiedBuffer("Netty", CharsetUtil.UTF_8));
        DefaultLastMemcacheContent content2 =
            new DefaultLastMemcacheContent(Unpooled.copiedBuffer(" Rocks!", CharsetUtil.UTF_8));
        int totalBodyLength = content1.content().readableBytes() + content2.content().readableBytes();

        BinaryMemcacheRequestHeader header = new DefaultBinaryMemcacheRequestHeader();
        header.setTotalBodyLength(totalBodyLength);
        BinaryMemcacheRequest request = new DefaultBinaryMemcacheRequest(header);

        boolean result = channel.writeOutbound(request);
        assertThat(result, is(true));
        result = channel.writeOutbound(content1);
        assertThat(result, is(true));
        result = channel.writeOutbound(content2);
        assertThat(result, is(true));

        ByteBuf written = (ByteBuf) channel.readOutbound();
        assertThat(written.readableBytes(), is(DEFAULT_HEADER_SIZE));
        written = (ByteBuf) channel.readOutbound();
        assertThat(written.readableBytes(), is(content1.content().readableBytes()));
        assertThat(
            written.readBytes(content1.content().readableBytes()).toString(CharsetUtil.UTF_8),
            is("Netty")
        );
        written = (ByteBuf) channel.readOutbound();
        assertThat(written.readableBytes(), is(content2.content().readableBytes()));
        assertThat(
            written.readBytes(content2.content().readableBytes()).toString(CharsetUtil.UTF_8),
            is(" Rocks!")
        );
    }

    @Test(expected = EncoderException.class)
    public void shouldFailWithoutLastContent() {
        channel.writeOutbound(new DefaultMemcacheContent(Unpooled.EMPTY_BUFFER));
        channel.writeOutbound(
            new DefaultBinaryMemcacheRequest(new DefaultBinaryMemcacheRequestHeader()));
    }

}
