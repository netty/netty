/*
 * Copyright 2012 The Netty Project
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
import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;

import static org.junit.Assert.*;

/**
 */
public class HttpRequestEncoderTest {

    @SuppressWarnings("deprecation")
    private static ByteBuf[] getBuffers() {
        return new ByteBuf[]{
                Unpooled.buffer(128).order(ByteOrder.BIG_ENDIAN),
                Unpooled.buffer(128).order(ByteOrder.LITTLE_ENDIAN),
                Unpooled.wrappedBuffer(ByteBuffer.allocate(128).order(ByteOrder.BIG_ENDIAN)).resetWriterIndex(),
                Unpooled.wrappedBuffer(ByteBuffer.allocate(128).order(ByteOrder.LITTLE_ENDIAN)).resetWriterIndex()
        };
    }

    @Test
    public void testUriWithoutPath() throws Exception {
        for (ByteBuf buffer : getBuffers()) {
            HttpRequestEncoder encoder = new HttpRequestEncoder();
            encoder.encodeInitialLine(buffer, new DefaultHttpRequest(HttpVersion.HTTP_1_1,
                    HttpMethod.GET, "http://localhost"));
            String req = buffer.toString(Charset.forName("US-ASCII"));
            assertEquals("GET http://localhost/ HTTP/1.1\r\n", req);
            buffer.release();
        }
    }

    @Test
    public void testUriWithoutPath2() throws Exception {
        for (ByteBuf buffer : getBuffers()) {
            HttpRequestEncoder encoder = new HttpRequestEncoder();
            encoder.encodeInitialLine(buffer, new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET,
                    "http://localhost:9999?p1=v1"));
            String req = buffer.toString(Charset.forName("US-ASCII"));
            assertEquals("GET http://localhost:9999/?p1=v1 HTTP/1.1\r\n", req);
            buffer.release();
        }
    }

    @Test
    public void testUriWithEmptyPath() throws Exception {
        for (ByteBuf buffer : getBuffers()) {
            HttpRequestEncoder encoder = new HttpRequestEncoder();
            encoder.encodeInitialLine(buffer, new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET,
                    "http://localhost:9999/?p1=v1"));
            String req = buffer.toString(Charset.forName("US-ASCII"));
            assertEquals("GET http://localhost:9999/?p1=v1 HTTP/1.1\r\n", req);
            buffer.release();
        }
    }

    @Test
    public void testUriWithPath() throws Exception {
        for (ByteBuf buffer : getBuffers()) {
            HttpRequestEncoder encoder = new HttpRequestEncoder();
            encoder.encodeInitialLine(buffer, new DefaultHttpRequest(HttpVersion.HTTP_1_1,
                    HttpMethod.GET, "http://localhost/"));
            String req = buffer.toString(Charset.forName("US-ASCII"));
            assertEquals("GET http://localhost/ HTTP/1.1\r\n", req);
            buffer.release();
        }
    }

    @Test
    public void testAbsPath() throws Exception {
        for (ByteBuf buffer : getBuffers()) {
            HttpRequestEncoder encoder = new HttpRequestEncoder();
            encoder.encodeInitialLine(buffer, new DefaultHttpRequest(HttpVersion.HTTP_1_1,
                    HttpMethod.GET, "/"));
            String req = buffer.toString(Charset.forName("US-ASCII"));
            assertEquals("GET / HTTP/1.1\r\n", req);
            buffer.release();
        }
    }

    @Test
    public void testEmptyAbsPath() throws Exception {
        for (ByteBuf buffer : getBuffers()) {
            HttpRequestEncoder encoder = new HttpRequestEncoder();
            encoder.encodeInitialLine(buffer, new DefaultHttpRequest(HttpVersion.HTTP_1_1,
                    HttpMethod.GET, ""));
            String req = buffer.toString(Charset.forName("US-ASCII"));
            assertEquals("GET / HTTP/1.1\r\n", req);
            buffer.release();
        }
    }

    @Test
    public void testQueryStringPath() throws Exception {
        for (ByteBuf buffer : getBuffers()) {
            HttpRequestEncoder encoder = new HttpRequestEncoder();
            encoder.encodeInitialLine(buffer, new DefaultHttpRequest(HttpVersion.HTTP_1_1,
                    HttpMethod.GET, "/?url=http://example.com"));
            String req = buffer.toString(Charset.forName("US-ASCII"));
            assertEquals("GET /?url=http://example.com HTTP/1.1\r\n", req);
            buffer.release();
        }
    }
}
