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

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.*;
import org.jboss.netty.util.CharsetUtil;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * {@link EventedHttpPostRequestDecoder} test cases.
 */
public class EventedHttpPostRequestDecoderTest {

    @Test
    public void testProcessRequest() throws Exception {

        final String boundary = "dLV9Wyq26L_-JQxk6ferf-RT153LhOO";
        String fileContents = "this are the file contents for the file being uploaded";

        // Force to use memory-based data.
        final DefaultHttpDataFactory inMemoryFactory = new DefaultHttpDataFactory(false);

        Map<String,String> parameters = new HashMap<String, String>();
        parameters.put("name", "netty");
        parameters.put("project", "netty-http");

        StringBuilder builder = new StringBuilder();

        for ( Map.Entry<String,String> entry : parameters.entrySet() ) {
            builder.append( "--" + boundary + "\r\n" +
                    String.format("Content-Disposition: form-data; name=\"%s\"\r\n", entry.getKey()) +
                    "\r\n" +
                    entry.getValue() + "\r\n" );
        }

        builder.append("--" + boundary + "\r\n" +
                "Content-Disposition: form-data; name=\"file\"; filename=\"tmp-0.txt\"\r\n" +
                "Content-Type: image/gif\r\n" +
                "\r\n" +
                fileContents + "\r\n" +
                "--" + boundary + "--\r\n");

        byte[] body = builder.toString().getBytes( CharsetUtil.UTF_8 );

        final DefaultHttpRequest req = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "http://localhost");

        req.setContent(ChannelBuffers.EMPTY_BUFFER);
        req.setHeader(HttpHeaders.Names.CONTENT_TYPE, "multipart/form-data; boundary=" + boundary);
        req.setHeader(HttpHeaders.Names.TRANSFER_ENCODING, HttpHeaders.Values.CHUNKED);
        req.setHeader(HttpHeaders.Names.CONTENT_LENGTH, body.length );
        req.setChunked(true);

        final EventedHttpPostRequestDecoder decoder = new EventedHttpPostRequestDecoder(inMemoryFactory, req);

        TestListener listener = new TestListener();

        decoder.addListener(listener);

        decoder.offer(new DefaultHttpChunk(ChannelBuffers.wrappedBuffer(body)));
        decoder.offer(new DefaultHttpChunk(ChannelBuffers.EMPTY_BUFFER));

        Assert.assertTrue(listener.started);
        Assert.assertTrue(listener.finished);
        Assert.assertTrue(listener.bufferReceived);
        Assert.assertTrue(listener.uploadStarted);
        Assert.assertEquals(parameters, listener.parameters);

        Assert.assertEquals( fileContents, new String(listener.upload.get(), CharsetUtil.UTF_8) );

    }

    static class TestListener implements HttpPostRequestListener {

        boolean started;
        boolean finished;
        boolean bufferReceived;
        Map<String,String> parameters = new HashMap<String, String>();
        boolean uploadStarted;
        FileUpload upload;

        public void requestStarted() {
            started = true;
        }

        public void requestFinished() {
            finished = true;
        }

        public void httpDataReceived(InterfaceHttpData data) {

            Attribute attribute = (Attribute) data;

            try {
                parameters.put( data.getName(), attribute.getValue() );
            } catch ( IOException e) {
                throw new RuntimeException(e);
            }

        }

        public void fileUploadStarted(FileUpload fileUpload) {
            uploadStarted = true;
        }

        public void fileUploadFinished(FileUpload fileUpload) {
            upload = fileUpload;
        }

        public void fileUploadChunkReceived(ChannelBuffer buffer) {
            bufferReceived = true;
        }
    }

}
