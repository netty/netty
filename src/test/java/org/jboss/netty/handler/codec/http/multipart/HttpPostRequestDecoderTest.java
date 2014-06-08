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
package org.jboss.netty.handler.codec.http.multipart;

import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.DefaultHttpChunk;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.util.CharsetUtil;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

/** {@link HttpPostRequestDecoder} test case. */
public class HttpPostRequestDecoderTest {
    @Test
    public void testBinaryStreamUpload() throws Exception {
        final String boundary = "dLV9Wyq26L_-JQxk6ferf-RT153LhOO";

        final DefaultHttpRequest req = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "http://localhost");

        req.setContent(ChannelBuffers.EMPTY_BUFFER);
        req.headers().set(HttpHeaders.Names.CONTENT_TYPE, "multipart/form-data; boundary=" + boundary);
        req.headers().set(HttpHeaders.Names.TRANSFER_ENCODING, HttpHeaders.Values.CHUNKED);
        req.setChunked(true);

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

            decoder.offer(new DefaultHttpChunk(ChannelBuffers.copiedBuffer(body, CharsetUtil.UTF_8)));
            decoder.offer(new DefaultHttpChunk(ChannelBuffers.EMPTY_BUFFER));

            // Validate it's enough chunks to decode upload.
            assertTrue(decoder.hasNext());

            // Decode binary upload.
            MemoryFileUpload upload = (MemoryFileUpload) decoder.next();

            // Validate data has been parsed correctly as it was passed into request.
            assertArrayEquals("Invalid decoded data [data=" + data + "upload=" + upload + ']',
                data.getBytes(), upload.get());
        }
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
        defaultHttpRequest.setChunked(true);

        HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(defaultHttpRequest);

        int firstChunk = 10;
        int middleChunk = 1024;

        HttpChunk part1 = new DefaultHttpChunk(ChannelBuffers.wrappedBuffer(
                payload.substring(0, firstChunk).getBytes()));
        HttpChunk part2 = new DefaultHttpChunk(ChannelBuffers.wrappedBuffer(
                payload.substring(firstChunk, firstChunk + middleChunk).getBytes()));
        HttpChunk part3 = new DefaultHttpChunk(ChannelBuffers.wrappedBuffer(
                payload.substring(firstChunk + middleChunk, firstChunk + middleChunk * 2).getBytes()));
        HttpChunk part4 = new DefaultHttpChunk(ChannelBuffers.wrappedBuffer(
                payload.substring(firstChunk + middleChunk * 2).getBytes()));

        decoder.offer(part1);
        decoder.offer(part2);
        decoder.offer(part3);
        decoder.offer(part4);
        decoder.offer(HttpChunk.LAST_CHUNK);
    }
}
