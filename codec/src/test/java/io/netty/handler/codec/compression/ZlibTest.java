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
import static org.junit.Assert.assertNull;
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
        ByteBuf buf = (ByteBuf) chDecoderGZip.readInbound();
        assertEquals(buf, data);
        assertNull(chDecoderGZip.readInbound());
        data.release();
        deflatedData.release();
        buf.release();
    }

    protected void testCompress(ZlibWrapper encoderWrapper, ZlibWrapper decoderWrapper) throws Exception {
        ByteBuf data = Unpooled.copiedBuffer("test", CharsetUtil.UTF_8);
        EmbeddedChannel chEncoder = new EmbeddedChannel(createEncoder(encoderWrapper));

        chEncoder.writeOutbound(data.copy());
        assertTrue(chEncoder.finish());

        EmbeddedChannel chDecoderZlib = new EmbeddedChannel(createDecoder(decoderWrapper));
        for (;;) {
            ByteBuf deflatedData = (ByteBuf) chEncoder.readOutbound();
            if (deflatedData == null) {
                break;
            }
            chDecoderZlib.writeInbound(deflatedData);
        }

        assertTrue(chDecoderZlib.finish());

        ByteBuf buf = (ByteBuf) chDecoderZlib.readInbound();
        assertEquals(data, buf);
        assertNull(chDecoderZlib.readInbound());

        data.release();
        buf.release();
    }

    @Test
    public void testZLIB() throws Exception {
        testCompress(ZlibWrapper.ZLIB, ZlibWrapper.ZLIB);
    }

    @Test
    public void testNONE() throws Exception {
        testCompress(ZlibWrapper.NONE, ZlibWrapper.NONE);
    }

    @Test
    public void testGZIP() throws Exception {
        testCompress(ZlibWrapper.GZIP, ZlibWrapper.GZIP);
    }

    @Test
    public void testZLIB_OR_NONE() throws Exception {
        testCompress(ZlibWrapper.NONE, ZlibWrapper.ZLIB_OR_NONE);
    }

    @Test
    public void testZLIB_OR_NONE2() throws Exception {
        testCompress(ZlibWrapper.ZLIB, ZlibWrapper.ZLIB_OR_NONE);
    }

    @Test
    public void testZLIB_OR_NONE3() throws Exception {
        testCompress(ZlibWrapper.GZIP, ZlibWrapper.ZLIB_OR_NONE);
    }

    private static byte[] gzip(String message) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        GZIPOutputStream stream = new GZIPOutputStream(out);
        stream.write(message.getBytes(CharsetUtil.UTF_8));
        stream.close();
        return out.toByteArray();
    }

}
