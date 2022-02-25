/*
* Copyright 2014 The Netty Project
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
package io.netty5.handler.codec.http;

import io.netty5.buffer.api.Buffer;
import io.netty5.channel.FileRegion;
import io.netty5.channel.embedded.EmbeddedChannel;
import io.netty5.util.CharsetUtil;
import org.junit.jupiter.api.Test;

import java.nio.channels.WritableByteChannel;

import static io.netty5.buffer.api.DefaultBufferAllocators.preferredAllocator;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HttpResponseEncoderTest {
    private static final long INTEGER_OVERFLOW = (long) Integer.MAX_VALUE + 1;
    private static final FileRegion FILE_REGION = new DummyLongFileRegion();

    @Test
    public void testLargeFileRegionChunked() {
        EmbeddedChannel channel = new EmbeddedChannel(new HttpResponseEncoder());
        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        response.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
        assertTrue(channel.writeOutbound(response));

        Buffer buffer = channel.readOutbound();

        assertEquals("HTTP/1.1 200 OK\r\n" + HttpHeaderNames.TRANSFER_ENCODING + ": " +
                HttpHeaderValues.CHUNKED + "\r\n\r\n", buffer.toString(CharsetUtil.US_ASCII));
        buffer.close();
        assertTrue(channel.writeOutbound(FILE_REGION));
        buffer = channel.readOutbound();
        assertEquals("80000000\r\n", buffer.toString(CharsetUtil.US_ASCII));
        buffer.close();

        FileRegion region = channel.readOutbound();
        assertSame(FILE_REGION, region);
        region.release();
        buffer = channel.readOutbound();
        assertEquals("\r\n", buffer.toString(CharsetUtil.US_ASCII));
        buffer.close();

        assertTrue(channel.writeOutbound(new EmptyLastHttpContent(preferredAllocator())));
        buffer = channel.readOutbound();
        assertEquals("0\r\n\r\n", buffer.toString(CharsetUtil.US_ASCII));
        buffer.close();

        assertFalse(channel.finish());
    }

    private static class DummyLongFileRegion implements FileRegion {

        @Override
        public long position() {
            return 0;
        }

        @Override
        public long transfered() {
            return 0;
        }

        @Override
        public long transferred() {
            return 0;
        }

        @Override
        public long count() {
            return INTEGER_OVERFLOW;
        }

        @Override
        public long transferTo(WritableByteChannel target, long position) {
            throw new UnsupportedOperationException();
        }

        @Override
        public FileRegion touch(Object hint) {
            return this;
        }

        @Override
        public FileRegion touch() {
            return this;
        }

        @Override
        public FileRegion retain() {
            return this;
        }

        @Override
        public FileRegion retain(int increment) {
            return this;
        }

        @Override
        public int refCnt() {
            return 1;
        }

        @Override
        public boolean release() {
            return false;
        }

        @Override
        public boolean release(int decrement) {
            return false;
        }
    }

    @Test
    public void testEmptyBufferBypass() {
        EmbeddedChannel channel = new EmbeddedChannel(new HttpResponseEncoder());

        // Test writing an empty buffer works when the encoder is at ST_INIT.
        try (Buffer emptyBuffer = channel.bufferAllocator().allocate(0)) {
            assertTrue(channel.writeOutbound(emptyBuffer.copy()));
            Buffer buffer = channel.readOutbound();
            assertEquals(buffer, emptyBuffer);

            // Leave the ST_INIT state.
            HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            assertTrue(channel.writeOutbound(response));
            buffer = channel.readOutbound();
            assertEquals("HTTP/1.1 200 OK\r\n\r\n", buffer.toString(CharsetUtil.US_ASCII));
            buffer.close();

            // Test writing an empty buffer works when the encoder is not at ST_INIT.
            channel.writeOutbound(emptyBuffer.copy());
            buffer = channel.readOutbound();
            assertEquals(buffer, emptyBuffer);
        }

        assertFalse(channel.finish());
    }

    @Test
    public void testEmptyContentChunked() {
        testEmptyContent(true);
    }

    @Test
    public void testEmptyContentNotChunked() {
        testEmptyContent(false);
    }

    private static void testEmptyContent(boolean chunked) {
        String content = "netty rocks";
        Buffer contentBuffer = preferredAllocator().allocate(16).writeCharSequence(content, CharsetUtil.US_ASCII);
        int length = contentBuffer.readableBytes();

        EmbeddedChannel channel = new EmbeddedChannel(new HttpResponseEncoder());
        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        if (!chunked) {
            HttpUtil.setContentLength(response, length);
        }
        assertTrue(channel.writeOutbound(response));
        assertTrue(channel.writeOutbound(new DefaultHttpContent(preferredAllocator().allocate(0))));
        assertTrue(channel.writeOutbound(new DefaultLastHttpContent(contentBuffer)));

        Buffer buffer = channel.readOutbound();
        if (!chunked) {
            assertEquals("HTTP/1.1 200 OK\r\ncontent-length: " + length + "\r\n\r\n",
                    buffer.toString(CharsetUtil.US_ASCII));
        } else {
            assertEquals("HTTP/1.1 200 OK\r\n\r\n", buffer.toString(CharsetUtil.US_ASCII));
        }
        buffer.close();

        // Test writing an empty buffer works when the encoder is not at ST_INIT.
        buffer = channel.readOutbound();
        assertEquals(0, buffer.readableBytes());
        buffer.close();

        buffer = channel.readOutbound();
        assertEquals(length, buffer.readableBytes());
        buffer.close();

        assertFalse(channel.finish());
    }

    @Test
    public void testStatusNoContent() {
        EmbeddedChannel channel = new EmbeddedChannel(new HttpResponseEncoder());
        assertEmptyResponse(channel, HttpResponseStatus.NO_CONTENT, null, false);
        assertFalse(channel.finish());
    }

    @Test
    public void testStatusNoContentContentLength() {
        EmbeddedChannel channel = new EmbeddedChannel(new HttpResponseEncoder());
        assertEmptyResponse(channel, HttpResponseStatus.NO_CONTENT, HttpHeaderNames.CONTENT_LENGTH, true);
        assertFalse(channel.finish());
    }

    @Test
    public void testStatusNoContentTransferEncoding() {
        EmbeddedChannel channel = new EmbeddedChannel(new HttpResponseEncoder());
        assertEmptyResponse(channel, HttpResponseStatus.NO_CONTENT, HttpHeaderNames.TRANSFER_ENCODING, true);
        assertFalse(channel.finish());
    }

    @Test
    public void testStatusNotModified() {
        EmbeddedChannel channel = new EmbeddedChannel(new HttpResponseEncoder());
        assertEmptyResponse(channel, HttpResponseStatus.NOT_MODIFIED, null, false);
        assertFalse(channel.finish());
    }

    @Test
    public void testStatusNotModifiedContentLength() {
        EmbeddedChannel channel = new EmbeddedChannel(new HttpResponseEncoder());
        assertEmptyResponse(channel, HttpResponseStatus.NOT_MODIFIED, HttpHeaderNames.CONTENT_LENGTH, false);
        assertFalse(channel.finish());
    }

    @Test
    public void testStatusNotModifiedTransferEncoding() {
        EmbeddedChannel channel = new EmbeddedChannel(new HttpResponseEncoder());
        assertEmptyResponse(channel, HttpResponseStatus.NOT_MODIFIED, HttpHeaderNames.TRANSFER_ENCODING, false);
        assertFalse(channel.finish());
    }

    @Test
    public void testStatusInformational() {
        EmbeddedChannel channel = new EmbeddedChannel(new HttpResponseEncoder());
        for (int code = 100; code < 200; code++) {
            HttpResponseStatus status = HttpResponseStatus.valueOf(code);
            assertEmptyResponse(channel, status, null, false);
        }
        assertFalse(channel.finish());
    }

    @Test
    public void testStatusInformationalContentLength() {
        EmbeddedChannel channel = new EmbeddedChannel(new HttpResponseEncoder());
        for (int code = 100; code < 200; code++) {
            HttpResponseStatus status = HttpResponseStatus.valueOf(code);
            assertEmptyResponse(channel, status, HttpHeaderNames.CONTENT_LENGTH, code != 101);
        }
        assertFalse(channel.finish());
    }

    @Test
    public void testStatusInformationalTransferEncoding() {
        EmbeddedChannel channel = new EmbeddedChannel(new HttpResponseEncoder());
        for (int code = 100; code < 200; code++) {
            HttpResponseStatus status = HttpResponseStatus.valueOf(code);
            assertEmptyResponse(channel, status, HttpHeaderNames.TRANSFER_ENCODING, code != 101);
        }
        assertFalse(channel.finish());
    }

    private static void assertEmptyResponse(EmbeddedChannel channel, HttpResponseStatus status,
                                            CharSequence headerName, boolean headerStripped) {
        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, status);
        if (HttpHeaderNames.CONTENT_LENGTH.contentEquals(headerName)) {
            response.headers().set(headerName, "0");
        } else if (HttpHeaderNames.TRANSFER_ENCODING.contentEquals(headerName)) {
            response.headers().set(headerName, HttpHeaderValues.CHUNKED);
        }

        assertTrue(channel.writeOutbound(response));
        assertTrue(channel.writeOutbound(new EmptyLastHttpContent(preferredAllocator())));

        Buffer buffer = channel.readOutbound();
        StringBuilder responseText = new StringBuilder();
        responseText.append(HttpVersion.HTTP_1_1).append(' ').append(status.toString()).append("\r\n");
        if (!headerStripped && headerName != null) {
            responseText.append(headerName).append(": ");

            if (HttpHeaderNames.CONTENT_LENGTH.contentEquals(headerName)) {
                responseText.append('0');
            } else {
                responseText.append(HttpHeaderValues.CHUNKED);
            }
            responseText.append("\r\n");
        }
        responseText.append("\r\n");

        assertEquals(responseText.toString(), buffer.toString(CharsetUtil.US_ASCII));

        buffer.close();

        buffer = channel.readOutbound();
        buffer.close();
    }

    @Test
    public void testEmptyContentsChunked() throws Exception {
        testEmptyContents(true, false);
    }

    @Test
    public void testEmptyContentsChunkedWithTrailers() throws Exception {
        testEmptyContents(true, true);
    }

    @Test
    public void testEmptyContentsNotChunked() throws Exception {
        testEmptyContents(false, false);
    }

    @Test
    public void testEmptyContentNotsChunkedWithTrailers() throws Exception {
        testEmptyContents(false, true);
    }

    private void testEmptyContents(boolean chunked, boolean trailers) throws Exception {
        HttpResponseEncoder encoder = new HttpResponseEncoder();
        EmbeddedChannel channel = new EmbeddedChannel(encoder);
        HttpResponse request = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        if (chunked) {
            HttpUtil.setTransferEncodingChunked(request, true);
        }
        assertTrue(channel.writeOutbound(request));

        assertTrue(channel.writeOutbound(new DefaultHttpContent(preferredAllocator().allocate(0))));

        LastHttpContent<?> last = new DefaultLastHttpContent(preferredAllocator().allocate(0));
        if (trailers) {
            last.trailingHeaders().set("X-Netty-Test", "true");
        }
        assertTrue(channel.writeOutbound(last));

        try (Buffer head = channel.readOutbound()) {
            assertTrue(head.isAccessible());
        }

        try (Buffer content = channel.readOutbound()) {
            assertTrue(content.isAccessible());
        }

        try (Buffer lastContent = channel.readOutbound()) {
            assertTrue(lastContent.isAccessible());
        }
        assertFalse(channel.finish());
    }

    @Test
    public void testStatusResetContentTransferContentLength() {
        testStatusResetContentTransferContentLength0(HttpHeaderNames.CONTENT_LENGTH,
                                                     preferredAllocator().allocate(8).writeLong(8));
    }

    @Test
    public void testStatusResetContentTransferEncoding() {
        testStatusResetContentTransferContentLength0(HttpHeaderNames.TRANSFER_ENCODING,
                                                     preferredAllocator().allocate(8).writeLong(8));
    }

    private static void testStatusResetContentTransferContentLength0(CharSequence headerName, Buffer content) {
        EmbeddedChannel channel = new EmbeddedChannel(new HttpResponseEncoder());

        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.RESET_CONTENT);
        if (HttpHeaderNames.CONTENT_LENGTH.contentEqualsIgnoreCase(headerName)) {
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());
        } else {
            response.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
        }

        assertTrue(channel.writeOutbound(response));
        assertTrue(channel.writeOutbound(new DefaultHttpContent(content)));
        assertTrue(channel.writeOutbound(new EmptyLastHttpContent(preferredAllocator())));

        String responseText = String.valueOf(HttpVersion.HTTP_1_1) + ' ' +
                HttpResponseStatus.RESET_CONTENT + "\r\n" +
                HttpHeaderNames.CONTENT_LENGTH + ": 0\r\n" +
                "\r\n";

        StringBuilder written = new StringBuilder();
        for (;;) {
            try (Buffer buffer = channel.readOutbound()) {
                if (buffer == null) {
                    break;
                }
                written.append(buffer.toString(CharsetUtil.US_ASCII));
            }
        }

        assertEquals(responseText, written.toString());
        assertFalse(channel.finish());
    }
}
