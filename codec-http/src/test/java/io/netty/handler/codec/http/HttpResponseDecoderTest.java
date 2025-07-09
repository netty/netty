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
package io.netty.handler.codec.http;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.PrematureChannelClosureException;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import static io.netty.handler.codec.http.HttpHeadersTestUtils.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HttpResponseDecoderTest {

    /**
     * The size of headers should be calculated correctly even if a single header is split into multiple fragments.
     * @see <a href="https://github.com/netty/netty/issues/3445">#3445</a>
     */
    @Test
    public void testMaxHeaderSize1() {
        final int maxHeaderSize = 8192;

        final EmbeddedChannel ch = new EmbeddedChannel(new HttpResponseDecoder(4096, maxHeaderSize, 8192));
        final char[] bytes = new char[maxHeaderSize / 2 - 4];
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
        assertInstanceOf(LastHttpContent.class, ch.readInbound());
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
        assertInstanceOf(TooLongHttpHeaderException.class, res.decoderResult().cause());

        assertFalse(ch.finish());
        assertNull(ch.readInbound());
    }

    @Test
    void testTotalHeaderLimit() throws Exception {
        String requestStr = "HTTP/1.1 200 OK\r\n" +
                "Server: X\r\n" + // 9 content bytes
                "a1: b\r\n" +     // + 5 = 14 bytes,
                "a2: b\r\n\r\n";  // + 5 = 19 bytes

        // Decoding with a max header size of 18 bytes must fail:
        EmbeddedChannel channel = new EmbeddedChannel(new HttpResponseDecoder(1024, 18, 1024));
        assertTrue(channel.writeInbound(Unpooled.copiedBuffer(requestStr, CharsetUtil.US_ASCII)));
        HttpResponse response = channel.readInbound();
        assertTrue(response.decoderResult().isFailure());
        assertInstanceOf(TooLongHttpHeaderException.class, response.decoderResult().cause());
        assertFalse(channel.finish());

        // Decoding with a max header size of 19 must pass:
        channel = new EmbeddedChannel(new HttpResponseDecoder(1024, 19, 1024));
        assertTrue(channel.writeInbound(Unpooled.copiedBuffer(requestStr, CharsetUtil.US_ASCII)));
        response = channel.readInbound();
        assertTrue(response.decoderResult().isSuccess());
        assertEquals("X", response.headers().get("Server"));
        assertEquals("b", response.headers().get("a1"));
        assertEquals("b", response.headers().get("a2"));
        channel.close();
        assertEquals(LastHttpContent.EMPTY_LAST_CONTENT, channel.readInbound());
        assertFalse(channel.finish());
    }

    @Test
    public void testResponseChunked() {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpResponseDecoder());
        ch.writeInbound(Unpooled.copiedBuffer("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n",
                CharsetUtil.US_ASCII));

        HttpResponse res = ch.readInbound();
        assertSame(HttpVersion.HTTP_1_1, res.protocolVersion());
        assertEquals(HttpResponseStatus.OK, res.status());

        byte[] data = new byte[64];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) i;
        }

        for (int i = 0; i < 10; i++) {
            assertFalse(ch.writeInbound(Unpooled.copiedBuffer(Integer.toHexString(data.length) + "\r\n",
                    CharsetUtil.US_ASCII)));
            assertTrue(ch.writeInbound(Unpooled.copiedBuffer(data)));
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
    public void testResponseChunkedWithValidUncommonPatterns() {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpResponseDecoder());
        ch.writeInbound(Unpooled.copiedBuffer("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n",
                                              CharsetUtil.US_ASCII));

        HttpResponse res = ch.readInbound();
        assertSame(HttpVersion.HTTP_1_1, res.protocolVersion());
        assertEquals(HttpResponseStatus.OK, res.status());

        byte[] data = new byte[1];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) i;
        }

        // leading whitespace, trailing whitespace

        assertFalse(ch.writeInbound(Unpooled.copiedBuffer("  " + Integer.toHexString(data.length) + " \r\n",
                                                          CharsetUtil.US_ASCII)));
        assertTrue(ch.writeInbound(Unpooled.copiedBuffer(data)));
        HttpContent content = ch.readInbound();
        assertEquals(data.length, content.content().readableBytes());

        byte[] decodedData = new byte[data.length];
        content.content().readBytes(decodedData);
        assertArrayEquals(data, decodedData);
        content.release();

        assertFalse(ch.writeInbound(Unpooled.copiedBuffer("\r\n", CharsetUtil.US_ASCII)));

        // leading whitespace, trailing control char

        assertFalse(ch.writeInbound(Unpooled.copiedBuffer("  " + Integer.toHexString(data.length) + "\0\r\n",
                                                          CharsetUtil.US_ASCII)));
        assertTrue(ch.writeInbound(Unpooled.copiedBuffer(data)));
        content = ch.readInbound();
        assertEquals(data.length, content.content().readableBytes());

        decodedData = new byte[data.length];
        content.content().readBytes(decodedData);
        assertArrayEquals(data, decodedData);
        content.release();

        assertFalse(ch.writeInbound(Unpooled.copiedBuffer("\r\n", CharsetUtil.US_ASCII)));

        // leading whitespace, trailing semicolon

        assertFalse(ch.writeInbound(Unpooled.copiedBuffer("  " + Integer.toHexString(data.length) + ";\r\n",
                                                          CharsetUtil.US_ASCII)));
        assertTrue(ch.writeInbound(Unpooled.copiedBuffer(data)));
        content = ch.readInbound();
        assertEquals(data.length, content.content().readableBytes());

        decodedData = new byte[data.length];
        content.content().readBytes(decodedData);
        assertArrayEquals(data, decodedData);
        content.release();

        assertFalse(ch.writeInbound(Unpooled.copiedBuffer("\r\n", CharsetUtil.US_ASCII)));

        // Write the last chunk.
        ch.writeInbound(Unpooled.copiedBuffer("0\r\n\r\n", CharsetUtil.US_ASCII));

        // Ensure the last chunk was decoded.
        LastHttpContent lastContent = ch.readInbound();
        assertFalse(lastContent.content().isReadable());
        lastContent.release();

        ch.finish();
        assertNull(ch.readInbound());
    }

    @Test
    public void testResponseChunkedWithControlChars() {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpResponseDecoder());
        ch.writeInbound(Unpooled.copiedBuffer("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n",
                                              CharsetUtil.US_ASCII));

        HttpResponse res = ch.readInbound();
        assertSame(HttpVersion.HTTP_1_1, res.protocolVersion());
        assertEquals(HttpResponseStatus.OK, res.status());

        byte[] data = new byte[1];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) i;
        }

        assertFalse(ch.writeInbound(Unpooled.copiedBuffer("  " + Integer.toHexString(data.length) + " \r\n",
                                                          CharsetUtil.US_ASCII)));
        assertTrue(ch.writeInbound(Unpooled.copiedBuffer(data)));
        HttpContent content = ch.readInbound();
        assertEquals(data.length, content.content().readableBytes());

        byte[] decodedData = new byte[data.length];
        content.content().readBytes(decodedData);
        assertArrayEquals(data, decodedData);
        content.release();

        assertFalse(ch.writeInbound(Unpooled.copiedBuffer("\r\n", CharsetUtil.US_ASCII)));

        // Write the last chunk.
        ch.writeInbound(Unpooled.copiedBuffer("0\r\n\r\n", CharsetUtil.US_ASCII));

        // Ensure the last chunk was decoded.
        LastHttpContent lastContent = ch.readInbound();
        assertFalse(lastContent.content().isReadable());
        lastContent.release();

        assertFalse(ch.finish());
        assertNull(ch.readInbound());
    }

    @Test
    public void testResponseDisallowPartialChunks() {
        HttpResponseDecoder decoder = new HttpResponseDecoder(
            HttpObjectDecoder.DEFAULT_MAX_INITIAL_LINE_LENGTH,
            HttpObjectDecoder.DEFAULT_MAX_HEADER_SIZE,
            HttpObjectDecoder.DEFAULT_MAX_CHUNK_SIZE,
            HttpObjectDecoder.DEFAULT_VALIDATE_HEADERS,
            HttpObjectDecoder.DEFAULT_INITIAL_BUFFER_SIZE,
            HttpObjectDecoder.DEFAULT_ALLOW_DUPLICATE_CONTENT_LENGTHS,
            false);
        EmbeddedChannel ch = new EmbeddedChannel(decoder);

        String headers = "HTTP/1.1 200 OK\r\n"
            + "Transfer-Encoding: chunked\r\n"
            + "\r\n";
       assertTrue(ch.writeInbound(Unpooled.copiedBuffer(headers, CharsetUtil.US_ASCII)));

        HttpResponse res = ch.readInbound();
        assertSame(HttpVersion.HTTP_1_1, res.protocolVersion());
        assertEquals(HttpResponseStatus.OK, res.status());

        byte[] chunkBytes = new byte[10];
        Random random = new Random();
        random.nextBytes(chunkBytes);
        final ByteBuf chunk = ch.alloc().buffer().writeBytes(chunkBytes);
        final int chunkSize = chunk.readableBytes();
        ByteBuf partialChunk1 = chunk.retainedSlice(0, 5);
        ByteBuf partialChunk2 = chunk.retainedSlice(5, 5);

        assertFalse(ch.writeInbound(Unpooled.copiedBuffer(Integer.toHexString(chunkSize)
                                                          + "\r\n", CharsetUtil.US_ASCII)));
        assertFalse(ch.writeInbound(partialChunk1));
        assertTrue(ch.writeInbound(partialChunk2));

        HttpContent content = ch.readInbound();
        assertEquals(chunk, content.content());
        content.release();
        chunk.release();

        assertFalse(ch.writeInbound(Unpooled.copiedBuffer("\r\n", CharsetUtil.US_ASCII)));

        // Write the last chunk.
        assertTrue(ch.writeInbound(Unpooled.copiedBuffer("0\r\n\r\n", CharsetUtil.US_ASCII)));

        // Ensure the last chunk was decoded.
        HttpContent lastContent = ch.readInbound();
        assertFalse(lastContent.content().isReadable());
        lastContent.release();

        assertFalse(ch.finish());
    }

    @Test
    public void testResponseChunkedExceedMaxChunkSize() {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpResponseDecoder(4096, 8192, 32));
        ch.writeInbound(
                Unpooled.copiedBuffer("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n", CharsetUtil.US_ASCII));

        HttpResponse res = ch.readInbound();
        assertSame(HttpVersion.HTTP_1_1, res.protocolVersion());
        assertEquals(HttpResponseStatus.OK, res.status());

        byte[] data = new byte[64];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) i;
        }

        for (int i = 0; i < 10; i++) {
            assertFalse(ch.writeInbound(Unpooled.copiedBuffer(Integer.toHexString(data.length) + "\r\n",
                    CharsetUtil.US_ASCII)));
            assertTrue(ch.writeInbound(Unpooled.copiedBuffer(data)));

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
        assertSame(HttpVersion.HTTP_1_1, res.protocolVersion());
        assertEquals(HttpResponseStatus.OK, res.status());
        assertNull(ch.readInbound());

        // Close the connection without sending anything.
        assertTrue(ch.finish());

        // The decoder should still produce the last content.
        LastHttpContent content = ch.readInbound();
        assertFalse(content.content().isReadable());
        content.release();

        // But nothing more.
        assertNull(ch.readInbound());
    }

    @Test
    public void testClosureWithoutContentLength2() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpResponseDecoder());

        // Write the partial response.
        ch.writeInbound(Unpooled.copiedBuffer("HTTP/1.1 200 OK\r\n\r\n12345678", CharsetUtil.US_ASCII));

        // Read the response headers.
        HttpResponse res = ch.readInbound();
        assertSame(HttpVersion.HTTP_1_1, res.protocolVersion());
        assertEquals(HttpResponseStatus.OK, res.status());

        // Read the partial content.
        HttpContent content = ch.readInbound();
        assertEquals("12345678", content.content().toString(CharsetUtil.US_ASCII));
        assertThat(content).isNotInstanceOf(LastHttpContent.class);
        content.release();

        assertNull(ch.readInbound());

        // Close the connection.
        assertTrue(ch.finish());

        // The decoder should still produce the last content.
        LastHttpContent lastContent = ch.readInbound();
        assertFalse(lastContent.content().isReadable());
        lastContent.release();

        // But nothing more.
        assertNull(ch.readInbound());
    }

    @Test
    public void testPrematureClosureWithChunkedEncoding1() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpResponseDecoder());
        ch.writeInbound(
                Unpooled.copiedBuffer("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n", CharsetUtil.US_ASCII));

        // Read the response headers.
        HttpResponse res = ch.readInbound();
        assertSame(HttpVersion.HTTP_1_1, res.protocolVersion());
        assertEquals(HttpResponseStatus.OK, res.status());
        assertEquals("chunked", res.headers().get(HttpHeaderNames.TRANSFER_ENCODING));
        assertNull(ch.readInbound());

        // Close the connection without sending anything.
        ch.finish();
        // The decoder should not generate the last chunk because it's closed prematurely.
        assertNull(ch.readInbound());
    }

    @Test
    public void testPrematureClosureWithChunkedEncoding2() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpResponseDecoder());

        // Write the partial response.
        ch.writeInbound(Unpooled.copiedBuffer(
                "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n8\r\n12345678", CharsetUtil.US_ASCII));

        // Read the response headers.
        HttpResponse res = ch.readInbound();
        assertSame(HttpVersion.HTTP_1_1, res.protocolVersion());
        assertEquals(HttpResponseStatus.OK, res.status());
        assertEquals("chunked", res.headers().get(HttpHeaderNames.TRANSFER_ENCODING));

        // Read the partial content.
        HttpContent content = ch.readInbound();
        assertEquals("12345678", content.content().toString(CharsetUtil.US_ASCII));
        assertThat(content).isNotInstanceOf(LastHttpContent.class);
        content.release();

        assertNull(ch.readInbound());

        // Close the connection.
        ch.finish();

        // The decoder should not generate the last chunk because it's closed prematurely.
        assertNull(ch.readInbound());
    }

    @Test
    public void testLastResponseWithEmptyHeaderAndEmptyContent() {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpResponseDecoder());
        ch.writeInbound(Unpooled.copiedBuffer("HTTP/1.1 200 OK\r\n\r\n", CharsetUtil.US_ASCII));

        HttpResponse res = ch.readInbound();
        assertSame(HttpVersion.HTTP_1_1, res.protocolVersion());
        assertEquals(HttpResponseStatus.OK, res.status());
        assertNull(ch.readInbound());

        assertTrue(ch.finish());

        LastHttpContent content = ch.readInbound();
        assertFalse(content.content().isReadable());
        content.release();

        assertNull(ch.readInbound());
    }

    @Test
    public void testLastResponseWithoutContentLengthHeader() {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpResponseDecoder());
        ch.writeInbound(Unpooled.copiedBuffer("HTTP/1.1 200 OK\r\n\r\n", CharsetUtil.US_ASCII));

        HttpResponse res = ch.readInbound();
        assertSame(HttpVersion.HTTP_1_1, res.protocolVersion());
        assertEquals(HttpResponseStatus.OK, res.status());
        assertNull(ch.readInbound());

        ch.writeInbound(Unpooled.wrappedBuffer(new byte[1024]));
        HttpContent content = ch.readInbound();
        assertEquals(1024, content.content().readableBytes());
        content.release();

        assertTrue(ch.finish());

        LastHttpContent lastContent = ch.readInbound();
        assertFalse(lastContent.content().isReadable());
        lastContent.release();

        assertNull(ch.readInbound());
    }

    @Test
    public void testLastResponseWithHeaderRemoveTrailingSpaces() {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpResponseDecoder());
        ch.writeInbound(Unpooled.copiedBuffer(
                "HTTP/1.1 200 OK\r\nX-Header: h2=h2v2; Expires=Wed, 09-Jun-2021 10:18:14 GMT       \r\n\r\n",
                CharsetUtil.US_ASCII));

        HttpResponse res = ch.readInbound();
        assertSame(HttpVersion.HTTP_1_1, res.protocolVersion());
        assertEquals(HttpResponseStatus.OK, res.status());
        assertEquals("h2=h2v2; Expires=Wed, 09-Jun-2021 10:18:14 GMT", res.headers().get(of("X-Header")));
        assertNull(ch.readInbound());

        ch.writeInbound(Unpooled.wrappedBuffer(new byte[1024]));
        HttpContent content = ch.readInbound();
        assertEquals(1024, content.content().readableBytes());
        content.release();

        assertTrue(ch.finish());

        LastHttpContent lastContent = ch.readInbound();
        assertFalse(lastContent.content().isReadable());
        lastContent.release();

        assertNull(ch.readInbound());
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
        assertSame(HttpVersion.HTTP_1_1, res.protocolVersion());
        assertEquals(HttpResponseStatus.RESET_CONTENT, res.status());

        LastHttpContent lastContent = ch.readInbound();
        assertFalse(lastContent.content().isReadable());
        lastContent.release();

        assertFalse(ch.finish());
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
        assertSame(HttpVersion.HTTP_1_1, res.protocolVersion());
        assertEquals(HttpResponseStatus.OK, res.status());

        LastHttpContent lastContent = ch.readInbound();
        assertFalse(lastContent.content().isReadable());
        HttpHeaders headers = lastContent.trailingHeaders();
        assertEquals(1, headers.names().size());
        List<String> values = headers.getAll(of("Set-Cookie"));
        assertEquals(2, values.size());
        assertTrue(values.contains("t1=t1v1"));
        assertTrue(values.contains("t2=t2v2; Expires=Wed, 09-Jun-2021 10:18:14 GMT"));
        lastContent.release();

        assertFalse(ch.finish());
        assertNull(ch.readInbound());
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

            // if header is done it should produce an HttpRequest
            boolean headerDone = a + amount == headerLength;
            assertEquals(headerDone, ch.writeInbound(Unpooled.copiedBuffer(content, a, amount)));
            a += amount;
        }

        ch.writeInbound(Unpooled.copiedBuffer(content, headerLength, content.length - headerLength));
        HttpResponse res = ch.readInbound();
        assertSame(HttpVersion.HTTP_1_1, res.protocolVersion());
        assertEquals(HttpResponseStatus.OK, res.status());

        LastHttpContent lastContent = ch.readInbound();
        assertFalse(lastContent.content().isReadable());
        HttpHeaders headers = lastContent.trailingHeaders();
        assertEquals(1, headers.names().size());
        List<String> values = headers.getAll(of("Set-Cookie"));
        assertEquals(2, values.size());
        assertTrue(values.contains("t1=t1v1"));
        assertTrue(values.contains("t2=t2v2; Expires=Wed, 09-Jun-2021 10:18:14 GMT"));
        lastContent.release();

        assertFalse(ch.finish());
        assertNull(ch.readInbound());
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
        ch.writeInbound(Unpooled.copiedBuffer(data, 0, data.length / 2));
        ch.writeInbound(Unpooled.copiedBuffer(data, 5, data.length / 2));

        HttpResponse res = ch.readInbound();
        assertSame(HttpVersion.HTTP_1_1, res.protocolVersion());
        assertEquals(HttpResponseStatus.OK, res.status());

        HttpContent firstContent = ch.readInbound();
        assertEquals(5, firstContent.content().readableBytes());
        assertEquals(Unpooled.copiedBuffer(data, 0, 5), firstContent.content());
        firstContent.release();

        LastHttpContent lastContent = ch.readInbound();
        assertEquals(5, lastContent.content().readableBytes());
        assertEquals(Unpooled.copiedBuffer(data, 5, 5), lastContent.content());
        lastContent.release();

        assertFalse(ch.finish());
        assertNull(ch.readInbound());
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

            ch.writeInbound(Unpooled.copiedBuffer(header, a, amount));
            a += amount;
        }
        byte[] data = new byte[10];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) i;
        }
        ch.writeInbound(Unpooled.copiedBuffer(data, 0, data.length / 2));
        ch.writeInbound(Unpooled.copiedBuffer(data, 5, data.length / 2));

        HttpResponse res = ch.readInbound();
        assertSame(HttpVersion.HTTP_1_1, res.protocolVersion());
        assertEquals(HttpResponseStatus.OK, res.status());

        HttpContent firstContent = ch.readInbound();
        assertEquals(5, firstContent.content().readableBytes());
        assertEquals(Unpooled.wrappedBuffer(data, 0, 5), firstContent.content());
        firstContent.release();

        LastHttpContent lastContent = ch.readInbound();
        assertEquals(5, lastContent.content().readableBytes());
        assertEquals(Unpooled.wrappedBuffer(data, 5, 5), lastContent.content());
        lastContent.release();

        assertFalse(ch.finish());
        assertNull(ch.readInbound());
    }

    @Test
    public void testOrderOfHeadersWithContentLength() {
        String requestStr = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/plain; charset=UTF-8\r\n" +
                "Content-Length: 5\r\n" +
                "Connection: close\r\n\r\n" +
                "hello";
        EmbeddedChannel channel = new EmbeddedChannel(new HttpResponseDecoder());
        assertTrue(channel.writeInbound(Unpooled.copiedBuffer(requestStr, CharsetUtil.US_ASCII)));
        HttpResponse response = channel.readInbound();
        List<String> headers = new ArrayList<String>();
        for (Map.Entry<String, String> header : response.headers()) {
            headers.add(header.getKey());
        }
        assertEquals(Arrays.asList("Content-Type", "Content-Length", "Connection"), headers, "ordered headers");
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
        assertSame(HttpVersion.HTTP_1_1, res.protocolVersion());
        assertEquals(HttpResponseStatus.SWITCHING_PROTOCOLS, res.status());
        HttpContent content = ch.readInbound();
        assertEquals(16, content.content().readableBytes());
        content.release();

        assertFalse(ch.finish());

        assertNull(ch.readInbound());
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
        ch.writeInbound(Unpooled.copiedBuffer(data, otherData));

        HttpResponse res = ch.readInbound();
        assertSame(HttpVersion.HTTP_1_1, res.protocolVersion());
        assertEquals(HttpResponseStatus.SWITCHING_PROTOCOLS, res.status());
        HttpContent content = ch.readInbound();
        assertEquals(16, content.content().readableBytes());
        content.release();

        assertTrue(ch.finish());

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

        ch.writeInbound(Unpooled.copiedBuffer(data));

        // Garbage input should generate the 999 Unknown response.
        HttpResponse res = ch.readInbound();
        assertSame(HttpVersion.HTTP_1_0, res.protocolVersion());
        assertEquals(999, res.status().code());
        assertTrue(res.decoderResult().isFailure());
        assertTrue(res.decoderResult().isFinished());
        assertNull(ch.readInbound());

        // More garbage should not generate anything (i.e. the decoder discards anything beyond this point.)
        ch.writeInbound(Unpooled.copiedBuffer(data));
        assertNull(ch.readInbound());

        // Closing the connection should not generate anything since the protocol has been violated.
        ch.finish();
        assertNull(ch.readInbound());
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
        assertInstanceOf(HttpResponse.class, channel.readInbound());

        // Ensure that the decoder generates the last chunk with correct decoder result.
        LastHttpContent invalidChunk = channel.readInbound();
        assertTrue(invalidChunk.decoderResult().isFailure());
        invalidChunk.release();

        // And no more messages should be produced by the decoder.
        assertNull(channel.readInbound());

        // .. even after the connection is closed.
        assertFalse(channel.finish());
    }

    @Test
    public void testWhiteSpaceGarbageChunk() {
        EmbeddedChannel channel = new EmbeddedChannel(new HttpResponseDecoder());
        String responseWithIllegalChunk =
                "HTTP/1.1 200 OK\r\n" +
                "Transfer-Encoding: chunked\r\n\r\n" +
                " \r\n";

        channel.writeInbound(Unpooled.copiedBuffer(responseWithIllegalChunk, CharsetUtil.US_ASCII));
        assertInstanceOf(HttpResponse.class, channel.readInbound());

        // Ensure that the decoder generates the last chunk with correct decoder result.
        LastHttpContent invalidChunk = channel.readInbound();
        assertTrue(invalidChunk.decoderResult().isFailure());
        invalidChunk.release();

        // And no more messages should be produced by the decoder.
        assertNull(channel.readInbound());

        // .. even after the connection is closed.
        assertFalse(channel.finish());
    }

    @Test
    public void testLeadingWhiteSpacesSemiColonGarbageChunk() {
        EmbeddedChannel channel = new EmbeddedChannel(new HttpResponseDecoder());
        String responseWithIllegalChunk =
                "HTTP/1.1 200 OK\r\n" +
                "Transfer-Encoding: chunked\r\n\r\n" +
                "  ;\r\n";

        channel.writeInbound(Unpooled.copiedBuffer(responseWithIllegalChunk, CharsetUtil.US_ASCII));
        assertInstanceOf(HttpResponse.class, channel.readInbound());

        // Ensure that the decoder generates the last chunk with correct decoder result.
        LastHttpContent invalidChunk = channel.readInbound();
        assertTrue(invalidChunk.decoderResult().isFailure());
        invalidChunk.release();

        // And no more messages should be produced by the decoder.
        assertNull(channel.readInbound());

        // .. even after the connection is closed.
        assertFalse(channel.finish());
    }

    @Test
    public void testControlCharGarbageChunk() {
        EmbeddedChannel channel = new EmbeddedChannel(new HttpResponseDecoder());
        String responseWithIllegalChunk =
                "HTTP/1.1 200 OK\r\n" +
                "Transfer-Encoding: chunked\r\n\r\n" +
                "\0\r\n";

        channel.writeInbound(Unpooled.copiedBuffer(responseWithIllegalChunk, CharsetUtil.US_ASCII));
        assertInstanceOf(HttpResponse.class, channel.readInbound());

        // Ensure that the decoder generates the last chunk with correct decoder result.
        LastHttpContent invalidChunk = channel.readInbound();
        assertTrue(invalidChunk.decoderResult().isFailure());
        invalidChunk.release();

        // And no more messages should be produced by the decoder.
        assertNull(channel.readInbound());

        // .. even after the connection is closed.
        assertFalse(channel.finish());
    }

    @Test
    public void testLeadingWhiteSpacesControlCharGarbageChunk() {
        EmbeddedChannel channel = new EmbeddedChannel(new HttpResponseDecoder());
        String responseWithIllegalChunk =
                "HTTP/1.1 200 OK\r\n" +
                "Transfer-Encoding: chunked\r\n\r\n" +
                "  \0\r\n";

        channel.writeInbound(Unpooled.copiedBuffer(responseWithIllegalChunk, CharsetUtil.US_ASCII));
        assertInstanceOf(HttpResponse.class, channel.readInbound());

        // Ensure that the decoder generates the last chunk with correct decoder result.
        LastHttpContent invalidChunk = channel.readInbound();
        assertTrue(invalidChunk.decoderResult().isFailure());
        invalidChunk.release();

        // And no more messages should be produced by the decoder.
        assertNull(channel.readInbound());

        // .. even after the connection is closed.
        assertFalse(channel.finish());
    }

    @Test
    public void testGarbageChunkAfterWhiteSpaces() {
        EmbeddedChannel channel = new EmbeddedChannel(new HttpResponseDecoder());
        String responseWithIllegalChunk =
                "HTTP/1.1 200 OK\r\n" +
                "Transfer-Encoding: chunked\r\n\r\n" +
                "  12345N1 ;\r\n";

        channel.writeInbound(Unpooled.copiedBuffer(responseWithIllegalChunk, CharsetUtil.US_ASCII));
        assertInstanceOf(HttpResponse.class, channel.readInbound());

        // Ensure that the decoder generates the last chunk with correct decoder result.
        LastHttpContent invalidChunk = channel.readInbound();
        assertTrue(invalidChunk.decoderResult().isFailure());
        invalidChunk.release();

        // And no more messages should be produced by the decoder.
        assertNull(channel.readInbound());

        // .. even after the connection is closed.
        assertFalse(channel.finish());
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
        assertInstanceOf(PrematureChannelClosureException.class, message.decoderResult().cause());
        assertNull(channel.readInbound());
    }

    @Test
    public void testTrailerWithEmptyLineInSeparateBuffer() {
        HttpResponseDecoder decoder = new HttpResponseDecoder();
        EmbeddedChannel channel = new EmbeddedChannel(decoder);

        String headers = "HTTP/1.1 200 OK\r\n"
                + "Transfer-Encoding: chunked\r\n"
                + "Trailer: My-Trailer\r\n";
        assertFalse(channel.writeInbound(Unpooled.copiedBuffer(headers.getBytes(CharsetUtil.US_ASCII))));
        assertTrue(channel.writeInbound(Unpooled.copiedBuffer("\r\n".getBytes(CharsetUtil.US_ASCII))));

        assertTrue(channel.writeInbound(Unpooled.copiedBuffer("0\r\n", CharsetUtil.US_ASCII)));
        assertTrue(channel.writeInbound(Unpooled.copiedBuffer("My-Trailer: 42\r\n", CharsetUtil.US_ASCII)));
        assertTrue(channel.writeInbound(Unpooled.copiedBuffer("\r\n", CharsetUtil.US_ASCII)));

        HttpResponse response = channel.readInbound();
        assertEquals(2, response.headers().size());
        assertEquals("chunked", response.headers().get(HttpHeaderNames.TRANSFER_ENCODING));
        assertEquals("My-Trailer", response.headers().get(HttpHeaderNames.TRAILER));

        LastHttpContent lastContent = channel.readInbound();
        assertEquals(1, lastContent.trailingHeaders().size());
        assertEquals("42", lastContent.trailingHeaders().get("My-Trailer"));
        assertEquals(0, lastContent.content().readableBytes());
        lastContent.release();

        assertFalse(channel.finish());
    }

    @Test
    public void testWhitespace() {
        EmbeddedChannel channel = new EmbeddedChannel(new HttpResponseDecoder());
        String requestStr = "HTTP/1.1 200 OK\r\n" +
                "Transfer-Encoding : chunked\r\n" +
                "Host: netty.io\n\r\n";

        assertTrue(channel.writeInbound(Unpooled.copiedBuffer(requestStr, CharsetUtil.US_ASCII)));
        HttpResponse response = channel.readInbound();
        assertFalse(response.decoderResult().isFailure());
        assertEquals(HttpHeaderValues.CHUNKED.toString(), response.headers().get(HttpHeaderNames.TRANSFER_ENCODING));
        assertEquals("netty.io", response.headers().get(HttpHeaderNames.HOST));
        assertFalse(channel.finish());
    }

    @Test
    public void testHttpMessageDecoderResult() {
        String responseStr = "HTTP/1.1 200 OK\r\n" +
                "Content-Length: 11\r\n" +
                "Connection: close\r\n\r\n" +
                "Lorem ipsum";
        EmbeddedChannel channel = new EmbeddedChannel(new HttpResponseDecoder());
        assertTrue(channel.writeInbound(Unpooled.copiedBuffer(responseStr, CharsetUtil.US_ASCII)));
        HttpResponse response = channel.readInbound();
        assertTrue(response.decoderResult().isSuccess());
        assertInstanceOf(HttpMessageDecoderResult.class, response.decoderResult());
        HttpMessageDecoderResult decoderResult = (HttpMessageDecoderResult) response.decoderResult();
        assertEquals(15, decoderResult.initialLineLength());
        assertEquals(35, decoderResult.headerSize());
        assertEquals(50, decoderResult.totalSize());
        HttpContent c = channel.readInbound();
        c.release();
        assertFalse(channel.finish());
    }

    @Test
    public void testStatusWithoutReasonPhrase() {
        String responseStr = "HTTP/1.1 200 \r\n" +
                "Content-Length: 0\r\n\r\n";
        EmbeddedChannel channel = new EmbeddedChannel(new HttpResponseDecoder());
        assertTrue(channel.writeInbound(Unpooled.copiedBuffer(responseStr, CharsetUtil.US_ASCII)));
        HttpResponse response = channel.readInbound();
        assertTrue(response.decoderResult().isSuccess());
        assertEquals(HttpResponseStatus.OK, response.status());
        HttpContent c = channel.readInbound();
        c.release();
        assertFalse(channel.finish());
    }

    @Test
    public void testHeaderNameStartsWithControlChar1c() {
        testHeaderNameStartsWithControlChar(0x1c);
    }

    @Test
    public void testHeaderNameStartsWithControlChar1d() {
        testHeaderNameStartsWithControlChar(0x1d);
    }

    @Test
    public void testHeaderNameStartsWithControlChar1e() {
        testHeaderNameStartsWithControlChar(0x1e);
    }

    @Test
    public void testHeaderNameStartsWithControlChar1f() {
        testHeaderNameStartsWithControlChar(0x1f);
    }

    @Test
    public void testHeaderNameStartsWithControlChar0c() {
        testHeaderNameStartsWithControlChar(0x0c);
    }

    private void testHeaderNameStartsWithControlChar(int controlChar) {
        ByteBuf responseBuffer = Unpooled.buffer();
        responseBuffer.writeCharSequence("HTTP/1.1 200 OK\r\n" +
                "Host: netty.io\r\n", CharsetUtil.US_ASCII);
        responseBuffer.writeByte(controlChar);
        responseBuffer.writeCharSequence("Transfer-Encoding: chunked\r\n\r\n", CharsetUtil.US_ASCII);
        testInvalidHeaders0(responseBuffer);
    }

    @Test
    public void testHeaderNameEndsWithControlChar1c() {
        testHeaderNameEndsWithControlChar(0x1c);
    }

    @Test
    public void testHeaderNameEndsWithControlChar1d() {
        testHeaderNameEndsWithControlChar(0x1d);
    }

    @Test
    public void testHeaderNameEndsWithControlChar1e() {
        testHeaderNameEndsWithControlChar(0x1e);
    }

    @Test
    public void testHeaderNameEndsWithControlChar1f() {
        testHeaderNameEndsWithControlChar(0x1f);
    }

    @Test
    public void testHeaderNameEndsWithControlChar0c() {
        testHeaderNameEndsWithControlChar(0x0c);
    }

    private void testHeaderNameEndsWithControlChar(int controlChar) {
        ByteBuf responseBuffer = Unpooled.buffer();
        responseBuffer.writeCharSequence("HTTP/1.1 200 OK\r\n" +
                "Host: netty.io\r\n", CharsetUtil.US_ASCII);
        responseBuffer.writeCharSequence("Transfer-Encoding", CharsetUtil.US_ASCII);
        responseBuffer.writeByte(controlChar);
        responseBuffer.writeCharSequence(": chunked\r\n\r\n", CharsetUtil.US_ASCII);
        testInvalidHeaders0(responseBuffer);
    }

    @ParameterizedTest
    @ValueSource(strings = { "HTP/1.1", "HTTP", "HTTP/1x", "Something/1.1", "HTTP/1",
            "HTTP/1.11", "HTTP/11.1", "HTTP/A.1", "HTTP/1.B"})
    public void testInvalidVersion(String version) {
        testInvalidHeaders0(Unpooled.copiedBuffer(
                version + " 200 OK\n\r\nHost: whatever\r\n\r\n", CharsetUtil.US_ASCII));
    }

    private static void testInvalidHeaders0(ByteBuf responseBuffer) {
        EmbeddedChannel channel = new EmbeddedChannel(new HttpResponseDecoder());
        assertTrue(channel.writeInbound(responseBuffer));
        HttpResponse response = channel.readInbound();
        assertInstanceOf(IllegalArgumentException.class, response.decoderResult().cause());
        assertTrue(response.decoderResult().isFailure());
        ReferenceCountUtil.release(response);
        assertFalse(channel.finish());
    }
}
