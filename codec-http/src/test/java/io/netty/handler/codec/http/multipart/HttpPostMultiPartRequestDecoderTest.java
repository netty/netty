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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpConstants;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

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

    private void commonTestBigFileDelimiterInMiddleChunk(HttpDataFactory factory, boolean inMemory)
            throws IOException {
        int nbChunks = 100;
        int bytesPerChunk = 100000;
        int bytesLastChunk = 10000;
        int fileSize = bytesPerChunk * nbChunks + bytesLastChunk; // set Xmx to a number lower than this and it crashes

        String delimiter = "--861fbeab-cd20-470c-9609-d40a0f704466";
        String prefix = delimiter + "\n" +
                "Content-Disposition: form-data; name=\"image\"; filename=\"guangzhou.jpeg\"\n" +
                "Content-Type: image/jpeg\n" +
                "Content-Length: " + fileSize + "\n" +
                "\n";

        String suffix1 = "\n" +
                "--861fbeab-";
        String suffix2 = "cd20-470c-9609-d40a0f704466--\n";
        String suffix = suffix1 + suffix2;

        HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/upload");
        request.headers().set("content-type", "multipart/form-data; boundary=861fbeab-cd20-470c-9609-d40a0f704466");
        request.headers().set("content-length", prefix.length() + fileSize + suffix.length());

        HttpPostMultipartRequestDecoder decoder = new HttpPostMultipartRequestDecoder(factory, request);
        ByteBuf buf = Unpooled.wrappedBuffer(prefix.getBytes(CharsetUtil.UTF_8));
        decoder.offer(new DefaultHttpContent(buf));
        assertNotNull(((HttpData) decoder.currentPartialHttpData()).content());
        buf.release();

        byte[] body = new byte[bytesPerChunk];
        Arrays.fill(body, (byte) 1);
        // Set first bytes as CRLF to ensure it is correctly getting the last CRLF
        body[0] = HttpConstants.CR;
        body[1] = HttpConstants.LF;
        for (int i = 0; i < nbChunks; i++) {
            ByteBuf content = Unpooled.wrappedBuffer(body, 0, bytesPerChunk);
            decoder.offer(new DefaultHttpContent(content)); // **OutOfMemory previously here**
            assertNotNull(((HttpData) decoder.currentPartialHttpData()).content());
            content.release();
        }

        byte[] bsuffix1 = suffix1.getBytes(CharsetUtil.UTF_8);
        byte[] previousLastbody = new byte[bytesLastChunk - bsuffix1.length];
        byte[] bdelimiter = delimiter.getBytes(CharsetUtil.UTF_8);
        byte[] lastbody = new byte[2 * bsuffix1.length];
        Arrays.fill(previousLastbody, (byte) 1);
        previousLastbody[0] = HttpConstants.CR;
        previousLastbody[1] = HttpConstants.LF;
        Arrays.fill(lastbody, (byte) 1);
        // put somewhere a not valid delimiter
        for (int i = 0; i < bdelimiter.length; i++) {
            previousLastbody[i + 10] = bdelimiter[i];
        }
        lastbody[0] = HttpConstants.CR;
        lastbody[1] = HttpConstants.LF;
        for (int i = 0; i < bsuffix1.length; i++) {
            lastbody[bsuffix1.length + i] = bsuffix1[i];
        }

        ByteBuf content2 = Unpooled.wrappedBuffer(previousLastbody, 0, previousLastbody.length);
        decoder.offer(new DefaultHttpContent(content2));
        assertNotNull(((HttpData) decoder.currentPartialHttpData()).content());
        content2.release();
        content2 = Unpooled.wrappedBuffer(lastbody, 0, lastbody.length);
        decoder.offer(new DefaultHttpContent(content2));
        assertNotNull(((HttpData) decoder.currentPartialHttpData()).content());
        content2.release();
        content2 = Unpooled.wrappedBuffer(suffix2.getBytes(CharsetUtil.UTF_8));
        decoder.offer(new DefaultHttpContent(content2));
        assertNull(decoder.currentPartialHttpData());
        content2.release();
        decoder.offer(new DefaultLastHttpContent());

        FileUpload data = (FileUpload) decoder.getBodyHttpDatas().get(0);
        assertEquals(data.length(), fileSize);
        assertEquals(inMemory, data.isInMemory());
        if (data.isInMemory()) {
            // To be done only if not inMemory: assertEquals(data.get().length, fileSize);
            assertFalse(data.getByteBuf().capacity() < 1024 * 1024,
                    "Capacity should be higher than 1M");
        }
        assertTrue(decoder.getCurrentAllocatedCapacity() < 1024 * 1024,
                "Capacity should be less than 1M");
        InterfaceHttpData[] httpDatas = decoder.getBodyHttpDatas().toArray(new InterfaceHttpData[0]);
        for (InterfaceHttpData httpData : httpDatas) {
            assertEquals(1, httpData.refCnt(), "Before cleanAllHttpData should be 1");
        }
        factory.cleanAllHttpData();
        for (InterfaceHttpData httpData : httpDatas) {
            assertEquals(inMemory? 1 : 0, httpData.refCnt(), "Before cleanAllHttpData should be 1 if in Memory");
        }
        decoder.destroy();
        for (InterfaceHttpData httpData : httpDatas) {
            assertEquals(0, httpData.refCnt(), "RefCnt should be 0");
        }
    }

    @Test
    public void testBIgFileUploadDelimiterInMiddleChunkDecoderDiskFactory() throws IOException {
        // Factory using Disk mode
        HttpDataFactory factory = new DefaultHttpDataFactory(true);

        commonTestBigFileDelimiterInMiddleChunk(factory, false);
    }

    @Test
    public void testBIgFileUploadDelimiterInMiddleChunkDecoderMemoryFactory() throws IOException {
        // Factory using Memory mode
        HttpDataFactory factory = new DefaultHttpDataFactory(false);

        commonTestBigFileDelimiterInMiddleChunk(factory, true);
    }

    @Test
    public void testBIgFileUploadDelimiterInMiddleChunkDecoderMixedFactory() throws IOException {
        // Factory using Mixed mode, where file shall be on Disk
        HttpDataFactory factory = new DefaultHttpDataFactory(10000);

        commonTestBigFileDelimiterInMiddleChunk(factory, false);
    }

    @Test
    public void testNotBadReleaseBuffersDuringDecodingDiskFactory() throws IOException {
        // Using Disk Factory
        HttpDataFactory factory = new DefaultHttpDataFactory(true);
        commonNotBadReleaseBuffersDuringDecoding(factory, false);
    }
    @Test
    public void testNotBadReleaseBuffersDuringDecodingMemoryFactory() throws IOException {
        // Using Memory Factory
        HttpDataFactory factory = new DefaultHttpDataFactory(false);
        commonNotBadReleaseBuffersDuringDecoding(factory, true);
    }
    @Test
    public void testNotBadReleaseBuffersDuringDecodingMixedFactory() throws IOException {
        // Using Mixed Factory
        HttpDataFactory factory = new DefaultHttpDataFactory(100);
        commonNotBadReleaseBuffersDuringDecoding(factory, false);
    }

    private void commonNotBadReleaseBuffersDuringDecoding(HttpDataFactory factory, boolean inMemory)
            throws IOException {
        int nbItems = 20;
        int bytesPerItem = 1000;
        int maxMemory = 500;

        String prefix1 = "\n--861fbeab-cd20-470c-9609-d40a0f704466\n" +
                "Content-Disposition: form-data; name=\"image";
        String prefix2 =
                "\"; filename=\"guangzhou.jpeg\"\n" +
                        "Content-Type: image/jpeg\n" +
                        "Content-Length: " + bytesPerItem + "\n" + "\n";

        String suffix = "\n--861fbeab-cd20-470c-9609-d40a0f704466--\n";

        HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/upload");
        request.headers().set("content-type", "multipart/form-data; boundary=861fbeab-cd20-470c-9609-d40a0f704466");
        request.headers().set("content-length", nbItems * (prefix1.length() + prefix2.length() + 2 + bytesPerItem)
                + suffix.length());
        HttpPostMultipartRequestDecoder decoder = new HttpPostMultipartRequestDecoder(factory, request);
        decoder.setDiscardThreshold(maxMemory);
        for (int rank = 0; rank < nbItems; rank++) {
            byte[] bp1 = prefix1.getBytes(CharsetUtil.UTF_8);
            byte[] bp2 = prefix2.getBytes(CharsetUtil.UTF_8);
            byte[] prefix = new byte[bp1.length + 2 + bp2.length];
            for (int i = 0; i < bp1.length; i++) {
                prefix[i] = bp1[i];
            }
            byte[] brank = Integer.toString(10 + rank).getBytes(CharsetUtil.UTF_8);
            prefix[bp1.length] = brank[0];
            prefix[bp1.length + 1] = brank[1];
            for (int i = 0; i < bp2.length; i++) {
                prefix[bp1.length + 2 + i] = bp2[i];
            }
            ByteBuf buf = Unpooled.wrappedBuffer(prefix);
            decoder.offer(new DefaultHttpContent(buf));
            buf.release();
            byte[] body = new byte[bytesPerItem];
            Arrays.fill(body, (byte) rank);
            ByteBuf content = Unpooled.wrappedBuffer(body, 0, bytesPerItem);
            decoder.offer(new DefaultHttpContent(content));
            content.release();
        }
        byte[] lastbody = suffix.getBytes(CharsetUtil.UTF_8);
        ByteBuf content2 = Unpooled.wrappedBuffer(lastbody, 0, lastbody.length);
        decoder.offer(new DefaultHttpContent(content2));
        content2.release();
        decoder.offer(new DefaultLastHttpContent());

        for (int rank = 0; rank < nbItems; rank++) {
            FileUpload data = (FileUpload) decoder.getBodyHttpData("image" + (10 + rank));
            assertEquals(data.length(), bytesPerItem);
            assertEquals(inMemory, data.isInMemory());
            byte[] body = new byte[bytesPerItem];
            Arrays.fill(body, (byte) rank);
            assertTrue(Arrays.equals(body, data.get()));
        }
        // To not be done since will load full file on memory: assertEquals(data.get().length, fileSize);
        // Not mandatory since implicitely called during destroy of decoder
        for (InterfaceHttpData httpData: decoder.getBodyHttpDatas()) {
            httpData.release();
            factory.removeHttpDataFromClean(request, httpData);
        }
        factory.cleanAllHttpData();
        decoder.destroy();
    }
}
