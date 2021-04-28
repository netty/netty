/*
 * Copyright 2021 The Netty Project
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
package io.netty.handler.codec.http.multipart;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;
import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class HttpPostMultiPartRequestDecoderTest {

    @Test
    public void testDecodeFullHttpRequestWithNoContentTypeHeader() {
        FullHttpRequest req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/");
        try {
            new HttpPostMultipartRequestDecoder(req);
            fail("Was expecting an ErrorDataDecoderException");
        } catch (HttpPostRequestDecoder.ErrorDataDecoderException expected) {
            // expected
        } finally {
            assertTrue(req.release());
        }
    }

    @Test
    public void testDecodeFullHttpRequestWithInvalidCharset() {
        FullHttpRequest req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/");
        req.headers().set(HttpHeaderNames.CONTENT_TYPE,
                "multipart/form-data; boundary=--89421926422648 [; charset=UTF-8]");

        try {
            new HttpPostMultipartRequestDecoder(req);
            fail("Was expecting an ErrorDataDecoderException");
        } catch (HttpPostRequestDecoder.ErrorDataDecoderException expected) {
            // expected
        } finally {
            assertTrue(req.release());
        }
    }

    @Test
    public void testDecodeFullHttpRequestWithInvalidPayloadReleaseBuffer() {
        String content = "\n--861fbeab-cd20-470c-9609-d40a0f704466\n" +
                "Content-Disposition: form-data; name=\"image1\"; filename*=\"'some.jpeg\"\n" +
                        "Content-Type: image/jpeg\n" +
                        "Content-Length: 1\n" +
                        "x\n" +
                        "--861fbeab-cd20-470c-9609-d40a0f704466--\n";

        FullHttpRequest req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/upload",
                Unpooled.copiedBuffer(content, CharsetUtil.US_ASCII));
        req.headers().set("content-type", "multipart/form-data; boundary=861fbeab-cd20-470c-9609-d40a0f704466");
        req.headers().set("content-length", content.length());

        try {
            new HttpPostMultipartRequestDecoder(req);
            fail("Was expecting an ErrorDataDecoderException");
        } catch (HttpPostRequestDecoder.ErrorDataDecoderException expected) {
            // expected
        } finally {
            assertTrue(req.release());
        }
    }
}
