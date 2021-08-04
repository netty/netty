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
package io.netty.handler.codec.compression;

import io.netty.buffer.AbstractByteBufAllocator;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.EmptyArrays;
import io.netty.util.internal.PlatformDependent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Random;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class ZlibTest {

    private static final byte[] BYTES_SMALL = new byte[128];
    private static final byte[] BYTES_LARGE = new byte[1024 * 1024];
    private static final byte[] BYTES_LARGE2 = ("<!--?xml version=\"1.0\" encoding=\"ISO-8859-1\"?-->\n" +
            "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" " +
            "\"https://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">\n" +
            "<html xmlns=\"https://www.w3.org/1999/xhtml\" xml:lang=\"en\" lang=\"en\"><head>\n" +
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
        Random rand = PlatformDependent.threadLocalRandom();
        rand.nextBytes(BYTES_SMALL);
        rand.nextBytes(BYTES_LARGE);
    }

    protected ZlibDecoder createDecoder(ZlibWrapper wrapper) {
        return createDecoder(wrapper, 0);
    }

    protected abstract ZlibEncoder createEncoder(ZlibWrapper wrapper);
    protected abstract ZlibDecoder createDecoder(ZlibWrapper wrapper, int maxAllocation);

    @Test
    public void testGZIP2() throws Exception {
        byte[] bytes = "message".getBytes(CharsetUtil.UTF_8);
        ByteBuf data = Unpooled.wrappedBuffer(bytes);
        ByteBuf deflatedData = Unpooled.wrappedBuffer(gzip(bytes));

        EmbeddedChannel chDecoderGZip = new EmbeddedChannel(createDecoder(ZlibWrapper.GZIP));
        try {
            while (deflatedData.isReadable()) {
                chDecoderGZip.writeInbound(deflatedData.readRetainedSlice(1));
            }
            deflatedData.release();
            assertTrue(chDecoderGZip.finish());
            ByteBuf buf = Unpooled.buffer();
            for (;;) {
                ByteBuf b = chDecoderGZip.readInbound();
                if (b == null) {
                    break;
                }
                buf.writeBytes(b);
                b.release();
            }
            assertEquals(buf, data);
            assertNull(chDecoderGZip.readInbound());
            data.release();
            buf.release();
        } finally {
            dispose(chDecoderGZip);
        }
    }

    private void testCompress0(ZlibWrapper encoderWrapper, ZlibWrapper decoderWrapper, ByteBuf data) throws Exception {
        EmbeddedChannel chEncoder = new EmbeddedChannel(createEncoder(encoderWrapper));
        EmbeddedChannel chDecoderZlib = new EmbeddedChannel(createDecoder(decoderWrapper));

        try {
            chEncoder.writeOutbound(data.retain());
            chEncoder.flush();
            data.resetReaderIndex();

            for (;;) {
                ByteBuf deflatedData = chEncoder.readOutbound();
                if (deflatedData == null) {
                    break;
                }
                chDecoderZlib.writeInbound(deflatedData);
            }

            byte[] decompressed = new byte[data.readableBytes()];
            int offset = 0;
            for (;;) {
                ByteBuf buf = chDecoderZlib.readInbound();
                if (buf == null) {
                    break;
                }
                int length = buf.readableBytes();
                buf.readBytes(decompressed, offset, length);
                offset += length;
                buf.release();
                if (offset == decompressed.length) {
                    break;
                }
            }
            assertEquals(data, Unpooled.wrappedBuffer(decompressed));
            assertNull(chDecoderZlib.readInbound());

            // Closing an encoder channel will generate a footer.
            assertTrue(chEncoder.finish());
            for (;;) {
                Object msg = chEncoder.readOutbound();
                if (msg == null) {
                    break;
                }
                ReferenceCountUtil.release(msg);
            }
            // But, the footer will be decoded into nothing. It's only for validation.
            assertFalse(chDecoderZlib.finish());

            data.release();
        } finally {
            dispose(chEncoder);
            dispose(chDecoderZlib);
        }
    }

    private void testCompressNone(ZlibWrapper encoderWrapper, ZlibWrapper decoderWrapper) throws Exception {
        EmbeddedChannel chEncoder = new EmbeddedChannel(createEncoder(encoderWrapper));
        EmbeddedChannel chDecoderZlib = new EmbeddedChannel(createDecoder(decoderWrapper));

        try {
            // Closing an encoder channel without writing anything should generate both header and footer.
            assertTrue(chEncoder.finish());

            for (;;) {
                ByteBuf deflatedData = chEncoder.readOutbound();
                if (deflatedData == null) {
                    break;
                }
                chDecoderZlib.writeInbound(deflatedData);
            }

            // Decoder should not generate anything at all.
            boolean decoded = false;
            for (;;) {
                ByteBuf buf = chDecoderZlib.readInbound();
                if (buf == null) {
                    break;
                }

                buf.release();
                decoded = true;
            }
            assertFalse(decoded, "should decode nothing");

            assertFalse(chDecoderZlib.finish());
        } finally {
            dispose(chEncoder);
            dispose(chDecoderZlib);
        }
    }

    private static void dispose(EmbeddedChannel ch) {
        if (ch.finish()) {
            for (;;) {
                Object msg = ch.readInbound();
                if (msg == null) {
                    break;
                }
                ReferenceCountUtil.release(msg);
            }
            for (;;) {
                Object msg = ch.readOutbound();
                if (msg == null) {
                    break;
                }
                ReferenceCountUtil.release(msg);
            }
        }
    }

    // Test for https://github.com/netty/netty/issues/2572
    private void testDecompressOnly(ZlibWrapper decoderWrapper, byte[] compressed, byte[] data) throws Exception {
        EmbeddedChannel chDecoder = new EmbeddedChannel(createDecoder(decoderWrapper));
        chDecoder.writeInbound(Unpooled.copiedBuffer(compressed));
        assertTrue(chDecoder.finish());

        ByteBuf decoded = Unpooled.buffer(data.length);

        for (;;) {
            ByteBuf buf = chDecoder.readInbound();
            if (buf == null) {
                break;
            }
            decoded.writeBytes(buf);
            buf.release();
        }
        assertEquals(Unpooled.copiedBuffer(data), decoded);
        decoded.release();
    }

    private void testCompressSmall(ZlibWrapper encoderWrapper, ZlibWrapper decoderWrapper) throws Exception {
        testCompress0(encoderWrapper, decoderWrapper, Unpooled.wrappedBuffer(BYTES_SMALL));
        testCompress0(encoderWrapper, decoderWrapper,
                Unpooled.directBuffer(BYTES_SMALL.length).writeBytes(BYTES_SMALL));
    }

    private void testCompressLarge(ZlibWrapper encoderWrapper, ZlibWrapper decoderWrapper) throws Exception {
        testCompress0(encoderWrapper, decoderWrapper, Unpooled.wrappedBuffer(BYTES_LARGE));
        testCompress0(encoderWrapper, decoderWrapper,
                Unpooled.directBuffer(BYTES_LARGE.length).writeBytes(BYTES_LARGE));
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
        EmbeddedChannel chEncoder = new EmbeddedChannel(createEncoder(ZlibWrapper.GZIP));
        if (data != null) {
            chEncoder.writeOutbound(Unpooled.wrappedBuffer(data));
        }
        assertTrue(chEncoder.finish());

        ByteBuf encoded = Unpooled.buffer();
        for (;;) {
            ByteBuf buf = chEncoder.readOutbound();
            if (buf == null) {
                break;
            }
            encoded.writeBytes(buf);
            buf.release();
        }

        ByteBuf decoded = Unpooled.buffer();
        GZIPInputStream stream = new GZIPInputStream(new ByteBufInputStream(encoded, true));
        try {
            byte[] buf = new byte[8192];
            for (;;) {
                int readBytes = stream.read(buf);
                if (readBytes < 0) {
                    break;
                }
                decoded.writeBytes(buf, 0, readBytes);
            }
        } finally {
            stream.close();
        }

        if (data != null) {
            assertEquals(Unpooled.wrappedBuffer(data), decoded);
        } else {
            assertFalse(decoded.isReadable());
        }

        decoded.release();
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

    @Test
    public void testZLIB_OR_NONE3() throws Exception {
        testCompressNone(ZlibWrapper.GZIP, ZlibWrapper.ZLIB_OR_NONE);
        testCompressSmall(ZlibWrapper.GZIP, ZlibWrapper.ZLIB_OR_NONE);
        testCompressLarge(ZlibWrapper.GZIP, ZlibWrapper.ZLIB_OR_NONE);
    }

    @Test
    public void testMaxAllocation() throws Exception {
        int maxAllocation = 1024;
        ZlibDecoder decoder = createDecoder(ZlibWrapper.ZLIB, maxAllocation);
        final EmbeddedChannel chDecoder = new EmbeddedChannel(decoder);
        TestByteBufAllocator alloc = new TestByteBufAllocator(chDecoder.alloc());
        chDecoder.config().setAllocator(alloc);

        DecompressionException e = assertThrows(DecompressionException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                chDecoder.writeInbound(Unpooled.wrappedBuffer(deflate(BYTES_LARGE)));
            }
        });
        assertTrue(e.getMessage().startsWith("Decompression buffer has reached maximum size"));
        assertEquals(maxAllocation, alloc.getMaxAllocation());
        assertTrue(decoder.isClosed());
        assertFalse(chDecoder.finish());
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

    private static final class TestByteBufAllocator extends AbstractByteBufAllocator {
        private final ByteBufAllocator wrapped;
        private int maxAllocation;

        TestByteBufAllocator(ByteBufAllocator wrapped) {
            this.wrapped = wrapped;
        }

        public int getMaxAllocation() {
            return maxAllocation;
        }

        @Override
        public boolean isDirectBufferPooled() {
            return wrapped.isDirectBufferPooled();
        }

        @Override
        protected ByteBuf newHeapBuffer(int initialCapacity, int maxCapacity) {
            maxAllocation = Math.max(maxAllocation, maxCapacity);
            return wrapped.heapBuffer(initialCapacity, maxCapacity);
        }

        @Override
        protected ByteBuf newDirectBuffer(int initialCapacity, int maxCapacity) {
            maxAllocation = Math.max(maxAllocation, maxCapacity);
            return wrapped.directBuffer(initialCapacity, maxCapacity);
        }
    }
}
