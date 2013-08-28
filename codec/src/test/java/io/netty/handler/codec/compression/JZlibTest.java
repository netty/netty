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
package io.netty.handler.codec.compression;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.CharsetUtil;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;

public class JZlibTest extends ZlibTest {

    @Override
    protected ZlibEncoder createEncoder(ZlibWrapper wrapper) {
        return new JZlibEncoder(wrapper);
    }

    @Override
    protected ZlibDecoder createDecoder(ZlibWrapper wrapper) {
        return new JZlibDecoder(wrapper);
    }

    @Test
    public void testZLIB_OR_NONE() throws Exception {
        ByteBuf data = Unpooled.copiedBuffer("test", CharsetUtil.UTF_8);

        EmbeddedChannel chEncoder = new EmbeddedChannel(new JZlibEncoder(ZlibWrapper.NONE));

        chEncoder.writeOutbound(data.copy());
        assertTrue(chEncoder.finish());

        ByteBuf deflatedData = (ByteBuf) chEncoder.readOutbound();

        EmbeddedChannel chDecoderZlibOrNone = new EmbeddedChannel(new JZlibDecoder(ZlibWrapper.ZLIB_OR_NONE));

        chDecoderZlibOrNone.writeInbound(deflatedData);
        assertTrue(chDecoderZlibOrNone.finish());

        assertEquals(data, chDecoderZlibOrNone.readInbound());
    }

    @Test
    public void testZLIB_OR_NONE2() throws Exception {
        ByteBuf data = Unpooled.copiedBuffer("test", CharsetUtil.UTF_8);

        EmbeddedChannel chEncoder = new EmbeddedChannel(new JZlibEncoder(ZlibWrapper.ZLIB));

        chEncoder.writeOutbound(data.copy());
        assertTrue(chEncoder.finish());

        ByteBuf deflatedData = (ByteBuf) chEncoder.readOutbound();

        EmbeddedChannel chDecoderZlibOrNone = new EmbeddedChannel(new JZlibDecoder(ZlibWrapper.ZLIB_OR_NONE));

        chDecoderZlibOrNone.writeInbound(deflatedData);
        assertTrue(chDecoderZlibOrNone.finish());

        assertEquals(data, chDecoderZlibOrNone.readInbound());
    }

    @Test
    public void testZLIB_OR_NONE3() throws Exception {
        ByteBuf data = Unpooled.copiedBuffer("test", CharsetUtil.UTF_8);

        EmbeddedChannel chEncoder = new EmbeddedChannel(new JZlibEncoder(ZlibWrapper.GZIP));

        chEncoder.writeOutbound(data.copy());
        assertTrue(chEncoder.finish());

        ByteBuf deflatedData = (ByteBuf) chEncoder.readOutbound();

        EmbeddedChannel chDecoderZlibOrNone = new EmbeddedChannel(new JZlibDecoder(ZlibWrapper.ZLIB_OR_NONE));

        chDecoderZlibOrNone.writeInbound(deflatedData);
        assertTrue(chDecoderZlibOrNone.finish());

        assertEquals(data, chDecoderZlibOrNone.readInbound());
    }
}
