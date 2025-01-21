/*
 * Copyright 2022 The Netty Project
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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.Test;

import static io.netty.handler.codec.http.DefaultHttpHeadersFactory.headersFactory;
import static org.junit.jupiter.api.Assertions.*;

class HttpPostStandardRequestDecoderTest {

    @Test
    void testDecodeAttributes() {
        String requestBody = "key1=value1&key2=value2";

        HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/upload");

        HttpPostStandardRequestDecoder decoder = new HttpPostStandardRequestDecoder(httpDiskDataFactory(), request);
        ByteBuf buf = Unpooled.wrappedBuffer(requestBody.getBytes(CharsetUtil.UTF_8));
        DefaultHttpContent httpContent = new DefaultLastHttpContent(buf);
        decoder.offer(httpContent);

        assertEquals(2, decoder.getBodyHttpDatas().size());
        assertMemoryAttribute(decoder.getBodyHttpData("key1"), "value1");
        assertMemoryAttribute(decoder.getBodyHttpData("key2"), "value2");
        decoder.destroy();
    }

    @Test
    void testDecodeSingleAttributeWithNoValue() {
        String requestBody = "key1";

        HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/upload",
                headersFactory().newHeaders().add("Content-Type", "application/x-www-form-urlencoded"));

        HttpPostStandardRequestDecoder decoder = new HttpPostStandardRequestDecoder(httpDiskDataFactory(), request);
        ByteBuf buf = Unpooled.wrappedBuffer(requestBody.getBytes(CharsetUtil.UTF_8));
        DefaultHttpContent httpContent = new DefaultLastHttpContent(buf);
        decoder.offer(httpContent);

        assertEquals(1, decoder.getBodyHttpDatas().size());
        assertMemoryAttribute(decoder.getBodyHttpData("key1"), "");
        decoder.destroy();
    }

    @Test
    void testDecodeSingleAttributeWithNoValueEmptyLast() {
        String requestBody = "key1";

        HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/upload",
                headersFactory().newHeaders().add("Content-Type", "application/x-www-form-urlencoded"));

        HttpPostStandardRequestDecoder decoder = new HttpPostStandardRequestDecoder(httpDiskDataFactory(), request);
        ByteBuf buf = Unpooled.wrappedBuffer(requestBody.getBytes(CharsetUtil.UTF_8));
        DefaultHttpContent httpContent = new DefaultHttpContent(buf);
        decoder.offer(httpContent);

        decoder.offer(LastHttpContent.EMPTY_LAST_CONTENT);

        assertEquals(1, decoder.getBodyHttpDatas().size());
        assertMemoryAttribute(decoder.getBodyHttpData("key1"), "");
        decoder.destroy();
    }

    @Test
    void testDecodeEndAttributeWithNoValue() {
        String requestBody = "key1=value1&key2";

        HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/upload",
                headersFactory().newHeaders().add("Content-Type", "application/x-www-form-urlencoded"));

        HttpPostStandardRequestDecoder decoder = new HttpPostStandardRequestDecoder(httpDiskDataFactory(), request);
        ByteBuf buf = Unpooled.wrappedBuffer(requestBody.getBytes(CharsetUtil.UTF_8));
        DefaultHttpContent httpContent = new DefaultLastHttpContent(buf);
        decoder.offer(httpContent);

        assertEquals(2, decoder.getBodyHttpDatas().size());
        assertMemoryAttribute(decoder.getBodyHttpData("key1"), "value1");
        assertMemoryAttribute(decoder.getBodyHttpData("key2"), "");
        decoder.destroy();
    }

    @Test
    void testDecodeJsonAttributeAsEmpty() {
        String requestBody = "{\"iAm\": \" a JSON!\"}";

        HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/upload",
                headersFactory().newHeaders().add("Content-Type", "application/json"));

        HttpPostStandardRequestDecoder decoder = new HttpPostStandardRequestDecoder(httpDiskDataFactory(), request);
        ByteBuf buf = Unpooled.wrappedBuffer(requestBody.getBytes(CharsetUtil.UTF_8));
        DefaultHttpContent httpContent = new DefaultLastHttpContent(buf);
        decoder.offer(httpContent);

        assertEquals(0, decoder.getBodyHttpDatas().size());
        decoder.destroy();
    }

    @Test
    void testDecodeJsonAttributeAsEmptyAndNoHeaders() {
        String requestBody = "{\"iAm\": \" a JSON!\"}";

        HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/upload");

        HttpPostStandardRequestDecoder decoder = new HttpPostStandardRequestDecoder(httpDiskDataFactory(), request);
        ByteBuf buf = Unpooled.wrappedBuffer(requestBody.getBytes(CharsetUtil.UTF_8));
        DefaultHttpContent httpContent = new DefaultLastHttpContent(buf);
        decoder.offer(httpContent);

        assertEquals(0, decoder.getBodyHttpDatas().size());
        decoder.destroy();
    }

    @Test
    void testDecodeStartAttributeWithNoValue() {
        String requestBody = "key1&key2=value2";

        HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/upload");

        HttpPostStandardRequestDecoder decoder = new HttpPostStandardRequestDecoder(httpDiskDataFactory(), request);
        ByteBuf buf = Unpooled.wrappedBuffer(requestBody.getBytes(CharsetUtil.UTF_8));
        DefaultHttpContent httpContent = new DefaultLastHttpContent(buf);
        decoder.offer(httpContent);

        assertEquals(2, decoder.getBodyHttpDatas().size());
        assertMemoryAttribute(decoder.getBodyHttpData("key1"), "");
        assertMemoryAttribute(decoder.getBodyHttpData("key2"), "value2");
        decoder.destroy();
    }

    @Test
    void testDecodeMultipleAttributesWithNoValue() {
        String requestBody = "key1&key2&key3";

        HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/upload",
                headersFactory().newHeaders().add("Content-Type", "application/x-www-form-urlencoded"));

        HttpPostStandardRequestDecoder decoder = new HttpPostStandardRequestDecoder(httpDiskDataFactory(), request);
        ByteBuf buf = Unpooled.wrappedBuffer(requestBody.getBytes(CharsetUtil.UTF_8));
        DefaultHttpContent httpContent = new DefaultLastHttpContent(buf);
        decoder.offer(httpContent);

        assertEquals(3, decoder.getBodyHttpDatas().size());
        assertMemoryAttribute(decoder.getBodyHttpData("key1"), "");
        assertMemoryAttribute(decoder.getBodyHttpData("key2"), "");
        assertMemoryAttribute(decoder.getBodyHttpData("key3"), "");
        decoder.destroy();
    }

    @Test
    void testDecodeNestedAttributeWithNoValue() {
        String requestBody = "key1=value1&key2&key3=value3";

        HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/upload");

        HttpPostStandardRequestDecoder decoder = new HttpPostStandardRequestDecoder(httpDiskDataFactory(), request);
        ByteBuf buf = Unpooled.wrappedBuffer(requestBody.getBytes(CharsetUtil.UTF_8));
        DefaultHttpContent httpContent = new DefaultLastHttpContent(buf);
        decoder.offer(httpContent);

        assertEquals(3, decoder.getBodyHttpDatas().size());
        assertMemoryAttribute(decoder.getBodyHttpData("key1"), "value1");
        assertMemoryAttribute(decoder.getBodyHttpData("key2"), "");
        assertMemoryAttribute(decoder.getBodyHttpData("key3"), "value3");
        decoder.destroy();
    }

    @Test
    void testDecodeAttributesWithAmpersandPrefixSkipsNullAttribute() {
        String requestBody = "&key1=value1";

        HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/upload");

        HttpPostStandardRequestDecoder decoder = new HttpPostStandardRequestDecoder(httpDiskDataFactory(), request);
        ByteBuf buf = Unpooled.wrappedBuffer(requestBody.getBytes(CharsetUtil.UTF_8));
        DefaultHttpContent httpContent = new DefaultLastHttpContent(buf);
        decoder.offer(httpContent);

        assertEquals(1, decoder.getBodyHttpDatas().size());
        assertMemoryAttribute(decoder.getBodyHttpData("key1"), "value1");
        decoder.destroy();
    }

    @Test
    void testDecodeZeroAttributesWithAmpersandPrefix() {
        String requestBody = "&";

        HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/upload");

        HttpPostStandardRequestDecoder decoder = new HttpPostStandardRequestDecoder(httpDiskDataFactory(), request);
        ByteBuf buf = Unpooled.wrappedBuffer(requestBody.getBytes(CharsetUtil.UTF_8));
        DefaultHttpContent httpContent = new DefaultLastHttpContent(buf);
        decoder.offer(httpContent);

        assertEquals(0, decoder.getBodyHttpDatas().size());
        decoder.destroy();
    }

    private static DefaultHttpDataFactory httpDiskDataFactory() {
        return new DefaultHttpDataFactory(false);
    }

    private static void assertMemoryAttribute(InterfaceHttpData data, String expectedValue) {
        assertEquals(InterfaceHttpData.HttpDataType.Attribute, data.getHttpDataType());
        assertEquals(((MemoryAttribute) data).getValue(), expectedValue);
    }

}
