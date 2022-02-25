/*
 * Copyright 2012 The Netty Project
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

import io.netty5.buffer.ByteBuf;
import io.netty5.buffer.Unpooled;
import io.netty5.buffer.api.Buffer;
import io.netty5.channel.embedded.EmbeddedChannel;
import io.netty5.handler.codec.DecoderResult;
import io.netty5.util.CharsetUtil;
import io.netty5.util.IllegalReferenceCountException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;

import static io.netty5.buffer.api.DefaultBufferAllocators.preferredAllocator;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 */
public class HttpRequestEncoderTest {

    private static Buffer[] getBuffers() {
        return new Buffer[]{
                preferredAllocator().allocate(128),
                preferredAllocator().allocate(128).writerOffset(0),
        };
    }

    @Test
    public void testUriWithoutPath() throws Exception {
        for (Buffer buffer : getBuffers()) {
            HttpRequestEncoder encoder = new HttpRequestEncoder();
            encoder.encodeInitialLine(buffer, new DefaultHttpRequest(HttpVersion.HTTP_1_1,
                    HttpMethod.GET, "http://localhost"));
            String req = buffer.toString(US_ASCII);
            assertEquals("GET http://localhost/ HTTP/1.1\r\n", req);
            buffer.close();
        }
    }

    @Test
    public void testUriWithoutPath2() throws Exception {
        for (Buffer buffer : getBuffers()) {
            HttpRequestEncoder encoder = new HttpRequestEncoder();
            encoder.encodeInitialLine(buffer, new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET,
                    "http://localhost:9999?p1=v1"));
            String req = buffer.toString(US_ASCII);
            assertEquals("GET http://localhost:9999/?p1=v1 HTTP/1.1\r\n", req);
            buffer.close();
        }
    }

    @Test
    public void testUriWithEmptyPath() throws Exception {
        for (Buffer buffer : getBuffers()) {
            HttpRequestEncoder encoder = new HttpRequestEncoder();
            encoder.encodeInitialLine(buffer, new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET,
                    "http://localhost:9999/?p1=v1"));
            String req = buffer.toString(US_ASCII);
            assertEquals("GET http://localhost:9999/?p1=v1 HTTP/1.1\r\n", req);
            buffer.close();
        }
    }

    @Test
    public void testUriWithPath() throws Exception {
        for (Buffer buffer : getBuffers()) {
            HttpRequestEncoder encoder = new HttpRequestEncoder();
            encoder.encodeInitialLine(buffer, new DefaultHttpRequest(HttpVersion.HTTP_1_1,
                    HttpMethod.GET, "http://localhost/"));
            String req = buffer.toString(US_ASCII);
            assertEquals("GET http://localhost/ HTTP/1.1\r\n", req);
            buffer.close();
        }
    }

    @Test
    public void testAbsPath() throws Exception {
        for (Buffer buffer : getBuffers()) {
            HttpRequestEncoder encoder = new HttpRequestEncoder();
            encoder.encodeInitialLine(buffer, new DefaultHttpRequest(HttpVersion.HTTP_1_1,
                    HttpMethod.GET, "/"));
            String req = buffer.toString(US_ASCII);
            assertEquals("GET / HTTP/1.1\r\n", req);
            buffer.close();
        }
    }

    @Test
    public void testEmptyAbsPath() throws Exception {
        for (Buffer buffer : getBuffers()) {
            HttpRequestEncoder encoder = new HttpRequestEncoder();
            encoder.encodeInitialLine(buffer, new DefaultHttpRequest(HttpVersion.HTTP_1_1,
                    HttpMethod.GET, ""));
            String req = buffer.toString(US_ASCII);
            assertEquals("GET / HTTP/1.1\r\n", req);
            buffer.close();
        }
    }

    @Test
    public void testQueryStringPath() throws Exception {
        for (Buffer buffer : getBuffers()) {
            HttpRequestEncoder encoder = new HttpRequestEncoder();
            encoder.encodeInitialLine(buffer, new DefaultHttpRequest(HttpVersion.HTTP_1_1,
                    HttpMethod.GET, "/?url=http://example.com"));
            String req = buffer.toString(US_ASCII);
            assertEquals("GET /?url=http://example.com HTTP/1.1\r\n", req);
            buffer.close();
        }
    }

    @Test
    public void testEmptyReleasedBufferShouldNotWriteEmptyBufferToChannel() {
        HttpRequestEncoder encoder = new HttpRequestEncoder();
        final EmbeddedChannel channel = new EmbeddedChannel(encoder);
        final ByteBuf buf = Unpooled.buffer();
        buf.release();
        ExecutionException e = assertThrows(ExecutionException.class, () -> channel.writeAndFlush(buf).get());
        assertThat(e.getCause().getCause(), is(instanceOf(IllegalReferenceCountException.class)));

        channel.finishAndReleaseAll();
    }

    @Test
    public void testEmptyBufferShouldPassThrough() throws Exception {
        HttpRequestEncoder encoder = new HttpRequestEncoder();
        EmbeddedChannel channel = new EmbeddedChannel(encoder);
        ByteBuf buffer = Unpooled.buffer();
        channel.writeAndFlush(buffer).get();
        channel.finishAndReleaseAll();
        assertEquals(0, buffer.refCnt());
    }

    @Test
    public void testEmptyContentsChunked() {
        testEmptyContents(true, false);
    }

    @Test
    public void testEmptyContentsChunkedWithTrailers() {
        testEmptyContents(true, true);
    }

    @Test
    public void testEmptyContentsNotChunked() {
        testEmptyContents(false, false);
    }

    @Test
    public void testEmptyContentNotsChunkedWithTrailers() {
        testEmptyContents(false, true);
    }

    private void testEmptyContents(boolean chunked, boolean trailers) {
        HttpRequestEncoder encoder = new HttpRequestEncoder();
        EmbeddedChannel channel = new EmbeddedChannel(encoder);
        HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/");
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

    /**
     * A test that checks for a NPE that would occur if when processing {@link EmptyLastHttpContent}
     * when a certain initialization order of {@link EmptyHttpHeaders} would occur.
     */
    @Test
    public void testForChunkedRequestNpe() {
        EmbeddedChannel channel = new EmbeddedChannel(new HttpRequestEncoder());
        assertTrue(channel.writeOutbound(new CustomHttpRequest()));
        assertTrue(channel.writeOutbound(new DefaultHttpContent(
                preferredAllocator().allocate(16).writeCharSequence("test", CharsetUtil.US_ASCII))));
        assertTrue(channel.writeOutbound(new EmptyLastHttpContent(preferredAllocator())));
        assertTrue(channel.finishAndReleaseAll());
    }

    /**
     * This class is required to triggered the desired initialization order of {@link EmptyHttpHeaders}.
     * If {@link DefaultHttpRequest} is used, the {@link HttpHeaders} class will be initialized before {@link HttpUtil}
     * and the test won't trigger the original issue.
     */
    private static final class CustomHttpRequest implements HttpRequest {

        @Override
        public DecoderResult decoderResult() {
            return DecoderResult.SUCCESS;
        }

        @Override
        public void setDecoderResult(DecoderResult result) {
        }

        @Override
        public HttpVersion getProtocolVersion() {
            return HttpVersion.HTTP_1_1;
        }

        @Override
        public HttpVersion protocolVersion() {
            return getProtocolVersion();
        }

        @Override
        public HttpHeaders headers() {
            DefaultHttpHeaders headers = new DefaultHttpHeaders();
            headers.add("Transfer-Encoding", "chunked");
            return headers;
        }

        @Override
        public HttpMethod method() {
            return HttpMethod.POST;
        }

        @Override
        public HttpRequest setMethod(HttpMethod method) {
            return this;
        }

        @Override
        public String uri() {
            return "/";
        }

        @Override
        public HttpRequest setUri(String uri) {
            return this;
        }

        @Override
        public HttpRequest setProtocolVersion(HttpVersion version) {
            return this;
        }
    }
}
