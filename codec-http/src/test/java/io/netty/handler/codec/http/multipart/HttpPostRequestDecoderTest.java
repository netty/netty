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
package io.netty.handler.codec.http.multipart;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

/** {@link HttpPostRequestDecoder} test case. */
public class HttpPostRequestDecoderTest {

    @Test
    public void testBinaryStreamUploadWithSpace() throws Exception {
        testBinaryStreamUpload(true);
    }

    // https://github.com/netty/netty/issues/1575
    @Test
    public void testBinaryStreamUploadWithoutSpace() throws Exception {
        testBinaryStreamUpload(false);
    }

    private static void testBinaryStreamUpload(boolean withSpace) throws Exception {
        final String boundary = "dLV9Wyq26L_-JQxk6ferf-RT153LhOO";
        final String contentTypeValue;
        if (withSpace) {
            contentTypeValue = "multipart/form-data; boundary=" + boundary;
        } else {
            contentTypeValue = "multipart/form-data;boundary=" + boundary;
        }
        final DefaultHttpRequest req = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST,
                "http://localhost");

        req.setDecoderResult(DecoderResult.SUCCESS);
        req.headers().add(HttpHeaders.Names.CONTENT_TYPE, contentTypeValue);
        req.headers().add(HttpHeaders.Names.TRANSFER_ENCODING, HttpHeaders.Values.CHUNKED);

        // Force to use memory-based data.
        final DefaultHttpDataFactory inMemoryFactory = new DefaultHttpDataFactory(false);

        for (String data : Arrays.asList("", "\r", "\r\r", "\r\r\r")) {
            final String body =
                    "--" + boundary + "\r\n" +
                    "Content-Disposition: form-data; name=\"file\"; filename=\"tmp-0.txt\"\r\n" +
                    "Content-Type: image/gif\r\n" +
                    "\r\n" +
                    data + "\r\n" +
                    "--" + boundary + "--\r\n";

            // Create decoder instance to test.
            final HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(inMemoryFactory, req);

            decoder.offer(new DefaultHttpContent(Unpooled.copiedBuffer(body, CharsetUtil.UTF_8)));
            decoder.offer(new DefaultHttpContent(Unpooled.EMPTY_BUFFER));

            // Validate it's enough chunks to decode upload.
            assertTrue(decoder.hasNext());

            // Decode binary upload.
            MemoryFileUpload upload = (MemoryFileUpload) decoder.next();

            // Validate data has been parsed correctly as it was passed into request.
            assertEquals("Invalid decoded data [data=" + data.replaceAll("\r", "\\\\r") + ", upload=" + upload + ']',
                data, upload.getString(CharsetUtil.UTF_8));
        }
    }

    // See https://github.com/netty/netty/issues/1089
    @Test
    public void testFullHttpRequestUpload() throws Exception {
        final String boundary = "dLV9Wyq26L_-JQxk6ferf-RT153LhOO";

        final DefaultFullHttpRequest req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST,
                "http://localhost");

        req.setDecoderResult(DecoderResult.SUCCESS);
        req.headers().add(HttpHeaders.Names.CONTENT_TYPE, "multipart/form-data; boundary=" + boundary);
        req.headers().add(HttpHeaders.Names.TRANSFER_ENCODING, HttpHeaders.Values.CHUNKED);

        // Force to use memory-based data.
        final DefaultHttpDataFactory inMemoryFactory = new DefaultHttpDataFactory(false);

        for (String data : Arrays.asList("", "\r", "\r\r", "\r\r\r")) {
            final String body =
                    "--" + boundary + "\r\n" +
                            "Content-Disposition: form-data; name=\"file\"; filename=\"tmp-0.txt\"\r\n" +
                            "Content-Type: image/gif\r\n" +
                            "\r\n" +
                            data + "\r\n" +
                            "--" + boundary + "--\r\n";

            req.content().writeBytes(body.getBytes(CharsetUtil.UTF_8));
        }
        // Create decoder instance to test.
        final HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(inMemoryFactory, req);
        assertFalse(decoder.getBodyHttpDatas().isEmpty());
    }
}
