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
package org.jboss.netty.handler.codec.compression;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferInputStream;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelDownstreamHandler;
import org.jboss.netty.channel.ChannelUpstreamHandler;
import org.jboss.netty.handler.codec.embedder.DecoderEmbedder;
import org.jboss.netty.handler.codec.embedder.EncoderEmbedder;
import org.jboss.netty.util.CharsetUtil;
import org.jboss.netty.util.internal.EmptyArrays;
import org.jboss.netty.util.internal.ThreadLocalRandom;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static org.junit.Assert.*;

public abstract class ZlibTest {

    private static final byte[] BYTES_SMALL = new byte[128];
    private static final byte[] BYTES_LARGE = new byte[1024 * 1024];
    private static final byte[] BYTES_LARGE2 = (
            "<!--?xml version=\"1.0\" encoding=\"ISO-8859-1\"?-->\n" +
            "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" " +
            "\"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">\n" +
            "<html xmlns=\"http://www.w3.org/1999/xhtml\" xml:lang=\"en\" lang=\"en\"><head>\n" +
            "    <title>Apache Tomcat</title>\n" +
            "</head>\n" +
            '\n' +
            "<body>\n" +
            "<h1>It works !</h1>\n" +
            '\n' +
            "<p>If you're seeing this page via a web browser, it means you've setup Tomcat successfully." +
            " Congratulations!</p>\n" +
            " \n" +
            "<p>This is the default Tomcat home page." +
            " It can be found on the local filesystem at: <code>/var/lib/tomcat7/webapps/ROOT/index.html</code></p>\n" +
            '\n' +
            "<p>Tomcat7 veterans might be pleased to learn that this system instance of Tomcat is installed with" +
            " <code>CATALINA_HOME</code> in <code>/usr/share/tomcat7</code> and <code>CATALINA_BASE</code> in" +
            " <code>/var/lib/tomcat7</code>, following the rules from" +
            " <code>/usr/share/doc/tomcat7-common/RUNNING.txt.gz</code>.</p>\n" +
            '\n' +
            "<p>You might consider installing the following packages, if you haven't already done so:</p>\n" +
            '\n' +
            "<p><b>tomcat7-docs</b>: This package installs a web application that allows to browse the Tomcat 7" +
            " documentation locally. Once installed, you can access it by clicking <a href=\"docs/\">here</a>.</p>\n" +
            '\n' +
            "<p><b>tomcat7-examples</b>: This package installs a web application that allows to access the Tomcat" +
            " 7 Servlet and JSP examples. Once installed, you can access it by clicking" +
            " <a href=\"examples/\">here</a>.</p>\n" +
            '\n' +
            "<p><b>tomcat7-admin</b>: This package installs two web applications that can help managing this Tomcat" +
            " instance. Once installed, you can access the <a href=\"manager/html\">manager webapp</a> and" +
            " the <a href=\"host-manager/html\">host-manager webapp</a>.</p><p>\n" +
            '\n' +
            "</p><p>NOTE: For security reasons, using the manager webapp is restricted" +
            " to users with role \"manager\"." +
            " The host-manager webapp is restricted to users with role \"admin\". Users are " +
            "defined in <code>/etc/tomcat7/tomcat-users.xml</code>.</p>\n" +
            '\n' +
            '\n' +
            '\n' +
            "</body></html>").getBytes(CharsetUtil.UTF_8);

    static {
        ThreadLocalRandom rand = ThreadLocalRandom.current();
        rand.nextBytes(BYTES_SMALL);
        rand.nextBytes(BYTES_LARGE);
    }

    protected abstract ChannelDownstreamHandler createEncoder(ZlibWrapper wrapper);

    private static ChannelUpstreamHandler createDecoder(ZlibWrapper wrapper) {
        // We don't have ZLIB decoder that uses JDK Inflater in 3.x.
        return new ZlibDecoder(wrapper);
    }

    @Test
    public void testGZIP2() throws Exception {
        byte[] bytes = "message".getBytes(CharsetUtil.UTF_8);
        ChannelBuffer data = ChannelBuffers.wrappedBuffer(bytes);
        ChannelBuffer deflatedData = ChannelBuffers.wrappedBuffer(gzip(bytes));

        DecoderEmbedder<ChannelBuffer> chDecoderGZip =
                new DecoderEmbedder<ChannelBuffer>(createDecoder(ZlibWrapper.GZIP));

        chDecoderGZip.offer(deflatedData.copy());
        assertTrue(chDecoderGZip.finish());
        ChannelBuffer buf = chDecoderGZip.poll();
        assertEquals(buf, data);
        assertNull(chDecoderGZip.poll());
    }

    private void testCompress0(
            ZlibWrapper encoderWrapper, ZlibWrapper decoderWrapper, ChannelBuffer data) throws Exception {

        EncoderEmbedder<ChannelBuffer> chEncoder =
                new EncoderEmbedder<ChannelBuffer>(createEncoder(encoderWrapper));
        DecoderEmbedder<ChannelBuffer> chDecoderZlib =
                new DecoderEmbedder<ChannelBuffer>(createDecoder(decoderWrapper));

        chEncoder.offer(data.copy());

        for (;;) {
            ChannelBuffer deflatedData = chEncoder.poll();
            if (deflatedData == null) {
                break;
            }
            chDecoderZlib.offer(deflatedData);
        }

        byte[] decompressed = new byte[data.readableBytes()];
        int offset = 0;
        for (;;) {
            ChannelBuffer buf = chDecoderZlib.poll();
            if (buf == null) {
                break;
            }
            int length = buf.readableBytes();
            buf.readBytes(decompressed, offset, length);
            offset += length;
            if (offset == decompressed.length) {
                break;
            }
        }
        assertEquals(data, ChannelBuffers.wrappedBuffer(decompressed));
        assertNull(chDecoderZlib.poll());

        // Closing an encoder channel will generate a footer.
        assertTrue(chEncoder.finish());
        for (;;) {
            Object msg = chEncoder.poll();
            if (msg == null) {
                break;
            }
        }
        // But, the footer will be decoded into nothing. It's only for validation.
        assertFalse(chDecoderZlib.finish());
    }

    private void testCompressNone(ZlibWrapper encoderWrapper, ZlibWrapper decoderWrapper) throws Exception {
        EncoderEmbedder<ChannelBuffer> chEncoder =
                new EncoderEmbedder<ChannelBuffer>(createEncoder(encoderWrapper));
        DecoderEmbedder<ChannelBuffer> chDecoderZlib =
                new DecoderEmbedder<ChannelBuffer>(createDecoder(decoderWrapper));

        // Closing an encoder channel without writing anything should generate both header and footer.
        assertTrue(chEncoder.finish());

        for (;;) {
            ChannelBuffer deflatedData = chEncoder.poll();
            if (deflatedData == null) {
                break;
            }
            chDecoderZlib.offer(deflatedData);
        }

        // Decoder should not generate anything at all.
        boolean decoded = false;
        for (;;) {
            ChannelBuffer buf = chDecoderZlib.poll();
            if (buf == null) {
                break;
            }

            decoded = true;
        }
        assertFalse("should decode nothing", decoded);

        assertFalse(chDecoderZlib.finish());
    }

    // Test for https://github.com/netty/netty/issues/2572
    private static void testDecompressOnly(
            ZlibWrapper decoderWrapper, byte[] compressed, byte[] data) throws Exception {

        DecoderEmbedder<ChannelBuffer> chDecoder =
                new DecoderEmbedder<ChannelBuffer>(createDecoder(decoderWrapper));

        chDecoder.offer(ChannelBuffers.wrappedBuffer(compressed));
        assertTrue(chDecoder.finish());

        ChannelBuffer decoded = ChannelBuffers.dynamicBuffer(data.length);

        for (;;) {
            ChannelBuffer buf = chDecoder.poll();
            if (buf == null) {
                break;
            }
            decoded.writeBytes(buf);
        }
        assertEquals(ChannelBuffers.wrappedBuffer(data), decoded);
    }

    private void testCompressSmall(ZlibWrapper encoderWrapper, ZlibWrapper decoderWrapper) throws Exception {
        testCompress0(encoderWrapper, decoderWrapper, ChannelBuffers.wrappedBuffer(BYTES_SMALL));

        final ChannelBuffer directSmallBuf = ChannelBuffers.directBuffer(BYTES_SMALL.length);
        directSmallBuf.writeBytes(BYTES_SMALL);
        testCompress0(encoderWrapper, decoderWrapper, directSmallBuf);
    }

    private void testCompressLarge(ZlibWrapper encoderWrapper, ZlibWrapper decoderWrapper) throws Exception {
        testCompress0(encoderWrapper, decoderWrapper, ChannelBuffers.wrappedBuffer(BYTES_LARGE));

        final ChannelBuffer directLargeBuf = ChannelBuffers.directBuffer(BYTES_LARGE.length);
        directLargeBuf.writeBytes(BYTES_LARGE);
        testCompress0(encoderWrapper, decoderWrapper, directLargeBuf);
    }

    @Test
    public void testZLIB() throws Exception {
        testCompressNone(ZlibWrapper.ZLIB, ZlibWrapper.ZLIB);
        testCompressSmall(ZlibWrapper.ZLIB, ZlibWrapper.ZLIB);
        testCompressLarge(ZlibWrapper.ZLIB, ZlibWrapper.ZLIB);
        testDecompressOnly(ZlibWrapper.ZLIB, deflate(BYTES_LARGE2), BYTES_LARGE2);
    }

    @Test
    public void testNONE() throws Exception {
        testCompressNone(ZlibWrapper.NONE, ZlibWrapper.NONE);
        testCompressSmall(ZlibWrapper.NONE, ZlibWrapper.NONE);
        testCompressLarge(ZlibWrapper.NONE, ZlibWrapper.NONE);
    }

    @Test
    public void testGZIP() throws Exception {
        testCompressNone(ZlibWrapper.GZIP, ZlibWrapper.GZIP);
        testCompressSmall(ZlibWrapper.GZIP, ZlibWrapper.GZIP);
        testCompressLarge(ZlibWrapper.GZIP, ZlibWrapper.GZIP);
        testDecompressOnly(ZlibWrapper.GZIP, gzip(BYTES_LARGE2), BYTES_LARGE2);
    }

    @Test
    public void testGZIPCompressOnly() throws Exception {
        testGZIPCompressOnly0(null); // Do not write anything; just finish the stream.
        testGZIPCompressOnly0(EmptyArrays.EMPTY_BYTES); // Write an empty array.
        testGZIPCompressOnly0(BYTES_SMALL);
        testGZIPCompressOnly0(BYTES_LARGE);
    }

    private void testGZIPCompressOnly0(byte[] data) throws IOException {
        EncoderEmbedder<ChannelBuffer> chEncoder = new EncoderEmbedder<ChannelBuffer>(createEncoder(ZlibWrapper.GZIP));
        if (data != null) {
            chEncoder.offer(ChannelBuffers.wrappedBuffer(data));
        }
        assertTrue(chEncoder.finish());

        ChannelBuffer encoded = ChannelBuffers.dynamicBuffer();
        for (;;) {
            ChannelBuffer buf = chEncoder.poll();
            if (buf == null) {
                break;
            }
            encoded.writeBytes(buf);
        }

        ChannelBuffer decoded = ChannelBuffers.dynamicBuffer();
        GZIPInputStream stream = new GZIPInputStream(new ChannelBufferInputStream(encoded));
        byte[] buf = new byte[8192];
        for (;;) {
            int readBytes = stream.read(buf);
            if (readBytes < 0) {
                break;
            }
            decoded.writeBytes(buf, 0, readBytes);
        }
        stream.close();

        if (data != null) {
            assertEquals(ChannelBuffers.wrappedBuffer(data), decoded);
        } else {
            assertFalse(decoded.readable());
        }
    }

    @Test
    public void testZLIB_OR_NONE() throws Exception {
        testCompressNone(ZlibWrapper.NONE, ZlibWrapper.ZLIB_OR_NONE);
        testCompressSmall(ZlibWrapper.NONE, ZlibWrapper.ZLIB_OR_NONE);
        testCompressLarge(ZlibWrapper.NONE, ZlibWrapper.ZLIB_OR_NONE);
    }

    @Test
    public void testZLIB_OR_NONE2() throws Exception {
        testCompressNone(ZlibWrapper.ZLIB, ZlibWrapper.ZLIB_OR_NONE);
        testCompressSmall(ZlibWrapper.ZLIB, ZlibWrapper.ZLIB_OR_NONE);
        testCompressLarge(ZlibWrapper.ZLIB, ZlibWrapper.ZLIB_OR_NONE);
    }

    private static byte[] gzip(byte[] bytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        GZIPOutputStream stream = new GZIPOutputStream(out);
        stream.write(bytes);
        stream.close();
        return out.toByteArray();
    }

    private static byte[] deflate(byte[] bytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        OutputStream stream = new DeflaterOutputStream(out);
        stream.write(bytes);
        stream.close();
        return out.toByteArray();
    }
}
