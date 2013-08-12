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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class JdkZlibTest {

    @Test
    public void testZLIB() throws Exception {
        ByteBuf data = Unpooled.copiedBuffer("test", CharsetUtil.UTF_8);

        EmbeddedChannel chEncoder = new EmbeddedChannel(new JdkZlibEncoder(ZlibWrapper.ZLIB));

        chEncoder.writeOutbound(data.copy());
        assertTrue(chEncoder.finish());

        ByteBuf deflatedData = (ByteBuf) chEncoder.readOutbound();

        EmbeddedChannel chDecoderZlib = new EmbeddedChannel(new JdkZlibDecoder(ZlibWrapper.ZLIB));

        chDecoderZlib.writeInbound(deflatedData.copy());
        assertTrue(chDecoderZlib.finish());

        assertEquals(data, chDecoderZlib.readInbound());
    }

    @Test
    public void testNONE() throws Exception {
        ByteBuf data = Unpooled.copiedBuffer("test", CharsetUtil.UTF_8);

        EmbeddedChannel chEncoder = new EmbeddedChannel(new JdkZlibEncoder(ZlibWrapper.NONE));

        chEncoder.writeOutbound(data.copy());
        assertTrue(chEncoder.finish());

        ByteBuf deflatedData = (ByteBuf) chEncoder.readOutbound();

        EmbeddedChannel chDecoderZlibNone = new EmbeddedChannel(new JdkZlibDecoder(ZlibWrapper.NONE));

        chDecoderZlibNone.writeInbound(deflatedData.copy());
        assertTrue(chDecoderZlibNone.finish());

        assertEquals(data, chDecoderZlibNone.readInbound());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testZLIB_OR_NONE() throws Exception {
        new JdkZlibDecoder(ZlibWrapper.ZLIB_OR_NONE);
    }

    @Test
    public void testGZIP() throws Exception {
        ByteBuf data = Unpooled.copiedBuffer("test", CharsetUtil.UTF_8);

        EmbeddedChannel chEncoder = new EmbeddedChannel(new JdkZlibEncoder(ZlibWrapper.GZIP));

        chEncoder.writeOutbound(data.copy());
        assertTrue(chEncoder.finish());

        ByteBuf deflatedData = (ByteBuf) chEncoder.readOutbound();

        EmbeddedChannel chDecoderGZip = new EmbeddedChannel(new JdkZlibDecoder(ZlibWrapper.GZIP));

        chDecoderGZip.writeInbound(deflatedData.copy());
        assertTrue(chDecoderGZip.finish());

        assertEquals(data, chDecoderGZip.readInbound());
    }
}
