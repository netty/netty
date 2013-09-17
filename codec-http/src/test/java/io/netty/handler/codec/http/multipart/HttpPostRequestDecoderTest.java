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
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
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
    
    // See https://github.com/netty/netty/issues/1848
    @Test
    public void testNoZeroOut() throws Exception {
        final String boundary = "E832jQp_Rq2ErFmAduHSR8YlMSm0FCY";

        final DefaultHttpDataFactory aMemFactory = new DefaultHttpDataFactory(false);

        DefaultHttpRequest aRequest = new DefaultHttpRequest(HttpVersion.HTTP_1_1, 
                                                             HttpMethod.POST, 
                                                             "http://localhost");
        aRequest.headers().set(HttpHeaders.Names.CONTENT_TYPE,
                               "multipart/form-data; boundary=" + boundary);
        aRequest.headers().set(HttpHeaders.Names.TRANSFER_ENCODING, 
                               HttpHeaders.Values.CHUNKED);

        HttpPostRequestDecoder aDecoder = new HttpPostRequestDecoder(aMemFactory, aRequest);

        final String aData = "some data would be here. the data should be long enough that it " +
                             "will be longer than the original buffer length of 256 bytes in "+
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

        assertTrue("Should have a piece of data", aDecoder.hasNext());

	InterfaceHttpData aDecodedData = aDecoder.next();

        assertEquals(InterfaceHttpData.HttpDataType.Attribute, aDecodedData.getHttpDataType());

        Attribute aAttr = (Attribute)aDecodedData;

        assertEquals(aData, aAttr.getValue());
    }
}
