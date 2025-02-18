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
package io.netty.handler.codec.http.multipart;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpConstants;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.multipart.HttpPostRequestEncoder.EncoderMode;
import io.netty.handler.codec.http.multipart.HttpPostRequestEncoder.ErrorDataEncoderException;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.StringUtil;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_DISPOSITION;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TRANSFER_ENCODING;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpMethod.POST;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static io.netty.handler.codec.http.multipart.HttpPostBodyUtil.chunkSize;
import static io.netty.util.CharsetUtil.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/** {@link HttpPostRequestEncoder} test case. */
public class HttpPostRequestEncoderTest {

    @Test
    public void testAllowedMethods() throws Exception {
        shouldThrowExceptionIfNotAllowed(HttpMethod.CONNECT);
        shouldThrowExceptionIfNotAllowed(HttpMethod.PUT);
        shouldThrowExceptionIfNotAllowed(HttpMethod.POST);
        shouldThrowExceptionIfNotAllowed(HttpMethod.PATCH);
        shouldThrowExceptionIfNotAllowed(HttpMethod.DELETE);
        shouldThrowExceptionIfNotAllowed(HttpMethod.GET);
        shouldThrowExceptionIfNotAllowed(HttpMethod.HEAD);
        shouldThrowExceptionIfNotAllowed(HttpMethod.OPTIONS);
        try {
            shouldThrowExceptionIfNotAllowed(HttpMethod.TRACE);
            fail("Should raised an exception with TRACE method");
        } catch (ErrorDataEncoderException e) {
            // Exception is willing
        }
    }

    private void shouldThrowExceptionIfNotAllowed(HttpMethod method) throws Exception {
        DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,
                method, "http://localhost");

        HttpPostRequestEncoder encoder = new HttpPostRequestEncoder(request, true);
        File file1 = new File(getClass().getResource("/file-01.txt").toURI());
        encoder.addBodyAttribute("foo", "bar");
        encoder.addBodyFileUpload("quux", file1, "text/plain", false);

        String multipartDataBoundary = encoder.multipartDataBoundary;
        String content = getRequestBody(encoder);

        String expected = "--" + multipartDataBoundary + "\r\n" +
                CONTENT_DISPOSITION + ": form-data; name=\"foo\"" + "\r\n" +
                CONTENT_LENGTH + ": 3" + "\r\n" +
                CONTENT_TYPE + ": text/plain; charset=UTF-8" + "\r\n" +
                "\r\n" +
                "bar" +
                "\r\n" +
                "--" + multipartDataBoundary + "\r\n" +
                CONTENT_DISPOSITION + ": form-data; name=\"quux\"; filename=\"file-01.txt\"" + "\r\n" +
                CONTENT_LENGTH + ": " + file1.length() + "\r\n" +
                CONTENT_TYPE + ": text/plain" + "\r\n" +
                CONTENT_TRANSFER_ENCODING + ": binary" + "\r\n" +
                "\r\n" +
                "File 01" + StringUtil.NEWLINE +
                "\r\n" +
                "--" + multipartDataBoundary + "--" + "\r\n";

        assertEquals(expected, content);
    }

    @Test
    public void testSingleFileUploadNoName() throws Exception {
        DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,
                HttpMethod.POST, "http://localhost");

        HttpPostRequestEncoder encoder = new HttpPostRequestEncoder(request, true);
        File file1 = new File(getClass().getResource("/file-01.txt").toURI());
        encoder.addBodyAttribute("foo", "bar");
        encoder.addBodyFileUpload("quux", "", file1, "text/plain", false);

        String multipartDataBoundary = encoder.multipartDataBoundary;
        String content = getRequestBody(encoder);

        String expected = "--" + multipartDataBoundary + "\r\n" +
                CONTENT_DISPOSITION + ": form-data; name=\"foo\"" + "\r\n" +
                CONTENT_LENGTH + ": 3" + "\r\n" +
                CONTENT_TYPE + ": text/plain; charset=UTF-8" + "\r\n" +
                "\r\n" +
                "bar" +
                "\r\n" +
                "--" + multipartDataBoundary + "\r\n" +
                CONTENT_DISPOSITION + ": form-data; name=\"quux\"\r\n" +
                CONTENT_LENGTH + ": " + file1.length() + "\r\n" +
                CONTENT_TYPE + ": text/plain" + "\r\n" +
                CONTENT_TRANSFER_ENCODING + ": binary" + "\r\n" +
                "\r\n" +
                "File 01" + StringUtil.NEWLINE +
                "\r\n" +
                "--" + multipartDataBoundary + "--" + "\r\n";

        assertEquals(expected, content);
    }

    @Test
    public void testMultiFileUploadInMixedMode() throws Exception {
        DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,
                HttpMethod.POST, "http://localhost");

        HttpPostRequestEncoder encoder = new HttpPostRequestEncoder(request, true);
        File file1 = new File(getClass().getResource("/file-01.txt").toURI());
        File file2 = new File(getClass().getResource("/file-02.txt").toURI());
        File file3 = new File(getClass().getResource("/file-03.txt").toURI());
        encoder.addBodyAttribute("foo", "bar");
        encoder.addBodyFileUpload("quux", file1, "text/plain", false);
        encoder.addBodyFileUpload("quux", file2, "text/plain", false);
        encoder.addBodyFileUpload("quux", file3, "text/plain", false);

        // We have to query the value of these two fields before finalizing
        // the request, which unsets one of them.
        String multipartDataBoundary = encoder.multipartDataBoundary;
        String multipartMixedBoundary = encoder.multipartMixedBoundary;
        String content = getRequestBody(encoder);

        String expected = "--" + multipartDataBoundary + "\r\n" +
                CONTENT_DISPOSITION + ": form-data; name=\"foo\"" + "\r\n" +
                CONTENT_LENGTH + ": 3" + "\r\n" +
                CONTENT_TYPE + ": text/plain; charset=UTF-8" + "\r\n" +
                "\r\n" +
                "bar" + "\r\n" +
                "--" + multipartDataBoundary + "\r\n" +
                CONTENT_DISPOSITION + ": form-data; name=\"quux\"" + "\r\n" +
                CONTENT_TYPE + ": multipart/mixed; boundary=" + multipartMixedBoundary + "\r\n" +
                "\r\n" +
                "--" + multipartMixedBoundary + "\r\n" +
                CONTENT_DISPOSITION + ": attachment; filename=\"file-01.txt\"" + "\r\n" +
                CONTENT_LENGTH + ": " + file1.length() + "\r\n" +
                CONTENT_TYPE + ": text/plain" + "\r\n" +
                CONTENT_TRANSFER_ENCODING + ": binary" + "\r\n" +
                "\r\n" +
                "File 01" + StringUtil.NEWLINE +
                "\r\n" +
                "--" + multipartMixedBoundary + "\r\n" +
                CONTENT_DISPOSITION + ": attachment; filename=\"file-02.txt\"" + "\r\n" +
                CONTENT_LENGTH + ": " + file2.length() + "\r\n" +
                CONTENT_TYPE + ": text/plain" + "\r\n" +
                CONTENT_TRANSFER_ENCODING + ": binary" + "\r\n" +
                "\r\n" +
                "File 02" + StringUtil.NEWLINE +
                "\r\n" +
                "--" + multipartMixedBoundary + "\r\n" +
                CONTENT_DISPOSITION + ": attachment; filename=\"file-03.txt\"" + "\r\n" +
                CONTENT_LENGTH + ": " + file3.length() + "\r\n" +
                CONTENT_TYPE + ": text/plain" + "\r\n" +
                CONTENT_TRANSFER_ENCODING + ": binary" + "\r\n" +
                "\r\n" +
                "File 03" + StringUtil.NEWLINE +
                "\r\n" +
                "--" + multipartMixedBoundary + "--" + "\r\n" +
                "--" + multipartDataBoundary + "--" + "\r\n";

        assertEquals(expected, content);
    }

    @Test
    public void testMultiFileUploadInMixedModeNoName() throws Exception {
        DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,
                HttpMethod.POST, "http://localhost");

        HttpPostRequestEncoder encoder = new HttpPostRequestEncoder(request, true);
        File file1 = new File(getClass().getResource("/file-01.txt").toURI());
        File file2 = new File(getClass().getResource("/file-02.txt").toURI());
        encoder.addBodyAttribute("foo", "bar");
        encoder.addBodyFileUpload("quux", "", file1, "text/plain", false);
        encoder.addBodyFileUpload("quux", "", file2, "text/plain", false);

        // We have to query the value of these two fields before finalizing
        // the request, which unsets one of them.
        String multipartDataBoundary = encoder.multipartDataBoundary;
        String multipartMixedBoundary = encoder.multipartMixedBoundary;
        String content = getRequestBody(encoder);

        String expected = "--" + multipartDataBoundary + "\r\n" +
                CONTENT_DISPOSITION + ": form-data; name=\"foo\"" + "\r\n" +
                CONTENT_LENGTH + ": 3" + "\r\n" +
                CONTENT_TYPE + ": text/plain; charset=UTF-8" + "\r\n" +
                "\r\n" +
                "bar" + "\r\n" +
                "--" + multipartDataBoundary + "\r\n" +
                CONTENT_DISPOSITION + ": form-data; name=\"quux\"" + "\r\n" +
                CONTENT_TYPE + ": multipart/mixed; boundary=" + multipartMixedBoundary + "\r\n" +
                "\r\n" +
                "--" + multipartMixedBoundary + "\r\n" +
                CONTENT_DISPOSITION + ": attachment\r\n" +
                CONTENT_LENGTH + ": " + file1.length() + "\r\n" +
                CONTENT_TYPE + ": text/plain" + "\r\n" +
                CONTENT_TRANSFER_ENCODING + ": binary" + "\r\n" +
                "\r\n" +
                "File 01" + StringUtil.NEWLINE +
                "\r\n" +
                "--" + multipartMixedBoundary + "\r\n" +
                CONTENT_DISPOSITION + ": attachment\r\n" +
                CONTENT_LENGTH + ": " + file2.length() + "\r\n" +
                CONTENT_TYPE + ": text/plain" + "\r\n" +
                CONTENT_TRANSFER_ENCODING + ": binary" + "\r\n" +
                "\r\n" +
                "File 02" + StringUtil.NEWLINE +
                "\r\n" +
                "--" + multipartMixedBoundary + "--" + "\r\n" +
                "--" + multipartDataBoundary + "--" + "\r\n";

        assertEquals(expected, content);
    }

    @Test
    public void testSingleFileUploadInHtml5Mode() throws Exception {
        DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,
                HttpMethod.POST, "http://localhost");

        DefaultHttpDataFactory factory = new DefaultHttpDataFactory(DefaultHttpDataFactory.MINSIZE);

        HttpPostRequestEncoder encoder = new HttpPostRequestEncoder(factory,
                request, true, CharsetUtil.UTF_8, EncoderMode.HTML5);
        File file1 = new File(getClass().getResource("/file-01.txt").toURI());
        File file2 = new File(getClass().getResource("/file-02.txt").toURI());
        encoder.addBodyAttribute("foo", "bar");
        encoder.addBodyFileUpload("quux", file1, "text/plain", false);
        encoder.addBodyFileUpload("quux", file2, "text/plain", false);

        String multipartDataBoundary = encoder.multipartDataBoundary;
        String content = getRequestBody(encoder);

        String expected = "--" + multipartDataBoundary + "\r\n" +
                CONTENT_DISPOSITION + ": form-data; name=\"foo\"" + "\r\n" +
                CONTENT_LENGTH + ": 3" + "\r\n" +
                CONTENT_TYPE + ": text/plain; charset=UTF-8" + "\r\n" +
                "\r\n" +
                "bar" + "\r\n" +
                "--" + multipartDataBoundary + "\r\n" +
                CONTENT_DISPOSITION + ": form-data; name=\"quux\"; filename=\"file-01.txt\"" + "\r\n" +
                CONTENT_LENGTH + ": " + file1.length() + "\r\n" +
                CONTENT_TYPE + ": text/plain" + "\r\n" +
                CONTENT_TRANSFER_ENCODING + ": binary" + "\r\n" +
                "\r\n" +
                "File 01" + StringUtil.NEWLINE + "\r\n" +
                "--" + multipartDataBoundary + "\r\n" +
                CONTENT_DISPOSITION + ": form-data; name=\"quux\"; filename=\"file-02.txt\"" + "\r\n" +
                CONTENT_LENGTH + ": " + file2.length() + "\r\n" +
                CONTENT_TYPE + ": text/plain" + "\r\n" +
                CONTENT_TRANSFER_ENCODING + ": binary" + "\r\n" +
                "\r\n" +
                "File 02" + StringUtil.NEWLINE +
                "\r\n" +
                "--" + multipartDataBoundary + "--" + "\r\n";

        assertEquals(expected, content);
    }

    @Test
    public void testMultiFileUploadInHtml5Mode() throws Exception {
        DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,
                HttpMethod.POST, "http://localhost");

        DefaultHttpDataFactory factory = new DefaultHttpDataFactory(DefaultHttpDataFactory.MINSIZE);

        HttpPostRequestEncoder encoder = new HttpPostRequestEncoder(factory,
                request, true, CharsetUtil.UTF_8, EncoderMode.HTML5);
        File file1 = new File(getClass().getResource("/file-01.txt").toURI());
        encoder.addBodyAttribute("foo", "bar");
        encoder.addBodyFileUpload("quux", file1, "text/plain", false);

        String multipartDataBoundary = encoder.multipartDataBoundary;
        String content = getRequestBody(encoder);

        String expected = "--" + multipartDataBoundary + "\r\n" +
                CONTENT_DISPOSITION + ": form-data; name=\"foo\"" + "\r\n" +
                CONTENT_LENGTH + ": 3" + "\r\n" +
                CONTENT_TYPE + ": text/plain; charset=UTF-8" + "\r\n" +
                "\r\n" +
                "bar" +
                "\r\n" +
                "--" + multipartDataBoundary + "\r\n" +
                CONTENT_DISPOSITION + ": form-data; name=\"quux\"; filename=\"file-01.txt\"" + "\r\n" +
                CONTENT_LENGTH + ": " + file1.length() + "\r\n" +
                CONTENT_TYPE + ": text/plain" + "\r\n" +
                CONTENT_TRANSFER_ENCODING + ": binary" + "\r\n" +
                "\r\n" +
                "File 01" + StringUtil.NEWLINE +
                "\r\n" +
                "--" + multipartDataBoundary + "--" + "\r\n";

        assertEquals(expected, content);
    }

    @Test
    public void testHttpPostRequestEncoderSlicedBuffer() throws Exception {
        DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,
                HttpMethod.POST, "http://localhost");

        HttpPostRequestEncoder encoder = new HttpPostRequestEncoder(request, true);
        // add Form attribute
        encoder.addBodyAttribute("getform", "POST");
        encoder.addBodyAttribute("info", "first value");
        encoder.addBodyAttribute("secondinfo", "secondvalue a&");
        encoder.addBodyAttribute("thirdinfo", "short text");
        int length = 100000;
        char[] array = new char[length];
        Arrays.fill(array, 'a');
        String longText = new String(array);
        encoder.addBodyAttribute("fourthinfo", longText.substring(0, 7470));
        File file1 = new File(getClass().getResource("/file-01.txt").toURI());
        encoder.addBodyFileUpload("myfile", file1, "application/x-zip-compressed", false);
        encoder.finalizeRequest();
        while (! encoder.isEndOfInput()) {
            HttpContent httpContent = encoder.readChunk((ByteBufAllocator) null);
            ByteBuf content = httpContent.content();
            int refCnt = content.refCnt();
            assertTrue((content.unwrap() == content || content.unwrap() == null) && refCnt == 1 ||
                    content.unwrap() != content && refCnt == 2,
                    "content: " + content + " content.unwrap(): " + content.unwrap() + " refCnt: " + refCnt);
            httpContent.release();
        }
        encoder.cleanFiles();
        encoder.close();
    }

    private static String getRequestBody(HttpPostRequestEncoder encoder) throws Exception {
        encoder.finalizeRequest();

        List<InterfaceHttpData> chunks = encoder.multipartHttpDatas;
        ByteBuf[] buffers = new ByteBuf[chunks.size()];

        for (int i = 0; i < buffers.length; i++) {
            InterfaceHttpData data = chunks.get(i);
            if (data instanceof InternalAttribute) {
                buffers[i] = ((InternalAttribute) data).toByteBuf();
            } else if (data instanceof HttpData) {
                buffers[i] = ((HttpData) data).getByteBuf();
            }
        }

        ByteBuf content = Unpooled.wrappedBuffer(buffers);
        String contentStr = content.toString(CharsetUtil.UTF_8);
        content.release();
        return contentStr;
    }

    @Test
    public void testDataIsMultipleOfChunkSize1() throws Exception {
        DefaultHttpDataFactory factory = new DefaultHttpDataFactory(DefaultHttpDataFactory.MINSIZE);
        DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,
                HttpMethod.POST, "http://localhost");
        HttpPostRequestEncoder encoder = new HttpPostRequestEncoder(factory, request, true,
                HttpConstants.DEFAULT_CHARSET, HttpPostRequestEncoder.EncoderMode.RFC1738);

        MemoryFileUpload first = new MemoryFileUpload("resources", "", "application/json", null,
                CharsetUtil.UTF_8, -1);
        first.setMaxSize(-1);
        first.setContent(new ByteArrayInputStream(new byte[7955]));
        encoder.addBodyHttpData(first);

        MemoryFileUpload second = new MemoryFileUpload("resources2", "", "application/json", null,
                CharsetUtil.UTF_8, -1);
        second.setMaxSize(-1);
        second.setContent(new ByteArrayInputStream(new byte[7928]));
        encoder.addBodyHttpData(second);

        assertNotNull(encoder.finalizeRequest());

        checkNextChunkSize(encoder, 8080);
        checkNextChunkSize(encoder, 8080);

        HttpContent httpContent = encoder.readChunk((ByteBufAllocator) null);
        assertTrue(httpContent instanceof LastHttpContent, "Expected LastHttpContent is not received");
        httpContent.release();

           assertTrue(encoder.isEndOfInput(), "Expected end of input is not receive");
    }

    @Test
    public void testDataIsMultipleOfChunkSize2() throws Exception {
        DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,
                HttpMethod.POST, "http://localhost");
        HttpPostRequestEncoder encoder = new HttpPostRequestEncoder(request, true);
        int length = 7943;
        char[] array = new char[length];
        Arrays.fill(array, 'a');
        String longText = new String(array);
        encoder.addBodyAttribute("foo", longText);

        assertNotNull(encoder.finalizeRequest());

        checkNextChunkSize(encoder, 8080);

        HttpContent httpContent = encoder.readChunk((ByteBufAllocator) null);
        assertTrue(httpContent instanceof LastHttpContent, "Expected LastHttpContent is not received");
        httpContent.release();

        assertTrue(encoder.isEndOfInput(), "Expected end of input is not receive");
    }

    private static void checkNextChunkSize(HttpPostRequestEncoder encoder, int sizeWithoutDelimiter) throws Exception {
        // 16 bytes as HttpPostRequestEncoder uses Long.toHexString(...) to generate a hex-string which will be between
        // 2 and 16 bytes.
        // See https://github.com/netty/netty/blob/4.1/codec-http/src/main/java/io/netty/handler/
        // codec/http/multipart/HttpPostRequestEncoder.java#L291
        int expectedSizeMin = sizeWithoutDelimiter + 2;
        int expectedSizeMax = sizeWithoutDelimiter + 16;

        HttpContent httpContent = encoder.readChunk((ByteBufAllocator) null);

        int readable = httpContent.content().readableBytes();
        boolean expectedSize = readable >= expectedSizeMin && readable <= expectedSizeMax;
        assertTrue(expectedSize, "Chunk size is not in expected range (" + expectedSizeMin + " - "
                + expectedSizeMax + "), was: " + readable);
        httpContent.release();
    }

    @Test
    public void testEncodeChunkedContent() throws Exception {
        HttpRequest req = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/");
        HttpPostRequestEncoder encoder = new HttpPostRequestEncoder(req, false);

        int length = 8077 + 8096;
        char[] array = new char[length];
        Arrays.fill(array, 'a');
        String longText = new String(array);

        encoder.addBodyAttribute("data", longText);
        encoder.addBodyAttribute("moreData", "abcd");

        assertNotNull(encoder.finalizeRequest());

        while (!encoder.isEndOfInput()) {
            encoder.readChunk((ByteBufAllocator) null).release();
        }

        assertTrue(encoder.isEndOfInput());
        encoder.cleanFiles();
    }

    @Test
    public void testEncodeChunkedContentV2() throws Exception {
        final Map<String, String> params = new LinkedHashMap<String, String>();
        params.put("data", largeText("data", 3));
        params.put("empty", "");
        params.put("tiny", "a");
        params.put("large", generateString(chunkSize - 7 - 7 - 1 - 8));
        params.put("huge", largeText("huge", 8));
        params.put("small", "abcd");

        final StringBuilder expectQueryStr = new StringBuilder();
        final HttpRequest req = new DefaultHttpRequest(HTTP_1_1, POST, "/");
        final HttpPostRequestEncoder encoder = new HttpPostRequestEncoder(req, false);
        for (Entry<String, String> entry : params.entrySet()) {
            final String key = entry.getKey();
            final String value = entry.getValue();
            encoder.addBodyAttribute(key, value);
            expectQueryStr.append(key).append('=').append(value).append('&');
        }
        expectQueryStr.setLength(expectQueryStr.length() - 1); //remove last '&'
        assertNotNull(encoder.finalizeRequest());

        final StringBuilder chunks = new StringBuilder();
        while (!encoder.isEndOfInput()) {
            final HttpContent httpContent = encoder.readChunk((ByteBufAllocator) null);
            chunks.append(httpContent.content().toString(UTF_8));
            httpContent.release();
        }

        assertTrue(encoder.isEndOfInput());
        assertEquals(expectQueryStr.toString(), chunks.toString());
        encoder.cleanFiles();
    }

    private String largeText(String key, int multipleChunks) {
        return generateString(chunkSize * multipleChunks - key.length() - 2); // 2 is '=' and '&'
    }

    private String generateString(int length) {
        final char[] array = new char[length];
        Arrays.fill(array, 'a');
        return new String(array);
    }
}
