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
package io.netty.handler.codec.http;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.PrematureChannelClosureException;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.util.CharsetUtil;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static io.netty.handler.codec.http.HttpHeadersTestUtils.of;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class HttpResponseDecoderTest {

    /**
     * The size of headers should be calculated correctly even if a single header is split into multiple fragments.
     * @see <a href="https://github.com/netty/netty/issues/3445">#3445</a>
     */
    @Test
    public void testMaxHeaderSize1() {
        final int maxHeaderSize = 8192;

        final EmbeddedChannel ch = new EmbeddedChannel(new HttpResponseDecoder(4096, maxHeaderSize, 8192));
        final char[] bytes = new char[maxHeaderSize / 2 - 2];
        Arrays.fill(bytes, 'a');

        ch.writeInbound(Unpooled.copiedBuffer("HTTP/1.1 200 OK\r\n", CharsetUtil.US_ASCII));

        // Write two 4096-byte headers (= 8192 bytes)
        ch.writeInbound(Unpooled.copiedBuffer("A:", CharsetUtil.US_ASCII));
        ch.writeInbound(Unpooled.copiedBuffer(bytes, CharsetUtil.US_ASCII));
        ch.writeInbound(Unpooled.copiedBuffer("\r\n", CharsetUtil.US_ASCII));
        assertNull(ch.readInbound());
        ch.writeInbound(Unpooled.copiedBuffer("B:", CharsetUtil.US_ASCII));
        ch.writeInbound(Unpooled.copiedBuffer(bytes, CharsetUtil.US_ASCII));
        ch.writeInbound(Unpooled.copiedBuffer("\r\n", CharsetUtil.US_ASCII));
        ch.writeInbound(Unpooled.copiedBuffer("\r\n", CharsetUtil.US_ASCII));

        HttpResponse res = ch.readInbound();
        assertNull(res.decoderResult().cause());
        assertTrue(res.decoderResult().isSuccess());

        assertNull(ch.readInbound());
        assertTrue(ch.finish());
        assertThat(ch.readInbound(), instanceOf(LastHttpContent.class));
    }

    /**
     * Complementary test case of {@link #testMaxHeaderSize1()} When it actually exceeds the maximum, it should fail.
     */
    @Test
    public void testMaxHeaderSize2() {
        final int maxHeaderSize = 8192;

        final EmbeddedChannel ch = new EmbeddedChannel(new HttpResponseDecoder(4096, maxHeaderSize, 8192));
        final char[] bytes = new char[maxHeaderSize / 2 - 2];
        Arrays.fill(bytes, 'a');

        ch.writeInbound(Unpooled.copiedBuffer("HTTP/1.1 200 OK\r\n", CharsetUtil.US_ASCII));

        // Write a 4096-byte header and a 4097-byte header to test an off-by-one case (= 8193 bytes)
        ch.writeInbound(Unpooled.copiedBuffer("A:", CharsetUtil.US_ASCII));
        ch.writeInbound(Unpooled.copiedBuffer(bytes, CharsetUtil.US_ASCII));
        ch.writeInbound(Unpooled.copiedBuffer("\r\n", CharsetUtil.US_ASCII));
        assertNull(ch.readInbound());
        ch.writeInbound(Unpooled.copiedBuffer("B: ", CharsetUtil.US_ASCII)); // Note an extra space.
        ch.writeInbound(Unpooled.copiedBuffer(bytes, CharsetUtil.US_ASCII));
        ch.writeInbound(Unpooled.copiedBuffer("\r\n", CharsetUtil.US_ASCII));
        ch.writeInbound(Unpooled.copiedBuffer("\r\n", CharsetUtil.US_ASCII));

        HttpResponse res = ch.readInbound();
        assertTrue(res.decoderResult().cause() instanceof TooLongFrameException);

        assertFalse(ch.finish());
        assertNull(ch.readInbound());
    }

    @Test
    public void testResponseChunked() {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpResponseDecoder());
        ch.writeInbound(Unpooled.copiedBuffer("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n",
                CharsetUtil.US_ASCII));

        HttpResponse res = ch.readInbound();
        assertThat(res.protocolVersion(), sameInstance(HttpVersion.HTTP_1_1));
        assertThat(res.status(), is(HttpResponseStatus.OK));

        byte[] data = new byte[64];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) i;
        }

        for (int i = 0; i < 10; i++) {
            assertFalse(ch.writeInbound(Unpooled.copiedBuffer(Integer.toHexString(data.length) + "\r\n",
                    CharsetUtil.US_ASCII)));
            assertTrue(ch.writeInbound(Unpooled.wrappedBuffer(data)));
            HttpContent content = ch.readInbound();
            assertEquals(data.length, content.content().readableBytes());

            byte[] decodedData = new byte[data.length];
            content.content().readBytes(decodedData);
            assertArrayEquals(data, decodedData);
            content.release();

            assertFalse(ch.writeInbound(Unpooled.copiedBuffer("\r\n", CharsetUtil.US_ASCII)));
        }

        // Write the last chunk.
        ch.writeInbound(Unpooled.copiedBuffer("0\r\n\r\n", CharsetUtil.US_ASCII));

        // Ensure the last chunk was decoded.
        LastHttpContent content = ch.readInbound();
        assertFalse(content.content().isReadable());
        content.release();

        ch.finish();
        assertNull(ch.readInbound());
    }

    @Test
    public void testResponseChunkedExceedMaxChunkSize() {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpResponseDecoder(4096, 8192, 32));
        ch.writeInbound(
                Unpooled.copiedBuffer("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n", CharsetUtil.US_ASCII));

        HttpResponse res = ch.readInbound();
        assertThat(res.protocolVersion(), sameInstance(HttpVersion.HTTP_1_1));
        assertThat(res.status(), is(HttpResponseStatus.OK));

        byte[] data = new byte[64];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) i;
        }

        for (int i = 0; i < 10; i++) {
            assertFalse(ch.writeInbound(Unpooled.copiedBuffer(Integer.toHexString(data.length) + "\r\n",
                    CharsetUtil.US_ASCII)));
            assertTrue(ch.writeInbound(Unpooled.wrappedBuffer(data)));

            byte[] decodedData = new byte[data.length];
            HttpContent content = ch.readInbound();
            assertEquals(32, content.content().readableBytes());
            content.content().readBytes(decodedData, 0, 32);
            content.release();

            content = ch.readInbound();
            assertEquals(32, content.content().readableBytes());

            content.content().readBytes(decodedData, 32, 32);

            assertArrayEquals(data, decodedData);
            content.release();

            assertFalse(ch.writeInbound(Unpooled.copiedBuffer("\r\n", CharsetUtil.US_ASCII)));
        }

        // Write the last chunk.
        ch.writeInbound(Unpooled.copiedBuffer("0\r\n\r\n", CharsetUtil.US_ASCII));

        // Ensure the last chunk was decoded.
        LastHttpContent content = ch.readInbound();
        assertFalse(content.content().isReadable());
        content.release();

        ch.finish();
        assertNull(ch.readInbound());
    }

    @Test
    public void testClosureWithoutContentLength1() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpResponseDecoder());
        ch.writeInbound(Unpooled.copiedBuffer("HTTP/1.1 200 OK\r\n\r\n", CharsetUtil.US_ASCII));

        // Read the response headers.
        HttpResponse res = ch.readInbound();
        assertThat(res.protocolVersion(), sameInstance(HttpVersion.HTTP_1_1));
        assertThat(res.status(), is(HttpResponseStatus.OK));
        assertThat(ch.readInbound(), is(nullValue()));

        // Close the connection without sending anything.
        assertTrue(ch.finish());

        // The decoder should still produce the last content.
        LastHttpContent content = ch.readInbound();
        assertThat(content.content().isReadable(), is(false));
        content.release();

        // But nothing more.
        assertThat(ch.readInbound(), is(nullValue()));
    }

    @Test
    public void testClosureWithoutContentLength2() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpResponseDecoder());

        // Write the partial response.
        ch.writeInbound(Unpooled.copiedBuffer("HTTP/1.1 200 OK\r\n\r\n12345678", CharsetUtil.US_ASCII));

        // Read the response headers.
        HttpResponse res = ch.readInbound();
        assertThat(res.protocolVersion(), sameInstance(HttpVersion.HTTP_1_1));
        assertThat(res.status(), is(HttpResponseStatus.OK));

        // Read the partial content.
        HttpContent content = ch.readInbound();
        assertThat(content.content().toString(CharsetUtil.US_ASCII), is("12345678"));
        assertThat(content, is(not(instanceOf(LastHttpContent.class))));
        content.release();

        assertThat(ch.readInbound(), is(nullValue()));

        // Close the connection.
        assertTrue(ch.finish());

        // The decoder should still produce the last content.
        LastHttpContent lastContent = ch.readInbound();
        assertThat(lastContent.content().isReadable(), is(false));
        lastContent.release();

        // But nothing more.
        assertThat(ch.readInbound(), is(nullValue()));
    }

    @Test
    public void testPrematureClosureWithChunkedEncoding1() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpResponseDecoder());
        ch.writeInbound(
                Unpooled.copiedBuffer("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n", CharsetUtil.US_ASCII));

        // Read the response headers.
        HttpResponse res = ch.readInbound();
        assertThat(res.protocolVersion(), sameInstance(HttpVersion.HTTP_1_1));
        assertThat(res.status(), is(HttpResponseStatus.OK));
        assertThat(res.headers().get(HttpHeaderNames.TRANSFER_ENCODING), is("chunked"));
        assertThat(ch.readInbound(), is(nullValue()));

        // Close the connection without sending anything.
        ch.finish();
        // The decoder should not generate the last chunk because it's closed prematurely.
        assertThat(ch.readInbound(), is(nullValue()));
    }

    @Test
    public void testPrematureClosureWithChunkedEncoding2() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpResponseDecoder());

        // Write the partial response.
        ch.writeInbound(Unpooled.copiedBuffer(
                "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n8\r\n12345678", CharsetUtil.US_ASCII));

        // Read the response headers.
        HttpResponse res = ch.readInbound();
        assertThat(res.protocolVersion(), sameInstance(HttpVersion.HTTP_1_1));
        assertThat(res.status(), is(HttpResponseStatus.OK));
        assertThat(res.headers().get(HttpHeaderNames.TRANSFER_ENCODING), is("chunked"));

        // Read the partial content.
        HttpContent content = ch.readInbound();
        assertThat(content.content().toString(CharsetUtil.US_ASCII), is("12345678"));
        assertThat(content, is(not(instanceOf(LastHttpContent.class))));
        content.release();

        assertThat(ch.readInbound(), is(nullValue()));

        // Close the connection.
        ch.finish();

        // The decoder should not generate the last chunk because it's closed prematurely.
        assertThat(ch.readInbound(), is(nullValue()));
    }

    @Test
    public void testLastResponseWithEmptyHeaderAndEmptyContent() {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpResponseDecoder());
        ch.writeInbound(Unpooled.copiedBuffer("HTTP/1.1 200 OK\r\n\r\n", CharsetUtil.US_ASCII));

        HttpResponse res = ch.readInbound();
        assertThat(res.protocolVersion(), sameInstance(HttpVersion.HTTP_1_1));
        assertThat(res.status(), is(HttpResponseStatus.OK));
        assertThat(ch.readInbound(), is(nullValue()));

        assertThat(ch.finish(), is(true));

        LastHttpContent content = ch.readInbound();
        assertThat(content.content().isReadable(), is(false));
        content.release();

        assertThat(ch.readInbound(), is(nullValue()));
    }

    @Test
    public void testLastResponseWithoutContentLengthHeader() {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpResponseDecoder());
        ch.writeInbound(Unpooled.copiedBuffer("HTTP/1.1 200 OK\r\n\r\n", CharsetUtil.US_ASCII));

        HttpResponse res = ch.readInbound();
        assertThat(res.protocolVersion(), sameInstance(HttpVersion.HTTP_1_1));
        assertThat(res.status(), is(HttpResponseStatus.OK));
        assertThat(ch.readInbound(), is(nullValue()));

        ch.writeInbound(Unpooled.wrappedBuffer(new byte[1024]));
        HttpContent content = ch.readInbound();
        assertThat(content.content().readableBytes(), is(1024));
        content.release();

        assertThat(ch.finish(), is(true));

        LastHttpContent lastContent = ch.readInbound();
        assertThat(lastContent.content().isReadable(), is(false));
        lastContent.release();

        assertThat(ch.readInbound(), is(nullValue()));
    }

    @Test
    public void testLastResponseWithHeaderRemoveTrailingSpaces() {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpResponseDecoder());
        ch.writeInbound(Unpooled.copiedBuffer(
                "HTTP/1.1 200 OK\r\nX-Header: h2=h2v2; Expires=Wed, 09-Jun-2021 10:18:14 GMT       \r\n\r\n",
                CharsetUtil.US_ASCII));

        HttpResponse res = ch.readInbound();
        assertThat(res.protocolVersion(), sameInstance(HttpVersion.HTTP_1_1));
        assertThat(res.status(), is(HttpResponseStatus.OK));
        assertThat(res.headers().get(of("X-Header")), is("h2=h2v2; Expires=Wed, 09-Jun-2021 10:18:14 GMT"));
        assertThat(ch.readInbound(), is(nullValue()));

        ch.writeInbound(Unpooled.wrappedBuffer(new byte[1024]));
        HttpContent content = ch.readInbound();
        assertThat(content.content().readableBytes(), is(1024));
        content.release();

        assertThat(ch.finish(), is(true));

        LastHttpContent lastContent = ch.readInbound();
        assertThat(lastContent.content().isReadable(), is(false));
        lastContent.release();

        assertThat(ch.readInbound(), is(nullValue()));
    }

    @Test
    public void testResetContentResponseWithTransferEncoding() {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpResponseDecoder());
        assertTrue(ch.writeInbound(Unpooled.copiedBuffer(
                "HTTP/1.1 205 Reset Content\r\n" +
                "Transfer-Encoding: chunked\r\n" +
                "\r\n" +
                "0\r\n" +
                "\r\n",
                CharsetUtil.US_ASCII)));

        HttpResponse res = ch.readInbound();
        assertThat(res.protocolVersion(), sameInstance(HttpVersion.HTTP_1_1));
        assertThat(res.status(), is(HttpResponseStatus.RESET_CONTENT));

        LastHttpContent lastContent = ch.readInbound();
        assertThat(lastContent.content().isReadable(), is(false));
        lastContent.release();

        assertThat(ch.finish(), is(false));
    }

    @Test
    public void testLastResponseWithTrailingHeader() {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpResponseDecoder());
        ch.writeInbound(Unpooled.copiedBuffer(
                "HTTP/1.1 200 OK\r\n" +
                        "Transfer-Encoding: chunked\r\n" +
                        "\r\n" +
                        "0\r\n" +
                        "Set-Cookie: t1=t1v1\r\n" +
                        "Set-Cookie: t2=t2v2; Expires=Wed, 09-Jun-2021 10:18:14 GMT\r\n" +
                        "\r\n",
                CharsetUtil.US_ASCII));

        HttpResponse res = ch.readInbound();
        assertThat(res.protocolVersion(), sameInstance(HttpVersion.HTTP_1_1));
        assertThat(res.status(), is(HttpResponseStatus.OK));

        LastHttpContent lastContent = ch.readInbound();
        assertThat(lastContent.content().isReadable(), is(false));
        HttpHeaders headers = lastContent.trailingHeaders();
        assertEquals(1, headers.names().size());
        List<String> values = headers.getAll(of("Set-Cookie"));
        assertEquals(2, values.size());
        assertTrue(values.contains("t1=t1v1"));
        assertTrue(values.contains("t2=t2v2; Expires=Wed, 09-Jun-2021 10:18:14 GMT"));
        lastContent.release();

        assertThat(ch.finish(), is(false));
        assertThat(ch.readInbound(), is(nullValue()));
    }

    @Test
    public void testLastResponseWithTrailingHeaderFragmented() {
        byte[] data = ("HTTP/1.1 200 OK\r\n" +
                "Transfer-Encoding: chunked\r\n" +
                "\r\n" +
                "0\r\n" +
                "Set-Cookie: t1=t1v1\r\n" +
                "Set-Cookie: t2=t2v2; Expires=Wed, 09-Jun-2021 10:18:14 GMT\r\n" +
                "\r\n").getBytes(CharsetUtil.US_ASCII);

        for (int i = 1; i < data.length; i++) {
            testLastResponseWithTrailingHeaderFragmented(data, i);
        }
    }

    private static void testLastResponseWithTrailingHeaderFragmented(byte[] content, int fragmentSize) {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpResponseDecoder());
        int headerLength = 47;
        // split up the header
        for (int a = 0; a < headerLength;) {
            int amount = fragmentSize;
            if (a + amount > headerLength) {
                amount = headerLength -  a;
            }

            // if header is done it should produce a HttpRequest
            boolean headerDone = a + amount == headerLength;
            assertEquals(headerDone, ch.writeInbound(Unpooled.wrappedBuffer(content, a, amount)));
            a += amount;
        }

        ch.writeInbound(Unpooled.wrappedBuffer(content, headerLength, content.length - headerLength));
        HttpResponse res = ch.readInbound();
        assertThat(res.protocolVersion(), sameInstance(HttpVersion.HTTP_1_1));
        assertThat(res.status(), is(HttpResponseStatus.OK));

        LastHttpContent lastContent = ch.readInbound();
        assertThat(lastContent.content().isReadable(), is(false));
        HttpHeaders headers = lastContent.trailingHeaders();
        assertEquals(1, headers.names().size());
        List<String> values = headers.getAll(of("Set-Cookie"));
        assertEquals(2, values.size());
        assertTrue(values.contains("t1=t1v1"));
        assertTrue(values.contains("t2=t2v2; Expires=Wed, 09-Jun-2021 10:18:14 GMT"));
        lastContent.release();

        assertThat(ch.finish(), is(false));
        assertThat(ch.readInbound(), is(nullValue()));
    }

    @Test
    public void testResponseWithContentLength() {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpResponseDecoder());
        ch.writeInbound(Unpooled.copiedBuffer(
                "HTTP/1.1 200 OK\r\n" +
                        "Content-Length: 10\r\n" +
                        "\r\n", CharsetUtil.US_ASCII));

        byte[] data = new byte[10];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) i;
        }
        ch.writeInbound(Unpooled.wrappedBuffer(data, 0, data.length / 2));
        ch.writeInbound(Unpooled.wrappedBuffer(data, 5, data.length / 2));

        HttpResponse res = ch.readInbound();
        assertThat(res.protocolVersion(), sameInstance(HttpVersion.HTTP_1_1));
        assertThat(res.status(), is(HttpResponseStatus.OK));

        HttpContent firstContent = ch.readInbound();
        assertThat(firstContent.content().readableBytes(), is(5));
        assertEquals(Unpooled.wrappedBuffer(data, 0, 5), firstContent.content());
        firstContent.release();

        LastHttpContent lastContent = ch.readInbound();
        assertEquals(5, lastContent.content().readableBytes());
        assertEquals(Unpooled.wrappedBuffer(data, 5, 5), lastContent.content());
        lastContent.release();

        assertThat(ch.finish(), is(false));
        assertThat(ch.readInbound(), is(nullValue()));
    }

    @Test
    public void testResponseWithContentLengthFragmented() {
        byte[] data = ("HTTP/1.1 200 OK\r\n" +
                "Content-Length: 10\r\n" +
                "\r\n").getBytes(CharsetUtil.US_ASCII);

        for (int i = 1; i < data.length; i++) {
            testResponseWithContentLengthFragmented(data, i);
        }
    }

    private static void testResponseWithContentLengthFragmented(byte[] header, int fragmentSize) {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpResponseDecoder());
        // split up the header
        for (int a = 0; a < header.length;) {
            int amount = fragmentSize;
            if (a + amount > header.length) {
                amount = header.length -  a;
            }

            ch.writeInbound(Unpooled.wrappedBuffer(header, a, amount));
            a += amount;
        }
        byte[] data = new byte[10];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) i;
        }
        ch.writeInbound(Unpooled.wrappedBuffer(data, 0, data.length / 2));
        ch.writeInbound(Unpooled.wrappedBuffer(data, 5, data.length / 2));

        HttpResponse res = ch.readInbound();
        assertThat(res.protocolVersion(), sameInstance(HttpVersion.HTTP_1_1));
        assertThat(res.status(), is(HttpResponseStatus.OK));

        HttpContent firstContent = ch.readInbound();
        assertThat(firstContent.content().readableBytes(), is(5));
        assertEquals(Unpooled.wrappedBuffer(data, 0, 5), firstContent.content());
        firstContent.release();

        LastHttpContent lastContent = ch.readInbound();
        assertEquals(5, lastContent.content().readableBytes());
        assertEquals(Unpooled.wrappedBuffer(data, 5, 5), lastContent.content());
        lastContent.release();

        assertThat(ch.finish(), is(false));
        assertThat(ch.readInbound(), is(nullValue()));
    }

    @Test
    public void testWebSocketResponse() {
        byte[] data = ("HTTP/1.1 101 WebSocket Protocol Handshake\r\n" +
                "Upgrade: WebSocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Origin: http://localhost:8080\r\n" +
                "Sec-WebSocket-Location: ws://localhost/some/path\r\n" +
                "\r\n" +
                "1234567812345678").getBytes();
        EmbeddedChannel ch = new EmbeddedChannel(new HttpResponseDecoder());
        ch.writeInbound(Unpooled.wrappedBuffer(data));

        HttpResponse res = ch.readInbound();
        assertThat(res.protocolVersion(), sameInstance(HttpVersion.HTTP_1_1));
        assertThat(res.status(), is(HttpResponseStatus.SWITCHING_PROTOCOLS));
        HttpContent content = ch.readInbound();
        assertThat(content.content().readableBytes(), is(16));
        content.release();

        assertThat(ch.finish(), is(false));

        assertThat(ch.readInbound(), is(nullValue()));
    }

    // See https://github.com/netty/netty/issues/2173
    @Test
    public void testWebSocketResponseWithDataFollowing() {
        byte[] data = ("HTTP/1.1 101 WebSocket Protocol Handshake\r\n" +
                "Upgrade: WebSocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Origin: http://localhost:8080\r\n" +
                "Sec-WebSocket-Location: ws://localhost/some/path\r\n" +
                "\r\n" +
                "1234567812345678").getBytes();
        byte[] otherData = {1, 2, 3, 4};

        EmbeddedChannel ch = new EmbeddedChannel(new HttpResponseDecoder());
        ch.writeInbound(Unpooled.wrappedBuffer(data, otherData));

        HttpResponse res = ch.readInbound();
        assertThat(res.protocolVersion(), sameInstance(HttpVersion.HTTP_1_1));
        assertThat(res.status(), is(HttpResponseStatus.SWITCHING_PROTOCOLS));
        HttpContent content = ch.readInbound();
        assertThat(content.content().readableBytes(), is(16));
        content.release();

        assertThat(ch.finish(), is(true));

        ByteBuf expected = Unpooled.wrappedBuffer(otherData);
        ByteBuf buffer = ch.readInbound();
        try {
            assertEquals(expected, buffer);
        } finally {
            expected.release();
            if (buffer != null) {
                buffer.release();
            }
        }
    }

    @Test
    public void testGarbageHeaders() {
        // A response without headers - from https://github.com/netty/netty/issues/2103
        byte[] data = ("<html>\r\n" +
                "<head><title>400 Bad Request</title></head>\r\n" +
                "<body bgcolor=\"white\">\r\n" +
                "<center><h1>400 Bad Request</h1></center>\r\n" +
                "<hr><center>nginx/1.1.19</center>\r\n" +
                "</body>\r\n" +
                "</html>\r\n").getBytes();

        EmbeddedChannel ch = new EmbeddedChannel(new HttpResponseDecoder());

        ch.writeInbound(Unpooled.wrappedBuffer(data));

        // Garbage input should generate the 999 Unknown response.
        HttpResponse res = ch.readInbound();
        assertThat(res.protocolVersion(), sameInstance(HttpVersion.HTTP_1_0));
        assertThat(res.status().code(), is(999));
        assertThat(res.decoderResult().isFailure(), is(true));
        assertThat(res.decoderResult().isFinished(), is(true));
        assertThat(ch.readInbound(), is(nullValue()));

        // More garbage should not generate anything (i.e. the decoder discards anything beyond this point.)
        ch.writeInbound(Unpooled.wrappedBuffer(data));
        assertThat(ch.readInbound(), is(nullValue()));

        // Closing the connection should not generate anything since the protocol has been violated.
        ch.finish();
        assertThat(ch.readInbound(), is(nullValue()));
    }

    /**
     * Tests if the decoder produces one and only {@link LastHttpContent} when an invalid chunk is received and
     * the connection is closed.
     */
    @Test
    public void testGarbageChunk() {
        EmbeddedChannel channel = new EmbeddedChannel(new HttpResponseDecoder());
        String responseWithIllegalChunk =
                "HTTP/1.1 200 OK\r\n" +
                "Transfer-Encoding: chunked\r\n\r\n" +
                "NOT_A_CHUNK_LENGTH\r\n";

        channel.writeInbound(Unpooled.copiedBuffer(responseWithIllegalChunk, CharsetUtil.US_ASCII));
        assertThat(channel.readInbound(), is(instanceOf(HttpResponse.class)));

        // Ensure that the decoder generates the last chunk with correct decoder result.
        LastHttpContent invalidChunk = channel.readInbound();
        assertThat(invalidChunk.decoderResult().isFailure(), is(true));
        invalidChunk.release();

        // And no more messages should be produced by the decoder.
        assertThat(channel.readInbound(), is(nullValue()));

        // .. even after the connection is closed.
        assertThat(channel.finish(), is(false));
    }

    @Test
    public void testConnectionClosedBeforeHeadersReceived() {
        EmbeddedChannel channel = new EmbeddedChannel(new HttpResponseDecoder());
        String responseInitialLine =
                "HTTP/1.1 200 OK\r\n";
        assertFalse(channel.writeInbound(Unpooled.copiedBuffer(responseInitialLine, CharsetUtil.US_ASCII)));
        assertTrue(channel.finish());
        HttpMessage message = channel.readInbound();
        assertTrue(message.decoderResult().isFailure());
        assertThat(message.decoderResult().cause(), instanceOf(PrematureChannelClosureException.class));
        assertNull(channel.readInbound());
    }
}
