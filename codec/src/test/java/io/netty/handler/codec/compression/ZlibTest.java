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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public abstract class ZlibTest {

    protected abstract ZlibEncoder createEncoder(ZlibWrapper wrapper);
    protected abstract ZlibDecoder createDecoder(ZlibWrapper wrapper);

    @Test
    public void testGZIP2() throws Exception {
        ByteBuf data = Unpooled.wrappedBuffer("message".getBytes(CharsetUtil.UTF_8));
        ByteBuf deflatedData = Unpooled.wrappedBuffer(gzip("message"));

        EmbeddedChannel chDecoderGZip = new EmbeddedChannel(createDecoder(ZlibWrapper.GZIP));
        chDecoderGZip.writeInbound(deflatedData.copy());
        assertTrue(chDecoderGZip.finish());
        assertEquals(chDecoderGZip.readInbound(), data);
    }

    @Test
    public void testZLIB() throws Exception {
        ByteBuf data = Unpooled.copiedBuffer("test", CharsetUtil.UTF_8);

        EmbeddedChannel chEncoder = new EmbeddedChannel(createEncoder(ZlibWrapper.ZLIB));

        chEncoder.writeOutbound(data.copy());
        assertTrue(chEncoder.finish());

        ByteBuf deflatedData = (ByteBuf) chEncoder.readOutbound();

        EmbeddedChannel chDecoderZlib = new EmbeddedChannel(createDecoder(ZlibWrapper.ZLIB));

        chDecoderZlib.writeInbound(deflatedData.copy());
        assertTrue(chDecoderZlib.finish());

        assertEquals(data, chDecoderZlib.readInbound());
    }

    @Test
    public void testNONE() throws Exception {
        ByteBuf data = Unpooled.copiedBuffer("test", CharsetUtil.UTF_8);

        EmbeddedChannel chEncoder = new EmbeddedChannel(createEncoder(ZlibWrapper.NONE));

        chEncoder.writeOutbound(data.copy());
        assertTrue(chEncoder.finish());

        ByteBuf deflatedData = (ByteBuf) chEncoder.readOutbound();

        EmbeddedChannel chDecoderZlibNone = new EmbeddedChannel(createDecoder(ZlibWrapper.NONE));

        chDecoderZlibNone.writeInbound(deflatedData.copy());
        assertTrue(chDecoderZlibNone.finish());

        assertEquals(data, chDecoderZlibNone.readInbound());
    }

    @Test
    public void testGZIP() throws Exception {
        ByteBuf data = Unpooled.copiedBuffer("test", CharsetUtil.UTF_8);

        EmbeddedChannel chEncoder = new EmbeddedChannel(createEncoder(ZlibWrapper.GZIP));

        chEncoder.writeOutbound(data.copy());
        assertTrue(chEncoder.finish());

        ByteBuf deflatedData = (ByteBuf) chEncoder.readOutbound();

        EmbeddedChannel chDecoderGZip = new EmbeddedChannel(createDecoder(ZlibWrapper.GZIP));

        chDecoderGZip.writeInbound(deflatedData.copy());
        assertTrue(chDecoderGZip.finish());

        assertEquals(data, chDecoderGZip.readInbound());
    }

    private static byte[] gzip(String message) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        GZIPOutputStream stream = new GZIPOutputStream(out);
        stream.write(message.getBytes(CharsetUtil.UTF_8));
        stream.close();
        return out.toByteArray();
    }

}
