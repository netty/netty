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
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.net.URLEncoder;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * {@link HttpPostRequestDecoder} test case.
 */
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
        req.headers().add(HttpHeaderNames.CONTENT_TYPE, contentTypeValue);
        req.headers().add(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);

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

            ByteBuf buf = Unpooled.copiedBuffer(body, CharsetUtil.UTF_8);
            decoder.offer(new DefaultHttpContent(buf));
            decoder.offer(new DefaultHttpContent(Unpooled.EMPTY_BUFFER));

            // Validate it's enough chunks to decode upload.
            assertTrue(decoder.hasNext());

            // Decode binary upload.
            MemoryFileUpload upload = (MemoryFileUpload) decoder.next();

            // Validate data has been parsed correctly as it was passed into request.
            assertEquals(data, upload.getString(CharsetUtil.UTF_8),
                    "Invalid decoded data [data=" + data.replaceAll("\r", "\\\\r") + ", upload=" + upload + ']');
            upload.release();
            decoder.destroy();
            buf.release();
        }
    }

    // See https://github.com/netty/netty/issues/1089
    @Test
    public void testFullHttpRequestUpload() throws Exception {
        final String boundary = "dLV9Wyq26L_-JQxk6ferf-RT153LhOO";

        final DefaultFullHttpRequest req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST,
                "http://localhost");

        req.setDecoderResult(DecoderResult.SUCCESS);
        req.headers().add(HttpHeaderNames.CONTENT_TYPE, "multipart/form-data; boundary=" + boundary);
        req.headers().add(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);

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
        decoder.destroy();
        assertTrue(req.release());
    }

    // See https://github.com/netty/netty/issues/2544
    @Test
    public void testMultipartCodecWithCRasEndOfAttribute() throws Exception {
        final String boundary = "dLV9Wyq26L_-JQxk6ferf-RT153LhOO";

        // Force to use memory-based data.
        final DefaultHttpDataFactory inMemoryFactory = new DefaultHttpDataFactory(false);
        // Build test case
        String extradata = "aaaa";
        String[] datas = new String[5];
        for (int i = 0; i < 4; i++) {
            datas[i] = extradata;
            for (int j = 0; j < i; j++) {
                datas[i] += '\r';
            }
        }

        for (int i = 0; i < 4; i++) {
            final DefaultFullHttpRequest req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST,
                    "http://localhost");
            req.setDecoderResult(DecoderResult.SUCCESS);
            req.headers().add(HttpHeaderNames.CONTENT_TYPE, "multipart/form-data; boundary=" + boundary);
            req.headers().add(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
            final String body =
                    "--" + boundary + "\r\n" +
                            "Content-Disposition: form-data; name=\"file" + i + "\"\r\n" +
                            "Content-Type: image/gif\r\n" +
                            "\r\n" +
                            datas[i] + "\r\n" +
                            "--" + boundary + "--\r\n";

            req.content().writeBytes(body.getBytes(CharsetUtil.UTF_8));
            // Create decoder instance to test.
            final HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(inMemoryFactory, req);
            assertFalse(decoder.getBodyHttpDatas().isEmpty());
            // Check correctness: data size
            InterfaceHttpData httpdata = decoder.getBodyHttpData("file" + i);
            assertNotNull(httpdata);
            Attribute attribute = (Attribute) httpdata;
            byte[] datar = attribute.get();
            assertNotNull(datar);
            assertEquals(datas[i].getBytes(CharsetUtil.UTF_8).length, datar.length);

            decoder.destroy();
            assertTrue(req.release());
        }
    }

    // See https://github.com/netty/netty/issues/2542
    @Test
    public void testQuotedBoundary() throws Exception {
        final String boundary = "dLV9Wyq26L_-JQxk6ferf-RT153LhOO";

        final DefaultFullHttpRequest req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST,
                "http://localhost");

        req.setDecoderResult(DecoderResult.SUCCESS);
        req.headers().add(HttpHeaderNames.CONTENT_TYPE, "multipart/form-data; boundary=\"" + boundary + '"');
        req.headers().add(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);

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
        decoder.destroy();
        assertTrue(req.release());
    }

    // See https://github.com/netty/netty/issues/1848
    @Test
    public void testNoZeroOut() throws Exception {
        final String boundary = "E832jQp_Rq2ErFmAduHSR8YlMSm0FCY";

        final DefaultHttpDataFactory aMemFactory = new DefaultHttpDataFactory(false);

        DefaultHttpRequest aRequest = new DefaultHttpRequest(HttpVersion.HTTP_1_1,
                HttpMethod.POST,
                "http://localhost");
        aRequest.headers().set(HttpHeaderNames.CONTENT_TYPE,
                "multipart/form-data; boundary=" + boundary);
        aRequest.headers().set(HttpHeaderNames.TRANSFER_ENCODING,
                HttpHeaderValues.CHUNKED);

        HttpPostRequestDecoder aDecoder = new HttpPostRequestDecoder(aMemFactory, aRequest);

        final String aData = "some data would be here. the data should be long enough that it " +
                "will be longer than the original buffer length of 256 bytes in " +
                "the HttpPostRequestDecoder in order to trigger the issue. Some more " +
                "data just to be on the safe side.";

        final String body =
                "--" + boundary + "\r\n" +
                        "Content-Disposition: form-data; name=\"root\"\r\n" +
                        "Content-Type: text/plain\r\n" +
                        "\r\n" +
                        aData +
                        "\r\n" +
                        "--" + boundary + "--\r\n";

        byte[] aBytes = body.getBytes();

        int split = 125;

        ByteBufAllocator aAlloc = new UnpooledByteBufAllocator(true);
        ByteBuf aSmallBuf = aAlloc.heapBuffer(split, split);
        ByteBuf aLargeBuf = aAlloc.heapBuffer(aBytes.length - split, aBytes.length - split);

        aSmallBuf.writeBytes(aBytes, 0, split);
        aLargeBuf.writeBytes(aBytes, split, aBytes.length - split);

        aDecoder.offer(new DefaultHttpContent(aSmallBuf));
        aDecoder.offer(new DefaultHttpContent(aLargeBuf));

        aDecoder.offer(LastHttpContent.EMPTY_LAST_CONTENT);

        assertTrue(aDecoder.hasNext(), "Should have a piece of data");

        InterfaceHttpData aDecodedData = aDecoder.next();
        assertEquals(InterfaceHttpData.HttpDataType.Attribute, aDecodedData.getHttpDataType());

        Attribute aAttr = (Attribute) aDecodedData;
        assertEquals(aData, aAttr.getValue());

        aDecodedData.release();
        aDecoder.destroy();
        aSmallBuf.release();
        aLargeBuf.release();
    }

    // See https://github.com/netty/netty/issues/2305
    @Test
    public void testChunkCorrect() throws Exception {
        String payload = "town=794649819&town=784444184&town=794649672&town=794657800&town=" +
                "794655734&town=794649377&town=794652136&town=789936338&town=789948986&town=" +
                "789949643&town=786358677&town=794655880&town=786398977&town=789901165&town=" +
                "789913325&town=789903418&town=789903579&town=794645251&town=794694126&town=" +
                "794694831&town=794655274&town=789913656&town=794653956&town=794665634&town=" +
                "789936598&town=789904658&town=789899210&town=799696252&town=794657521&town=" +
                "789904837&town=789961286&town=789958704&town=789948839&town=789933899&town=" +
                "793060398&town=794659180&town=794659365&town=799724096&town=794696332&town=" +
                "789953438&town=786398499&town=794693372&town=789935439&town=794658041&town=" +
                "789917595&town=794655427&town=791930372&town=794652891&town=794656365&town=" +
                "789960339&town=794645586&town=794657688&town=794697211&town=789937427&town=" +
                "789902813&town=789941130&town=794696907&town=789904328&town=789955151&town=" +
                "789911570&town=794655074&town=789939531&town=789935242&town=789903835&town=" +
                "789953800&town=794649962&town=789939841&town=789934819&town=789959672&town=" +
                "794659043&town=794657035&town=794658938&town=794651746&town=794653732&town=" +
                "794653881&town=786397909&town=794695736&town=799724044&town=794695926&town=" +
                "789912270&town=794649030&town=794657946&town=794655370&town=794659660&town=" +
                "794694617&town=799149862&town=789953234&town=789900476&town=794654995&town=" +
                "794671126&town=789908868&town=794652942&town=789955605&town=789901934&town=" +
                "789950015&town=789937922&town=789962576&town=786360170&town=789954264&town=" +
                "789911738&town=789955416&town=799724187&town=789911879&town=794657462&town=" +
                "789912561&town=789913167&town=794655195&town=789938266&town=789952099&town=" +
                "794657160&town=789949414&town=794691293&town=794698153&town=789935636&town=" +
                "789956374&town=789934635&town=789935475&town=789935085&town=794651425&town=" +
                "794654936&town=794655680&town=789908669&town=794652031&town=789951298&town=" +
                "789938382&town=794651503&town=794653330&town=817675037&town=789951623&town=" +
                "789958999&town=789961555&town=794694050&town=794650241&town=794656286&town=" +
                "794692081&town=794660090&town=794665227&town=794665136&town=794669931";
        DefaultHttpRequest defaultHttpRequest =
                new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/");

        HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(defaultHttpRequest);

        int firstChunk = 10;
        int middleChunk = 1024;

        byte[] payload1 = payload.substring(0, firstChunk).getBytes();
        byte[] payload2 = payload.substring(firstChunk, firstChunk + middleChunk).getBytes();
        byte[] payload3 = payload.substring(firstChunk + middleChunk, firstChunk + middleChunk * 2).getBytes();
        byte[] payload4 = payload.substring(firstChunk + middleChunk * 2).getBytes();

        ByteBuf buf1 = Unpooled.directBuffer(payload1.length);
        ByteBuf buf2 = Unpooled.directBuffer(payload2.length);
        ByteBuf buf3 = Unpooled.directBuffer(payload3.length);
        ByteBuf buf4 = Unpooled.directBuffer(payload4.length);

        buf1.writeBytes(payload1);
        buf2.writeBytes(payload2);
        buf3.writeBytes(payload3);
        buf4.writeBytes(payload4);

        decoder.offer(new DefaultHttpContent(buf1));
        decoder.offer(new DefaultHttpContent(buf2));
        decoder.offer(new DefaultHttpContent(buf3));
        decoder.offer(new DefaultLastHttpContent(buf4));

        assertFalse(decoder.getBodyHttpDatas().isEmpty());
        assertEquals(139, decoder.getBodyHttpDatas().size());

        Attribute attr = (Attribute) decoder.getBodyHttpData("town");
        assertEquals("794649819", attr.getValue());

        decoder.destroy();
        buf1.release();
        buf2.release();
        buf3.release();
        buf4.release();
    }

    // See https://github.com/netty/netty/issues/3326
    @Test
    public void testFilenameContainingSemicolon() throws Exception {
        final String boundary = "dLV9Wyq26L_-JQxk6ferf-RT153LhOO";
        final DefaultFullHttpRequest req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST,
                "http://localhost");
        req.headers().add(HttpHeaderNames.CONTENT_TYPE, "multipart/form-data; boundary=" + boundary);
        // Force to use memory-based data.
        final DefaultHttpDataFactory inMemoryFactory = new DefaultHttpDataFactory(false);
        final String data = "asdf";
        final String filename = "tmp;0.txt";
        final String body =
                "--" + boundary + "\r\n" +
                        "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n" +
                        "Content-Type: image/gif\r\n" +
                        "\r\n" +
                        data + "\r\n" +
                        "--" + boundary + "--\r\n";

        req.content().writeBytes(body.getBytes(CharsetUtil.UTF_8.name()));
        // Create decoder instance to test.
        final HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(inMemoryFactory, req);
        assertFalse(decoder.getBodyHttpDatas().isEmpty());
        decoder.destroy();
        assertTrue(req.release());
    }

    @Test
    public void testFilenameContainingSemicolon2() throws Exception {
        final String boundary = "dLV9Wyq26L_-JQxk6ferf-RT153LhOO";
        final DefaultFullHttpRequest req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST,
                "http://localhost");
        req.headers().add(HttpHeaderNames.CONTENT_TYPE, "multipart/form-data; boundary=" + boundary);
        // Force to use memory-based data.
        final DefaultHttpDataFactory inMemoryFactory = new DefaultHttpDataFactory(false);
        final String data = "asdf";
        final String filename = "tmp;0.txt";
        final String body =
                "--" + boundary + "\r\n" +
                        "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n" +
                        "Content-Type: image/gif\r\n" +
                        "\r\n" +
                        data + "\r\n" +
                        "--" + boundary + "--\r\n";

        req.content().writeBytes(body.getBytes(CharsetUtil.UTF_8.name()));
        // Create decoder instance to test.
        final HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(inMemoryFactory, req);
        assertFalse(decoder.getBodyHttpDatas().isEmpty());
        InterfaceHttpData part1 = decoder.getBodyHttpDatas().get(0);
        assertTrue(part1 instanceof FileUpload);
        FileUpload fileUpload = (FileUpload) part1;
        assertEquals("tmp 0.txt", fileUpload.getFilename());
        decoder.destroy();
        assertTrue(req.release());
    }

    @Test
    public void testMultipartRequestWithoutContentTypeBody() {
        final String boundary = "dLV9Wyq26L_-JQxk6ferf-RT153LhOO";

        final DefaultFullHttpRequest req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST,
                "http://localhost");

        req.setDecoderResult(DecoderResult.SUCCESS);
        req.headers().add(HttpHeaderNames.CONTENT_TYPE, "multipart/form-data; boundary=" + boundary);
        req.headers().add(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);

        // Force to use memory-based data.
        final DefaultHttpDataFactory inMemoryFactory = new DefaultHttpDataFactory(false);

        for (String data : Arrays.asList("", "\r", "\r\r", "\r\r\r")) {
            final String body =
                    "--" + boundary + "\r\n" +
                            "Content-Disposition: form-data; name=\"file\"; filename=\"tmp-0.txt\"\r\n" +
                            "\r\n" +
                            data + "\r\n" +
                            "--" + boundary + "--\r\n";

            req.content().writeBytes(body.getBytes(CharsetUtil.UTF_8));
        }
        // Create decoder instance to test without any exception.
        final HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(inMemoryFactory, req);
        assertFalse(decoder.getBodyHttpDatas().isEmpty());
        decoder.destroy();
        assertTrue(req.release());
    }

    @Test
    public void testDecodeOtherMimeHeaderFields() throws Exception {
        final String boundary = "74e78d11b0214bdcbc2f86491eeb4902";
        String filecontent = "123456";

        final String body = "--" + boundary + "\r\n" +
                "Content-Disposition: form-data; name=\"file\"; filename=" + "\"" + "attached.txt" + "\"" +
                "\r\n" +
                "Content-Type: application/octet-stream" + "\r\n" +
                "Content-Encoding: gzip" + "\r\n" +
                "\r\n" +
                filecontent +
                "\r\n" +
                "--" + boundary + "--";

        final DefaultFullHttpRequest req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,
                HttpMethod.POST,
                "http://localhost",
                Unpooled.wrappedBuffer(body.getBytes()));
        req.headers().add(HttpHeaderNames.CONTENT_TYPE, "multipart/form-data; boundary=" + boundary);
        req.headers().add(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
        final DefaultHttpDataFactory inMemoryFactory = new DefaultHttpDataFactory(false);
        final HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(inMemoryFactory, req);
        assertFalse(decoder.getBodyHttpDatas().isEmpty());
        InterfaceHttpData part1 = decoder.getBodyHttpDatas().get(0);
        assertTrue(part1 instanceof FileUpload, "the item should be a FileUpload");
        FileUpload fileUpload = (FileUpload) part1;
        byte[] fileBytes = fileUpload.get();
        assertTrue(filecontent.equals(new String(fileBytes)), "the filecontent should not be decoded");
        decoder.destroy();
        assertTrue(req.release());
    }

    @Test
    public void testMultipartRequestWithFileInvalidCharset() throws Exception {
        final String boundary = "dLV9Wyq26L_-JQxk6ferf-RT153LhOO";
        final DefaultFullHttpRequest req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST,
                "http://localhost");
        req.headers().add(HttpHeaderNames.CONTENT_TYPE, "multipart/form-data; boundary=" + boundary);
        // Force to use memory-based data.
        final DefaultHttpDataFactory inMemoryFactory = new DefaultHttpDataFactory(false);
        final String data = "asdf";
        final String filename = "tmp;0.txt";
        final String body =
                "--" + boundary + "\r\n" +
                        "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n" +
                        "Content-Type: image/gif; charset=ABCD\r\n" +
                        "\r\n" +
                        data + "\r\n" +
                        "--" + boundary + "--\r\n";

        req.content().writeBytes(body.getBytes(CharsetUtil.UTF_8));
        // Create decoder instance to test.
        try {
            new HttpPostRequestDecoder(inMemoryFactory, req);
            fail("Was expecting an ErrorDataDecoderException");
        } catch (HttpPostRequestDecoder.ErrorDataDecoderException e) {
            assertTrue(e.getCause() instanceof UnsupportedCharsetException);
        } finally {
            assertTrue(req.release());
        }
    }

    @Test
    public void testMultipartRequestWithFieldInvalidCharset() throws Exception {
        final String boundary = "dLV9Wyq26L_-JQxk6ferf-RT153LhOO";
        final DefaultFullHttpRequest req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST,
                "http://localhost");
        req.headers().add(HttpHeaderNames.CONTENT_TYPE, "multipart/form-data; boundary=" + boundary);
        // Force to use memory-based data.
        final DefaultHttpDataFactory inMemoryFactory = new DefaultHttpDataFactory(false);
        final String aData = "some data would be here. the data should be long enough that it " +
                "will be longer than the original buffer length of 256 bytes in " +
                "the HttpPostRequestDecoder in order to trigger the issue. Some more " +
                "data just to be on the safe side.";
        final String body =
                "--" + boundary + "\r\n" +
                        "Content-Disposition: form-data; name=\"root\"\r\n" +
                        "Content-Type: text/plain; charset=ABCD\r\n" +
                        "\r\n" +
                        aData +
                        "\r\n" +
                        "--" + boundary + "--\r\n";

        req.content().writeBytes(body.getBytes(CharsetUtil.UTF_8));
        // Create decoder instance to test.
        try {
            new HttpPostRequestDecoder(inMemoryFactory, req);
            fail("Was expecting an ErrorDataDecoderException");
        } catch (HttpPostRequestDecoder.ErrorDataDecoderException e) {
            assertTrue(e.getCause() instanceof UnsupportedCharsetException);
        } finally {
            assertTrue(req.release());
        }
    }

    @Test
    public void testFormEncodeIncorrect() throws Exception {
        LastHttpContent content = new DefaultLastHttpContent(
                Unpooled.copiedBuffer("project=netty&&project=netty", CharsetUtil.US_ASCII));
        DefaultHttpRequest req = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/");
        HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(req);
        try {
            decoder.offer(content);
            fail();
        } catch (HttpPostRequestDecoder.ErrorDataDecoderException e) {
            assertTrue(e.getCause() instanceof IllegalArgumentException);
        } finally {
            content.release();
            decoder.destroy();
        }
    }

    // https://github.com/netty/netty/pull/7265
    @Test
    public void testDecodeContentDispositionFieldParameters() throws Exception {

        final String boundary = "74e78d11b0214bdcbc2f86491eeb4902";

        String encoding = "utf-8";
        String filename = "attached_файл.txt";
        String filenameEncoded = URLEncoder.encode(filename, encoding);

        final String body = "--" + boundary + "\r\n" +
                "Content-Disposition: form-data; name=\"file\"; filename*=" + encoding + "''" + filenameEncoded +
                "\r\n\r\n" +
                "foo\r\n" +
                "\r\n" +
                "--" + boundary + "--";

        final DefaultFullHttpRequest req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,
                HttpMethod.POST,
                "http://localhost",
                Unpooled.wrappedBuffer(body.getBytes()));

        req.headers().add(HttpHeaderNames.CONTENT_TYPE, "multipart/form-data; boundary=" + boundary);
        final DefaultHttpDataFactory inMemoryFactory = new DefaultHttpDataFactory(false);
        final HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(inMemoryFactory, req);
        assertFalse(decoder.getBodyHttpDatas().isEmpty());
        InterfaceHttpData part1 = decoder.getBodyHttpDatas().get(0);
        assertTrue(part1 instanceof FileUpload, "the item should be a FileUpload");
        FileUpload fileUpload = (FileUpload) part1;
        assertEquals(filename, fileUpload.getFilename(), "the filename should be decoded");
        decoder.destroy();
        assertTrue(req.release());
    }

    // https://github.com/netty/netty/pull/7265
    @Test
    public void testDecodeWithLanguageContentDispositionFieldParameters() throws Exception {

        final String boundary = "74e78d11b0214bdcbc2f86491eeb4902";

        String encoding = "utf-8";
        String filename = "attached_файл.txt";
        String language = "anything";
        String filenameEncoded = URLEncoder.encode(filename, encoding);

        final String body = "--" + boundary + "\r\n" +
                "Content-Disposition: form-data; name=\"file\"; filename*=" +
                encoding + "'" + language + "'" + filenameEncoded + "\r\n" +
                "\r\n" +
                "foo\r\n" +
                "\r\n" +
                "--" + boundary + "--";

        final DefaultFullHttpRequest req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,
                HttpMethod.POST,
                "http://localhost",
                Unpooled.wrappedBuffer(body.getBytes()));

        req.headers().add(HttpHeaderNames.CONTENT_TYPE, "multipart/form-data; boundary=" + boundary);
        final DefaultHttpDataFactory inMemoryFactory = new DefaultHttpDataFactory(false);
        final HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(inMemoryFactory, req);
        assertFalse(decoder.getBodyHttpDatas().isEmpty());
        InterfaceHttpData part1 = decoder.getBodyHttpDatas().get(0);
        assertTrue(part1 instanceof FileUpload, "the item should be a FileUpload");
        FileUpload fileUpload = (FileUpload) part1;
        assertEquals(filename, fileUpload.getFilename(), "the filename should be decoded");
        decoder.destroy();
        assertTrue(req.release());
    }

    // https://github.com/netty/netty/pull/7265
    @Test
    public void testDecodeMalformedNotEncodedContentDispositionFieldParameters() throws Exception {

        final String boundary = "74e78d11b0214bdcbc2f86491eeb4902";

        final String body = "--" + boundary + "\r\n" +
                "Content-Disposition: form-data; name=\"file\"; filename*=not-encoded\r\n" +
                "\r\n" +
                "foo\r\n" +
                "\r\n" +
                "--" + boundary + "--";

        final DefaultFullHttpRequest req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,
                HttpMethod.POST,
                "http://localhost",
                Unpooled.wrappedBuffer(body.getBytes()));

        req.headers().add(HttpHeaderNames.CONTENT_TYPE, "multipart/form-data; boundary=" + boundary);

        final DefaultHttpDataFactory inMemoryFactory = new DefaultHttpDataFactory(false);

        try {
            new HttpPostRequestDecoder(inMemoryFactory, req);
            fail("Was expecting an ErrorDataDecoderException");
        } catch (HttpPostRequestDecoder.ErrorDataDecoderException e) {
            assertTrue(e.getCause() instanceof ArrayIndexOutOfBoundsException);
        } finally {
            assertTrue(req.release());
        }
    }

    // https://github.com/netty/netty/pull/7265
    @Test
    public void testDecodeMalformedBadCharsetContentDispositionFieldParameters() throws Exception {

        final String boundary = "74e78d11b0214bdcbc2f86491eeb4902";

        final String body = "--" + boundary + "\r\n" +
                "Content-Disposition: form-data; name=\"file\"; filename*=not-a-charset''filename\r\n" +
                "\r\n" +
                "foo\r\n" +
                "\r\n" +
                "--" + boundary + "--";

        final DefaultFullHttpRequest req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,
                HttpMethod.POST,
                "http://localhost",
                Unpooled.wrappedBuffer(body.getBytes()));

        req.headers().add(HttpHeaderNames.CONTENT_TYPE, "multipart/form-data; boundary=" + boundary);

        final DefaultHttpDataFactory inMemoryFactory = new DefaultHttpDataFactory(false);

        try {
            new HttpPostRequestDecoder(inMemoryFactory, req);
            fail("Was expecting an ErrorDataDecoderException");
        } catch (HttpPostRequestDecoder.ErrorDataDecoderException e) {
            assertTrue(e.getCause() instanceof UnsupportedCharsetException);
        } finally {
            assertTrue(req.release());
        }
    }

    // https://github.com/netty/netty/issues/7620
    @Test
    public void testDecodeMalformedEmptyContentTypeFieldParameters() throws Exception {
        final String boundary = "dLV9Wyq26L_-JQxk6ferf-RT153LhOO";
        final DefaultFullHttpRequest req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST,
                "http://localhost");
        req.headers().add(HttpHeaderNames.CONTENT_TYPE, "multipart/form-data; boundary=" + boundary);
        // Force to use memory-based data.
        final DefaultHttpDataFactory inMemoryFactory = new DefaultHttpDataFactory(false);
        final String data = "asdf";
        final String filename = "tmp-0.txt";
        final String body =
                "--" + boundary + "\r\n" +
                        "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n" +
                        "Content-Type: \r\n" +
                        "\r\n" +
                        data + "\r\n" +
                        "--" + boundary + "--\r\n";

        req.content().writeBytes(body.getBytes(CharsetUtil.UTF_8.name()));
        // Create decoder instance to test.
        final HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(inMemoryFactory, req);
        assertFalse(decoder.getBodyHttpDatas().isEmpty());
        InterfaceHttpData part1 = decoder.getBodyHttpDatas().get(0);
        assertTrue(part1 instanceof FileUpload);
        FileUpload fileUpload = (FileUpload) part1;
        assertEquals("tmp-0.txt", fileUpload.getFilename());
        decoder.destroy();
        assertTrue(req.release());
    }

    // https://github.com/netty/netty/issues/8575
    @Test
    public void testMultipartRequest() throws Exception {
        String BOUNDARY = "01f136d9282f";

        byte[] bodyBytes = ("--" + BOUNDARY + "\n" +
            "Content-Disposition: form-data; name=\"msg_id\"\n" +
            "\n" +
            "15200\n" +
            "--" + BOUNDARY + "\n" +
            "Content-Disposition: form-data; name=\"msg\"\n" +
            "\n" +
            "test message\n" +
            "--" + BOUNDARY + "--").getBytes();
        ByteBuf byteBuf = Unpooled.directBuffer(bodyBytes.length);
        byteBuf.writeBytes(bodyBytes);

        FullHttpRequest req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_0, HttpMethod.POST, "/up", byteBuf);
        req.headers().add(HttpHeaderNames.CONTENT_TYPE, "multipart/form-data; boundary=" + BOUNDARY);

        HttpPostRequestDecoder decoder =
                new HttpPostRequestDecoder(new DefaultHttpDataFactory(DefaultHttpDataFactory.MINSIZE),
                        req,
                        CharsetUtil.UTF_8);

        assertTrue(decoder.isMultipart());
        assertFalse(decoder.getBodyHttpDatas().isEmpty());
        assertEquals(2, decoder.getBodyHttpDatas().size());

        Attribute attrMsg = (Attribute) decoder.getBodyHttpData("msg");
        assertTrue(attrMsg.getByteBuf().isDirect());
        assertEquals("test message", attrMsg.getValue());
        Attribute attrMsgId = (Attribute) decoder.getBodyHttpData("msg_id");
        assertTrue(attrMsgId.getByteBuf().isDirect());
        assertEquals("15200", attrMsgId.getValue());

        decoder.destroy();
        assertTrue(req.release());
    }

    @Test
    public void testNotLeak() {
        final FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/",
                Unpooled.copiedBuffer("a=1&&b=2", CharsetUtil.US_ASCII));
        try {
            assertThrows(HttpPostRequestDecoder.ErrorDataDecoderException.class, new Executable() {
                @Override
                public void execute() {
                    new HttpPostStandardRequestDecoder(request).destroy();
                }
            });
        } finally {
            assertTrue(request.release());
        }
    }

    @Test
    public void testNotLeakDirectBufferWhenWrapIllegalArgumentException() {
        assertThrows(HttpPostRequestDecoder.ErrorDataDecoderException.class, new Executable() {
            @Override
            public void execute() {
                testNotLeakWhenWrapIllegalArgumentException(Unpooled.directBuffer());
            }
        });
    }

    @Test
    public void testNotLeakHeapBufferWhenWrapIllegalArgumentException() {
        assertThrows(HttpPostRequestDecoder.ErrorDataDecoderException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                testNotLeakWhenWrapIllegalArgumentException(Unpooled.buffer());
            }
        });
    }

    private static void testNotLeakWhenWrapIllegalArgumentException(ByteBuf buf) {
        buf.writeCharSequence("a=b&foo=%22bar%22&==", CharsetUtil.US_ASCII);
        FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/", buf);
        try {
            new HttpPostStandardRequestDecoder(request).destroy();
        } finally {
            assertTrue(request.release());
        }
    }

    @Test
    public void testMultipartFormDataContentType() {
        HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/");
        assertFalse(HttpPostRequestDecoder.isMultipart(request));

        String multipartDataValue = HttpHeaderValues.MULTIPART_FORM_DATA + ";" + "boundary=gc0p4Jq0M2Yt08jU534c0p";
        request.headers().set(HttpHeaderNames.CONTENT_TYPE, ";" + multipartDataValue);
        assertFalse(HttpPostRequestDecoder.isMultipart(request));

        request.headers().set(HttpHeaderNames.CONTENT_TYPE, multipartDataValue);
        assertTrue(HttpPostRequestDecoder.isMultipart(request));
    }

    // see https://github.com/netty/netty/issues/10087
    @Test
    public void testDecodeWithLanguageContentDispositionFieldParametersForFix() throws Exception {

        final String boundary = "952178786863262625034234";

        String encoding = "UTF-8";
        String filename = "测试test.txt";
        String filenameEncoded = URLEncoder.encode(filename, encoding);

        final String body = "--" + boundary + "\r\n" +
                "Content-Disposition: form-data; name=\"file\"; filename*=\"" +
                encoding + "''" + filenameEncoded + "\"\r\n" +
                "\r\n" +
                "foo\r\n" +
                "\r\n" +
                "--" + boundary + "--";

        final DefaultFullHttpRequest req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,
                HttpMethod.POST,
                "http://localhost",
                Unpooled.wrappedBuffer(body.getBytes()));

        req.headers().add(HttpHeaderNames.CONTENT_TYPE, "multipart/form-data; boundary=" + boundary);
        final DefaultHttpDataFactory inMemoryFactory = new DefaultHttpDataFactory(false);
        final HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(inMemoryFactory, req);
        assertFalse(decoder.getBodyHttpDatas().isEmpty());
        InterfaceHttpData part1 = decoder.getBodyHttpDatas().get(0);
        assertTrue(part1 instanceof FileUpload, "the item should be a FileUpload");
        FileUpload fileUpload = (FileUpload) part1;
        assertEquals(filename, fileUpload.getFilename(), "the filename should be decoded");

        decoder.destroy();
        assertTrue(req.release());
    }

    @Test
    public void testDecodeFullHttpRequestWithUrlEncodedBody() throws Exception {
        byte[] bodyBytes = "foo=bar&a=b&empty=&city=%3c%22new%22%20york%20city%3e&other_city=los+angeles".getBytes();
        ByteBuf content = Unpooled.directBuffer(bodyBytes.length);
        content.writeBytes(bodyBytes);

        FullHttpRequest req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/", content);
        HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(req);
        assertFalse(decoder.getBodyHttpDatas().isEmpty());

        assertFalse(decoder.getBodyHttpDatas().isEmpty());
        assertEquals(5, decoder.getBodyHttpDatas().size());

        Attribute attr = (Attribute) decoder.getBodyHttpData("foo");
        assertTrue(attr.getByteBuf().isDirect());
        assertEquals("bar", attr.getValue());

        attr = (Attribute) decoder.getBodyHttpData("a");
        assertTrue(attr.getByteBuf().isDirect());
        assertEquals("b", attr.getValue());

        attr = (Attribute) decoder.getBodyHttpData("empty");
        assertTrue(attr.getByteBuf().isDirect());
        assertEquals("", attr.getValue());

        attr = (Attribute) decoder.getBodyHttpData("city");
        assertTrue(attr.getByteBuf().isDirect());
        assertEquals("<\"new\" york city>", attr.getValue());

        attr = (Attribute) decoder.getBodyHttpData("other_city");
        assertTrue(attr.getByteBuf().isDirect());
        assertEquals("los angeles", attr.getValue());

        decoder.destroy();
        assertTrue(req.release());
    }

    @Test
    public void testDecodeFullHttpRequestWithUrlEncodedBodyWithBrokenHexByte0() {
        byte[] bodyBytes = "foo=bar&a=b&empty=%&city=paris".getBytes();
        ByteBuf content = Unpooled.directBuffer(bodyBytes.length);
        content.writeBytes(bodyBytes);

        FullHttpRequest req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/", content);
        try {
            new HttpPostRequestDecoder(req);
            fail("Was expecting an ErrorDataDecoderException");
        } catch (HttpPostRequestDecoder.ErrorDataDecoderException e) {
            assertEquals("Invalid hex byte at index '0' in string: '%'", e.getMessage());
        } finally {
            assertTrue(req.release());
        }
    }

    @Test
    public void testDecodeFullHttpRequestWithUrlEncodedBodyWithBrokenHexByte1() {
        byte[] bodyBytes = "foo=bar&a=b&empty=%2&city=london".getBytes();
        ByteBuf content = Unpooled.directBuffer(bodyBytes.length);
        content.writeBytes(bodyBytes);

        FullHttpRequest req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/", content);
        try {
            new HttpPostRequestDecoder(req);
            fail("Was expecting an ErrorDataDecoderException");
        } catch (HttpPostRequestDecoder.ErrorDataDecoderException e) {
            assertEquals("Invalid hex byte at index '0' in string: '%2'", e.getMessage());
        } finally {
            assertTrue(req.release());
        }
    }

    @Test
    public void testDecodeFullHttpRequestWithUrlEncodedBodyWithInvalidHexNibbleHi() {
        byte[] bodyBytes = "foo=bar&a=b&empty=%Zc&city=london".getBytes();
        ByteBuf content = Unpooled.directBuffer(bodyBytes.length);
        content.writeBytes(bodyBytes);

        FullHttpRequest req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/", content);
        try {
            new HttpPostRequestDecoder(req);
            fail("Was expecting an ErrorDataDecoderException");
        } catch (HttpPostRequestDecoder.ErrorDataDecoderException e) {
            assertEquals("Invalid hex byte at index '0' in string: '%Zc'", e.getMessage());
        } finally {
            assertTrue(req.release());
        }
    }

    @Test
    public void testDecodeFullHttpRequestWithUrlEncodedBodyWithInvalidHexNibbleLo() {
        byte[] bodyBytes = "foo=bar&a=b&empty=%2g&city=london".getBytes();
        ByteBuf content = Unpooled.directBuffer(bodyBytes.length);
        content.writeBytes(bodyBytes);

        FullHttpRequest req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/", content);
        try {
            new HttpPostRequestDecoder(req);
            fail("Was expecting an ErrorDataDecoderException");
        } catch (HttpPostRequestDecoder.ErrorDataDecoderException e) {
            assertEquals("Invalid hex byte at index '0' in string: '%2g'", e.getMessage());
        } finally {
            assertTrue(req.release());
        }
    }

    @Test
    public void testDecodeMultipartRequest() {
        byte[] bodyBytes = ("--be38b42a9ad2713f\n" +
                "content-disposition: form-data; name=\"title\"\n" +
                "content-length: 10\n" +
                "content-type: text/plain; charset=UTF-8\n" +
                "\n" +
                "bar-stream\n" +
                "--be38b42a9ad2713f\n" +
                "content-disposition: form-data; name=\"data\"; filename=\"data.json\"\n" +
                "content-length: 16\n" +
                "content-type: application/json; charset=UTF-8\n" +
                "\n" +
                "{\"title\":\"Test\"}\n" +
                "--be38b42a9ad2713f--").getBytes();
        ByteBuf content = Unpooled.directBuffer(bodyBytes.length);
        content.writeBytes(bodyBytes);
        FullHttpRequest req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/", content);
        req.headers().add("Content-Type", "multipart/form-data;boundary=be38b42a9ad2713f");

        try {
            HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(new DefaultHttpDataFactory(false), req);
            assertEquals(2, decoder.getBodyHttpDatas().size());
            InterfaceHttpData data = decoder.getBodyHttpData("title");
            assertTrue(data instanceof MemoryAttribute);
            assertEquals("bar-stream", ((MemoryAttribute) data).getString());
            assertTrue(data.release());
            data = decoder.getBodyHttpData("data");
            assertTrue(data instanceof MemoryFileUpload);
            assertEquals("{\"title\":\"Test\"}", ((MemoryFileUpload) data).getString());
            assertTrue(data.release());
            decoder.destroy();
        } catch (HttpPostRequestDecoder.ErrorDataDecoderException e) {
            fail("Was not expecting an exception");
        } finally {
            assertTrue(req.release());
        }
    }
}
