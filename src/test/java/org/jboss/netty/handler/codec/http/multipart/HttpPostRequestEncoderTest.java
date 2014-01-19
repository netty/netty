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
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.util.CharsetUtil;
import org.jboss.netty.util.internal.StringUtil;
import org.junit.Test;

import java.io.File;
import java.util.List;

import static org.junit.Assert.*;

/** {@link HttpPostRequestEncoder} test case. */
public class HttpPostRequestEncoderTest {

    @Test
    public void testSingleFileUpload() throws Exception {
        DefaultHttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1,
                HttpMethod.POST, "http://localhost");

        HttpPostRequestEncoder encoder = new HttpPostRequestEncoder(request, true);
        File file1 = new File(getClass().getResource("/file-01.txt").toURI());
        encoder.addBodyAttribute("foo", "bar");
        encoder.addBodyFileUpload("quux", file1, "text/plain", false);

        String multipartDataBoundary = encoder.multipartDataBoundary;
        String content = getRequestBody(encoder);

        String expected = "--" + multipartDataBoundary + "\r\n" +
                "Content-Disposition: form-data; name=\"foo\"" + "\r\n" +
                "Content-Type: text/plain; charset=UTF-8" + "\r\n" +
                "\r\n" +
                "bar" +
                "\r\n" +
                "--" + multipartDataBoundary + "\r\n" +
                "Content-Disposition: form-data; name=\"quux\"; filename=\"file-01.txt\"" + "\r\n" +
                "Content-Type: text/plain" + "\r\n" +
                "Content-Transfer-Encoding: binary" + "\r\n" +
                "\r\n" +
                "File 01" + StringUtil.NEWLINE +
                "\r\n" +
                "--" + multipartDataBoundary + "--" + "\r\n";

        assertEquals(expected, content);
    }

    @Test
    public void testMultiFileUploadInMixedMode() throws Exception {
        DefaultHttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1,
                HttpMethod.POST, "http://localhost");

        HttpPostRequestEncoder encoder = new HttpPostRequestEncoder(request, true);
        File file1 = new File(getClass().getResource("/file-01.txt").toURI());
        File file2 = new File(getClass().getResource("/file-02.txt").toURI());
        encoder.addBodyAttribute("foo", "bar");
        encoder.addBodyFileUpload("quux", file1, "text/plain", false);
        encoder.addBodyFileUpload("quux", file2, "text/plain", false);

        // We have to query the value of these two fields before finalizing
        // the request, which unsets one of them.
        String multipartDataBoundary = encoder.multipartDataBoundary;
        String multipartMixedBoundary = encoder.multipartMixedBoundary;
        String content = getRequestBody(encoder);

        String expected = "--" + multipartDataBoundary + "\r\n" +
                "Content-Disposition: form-data; name=\"foo\"" + "\r\n" +
                "Content-Type: text/plain; charset=UTF-8" + "\r\n" +
                "\r\n" +
                "bar" + "\r\n" +
                "--" + multipartDataBoundary + "\r\n" +
                "Content-Disposition: form-data; name=\"quux\"" + "\r\n" +
                "Content-Type: multipart/mixed; boundary=" + multipartMixedBoundary + "\r\n" +
                "\r\n" +
                "--" + multipartMixedBoundary + "\r\n" +
                "Content-Disposition: attachment; filename=\"file-02.txt\"" + "\r\n" +
                "Content-Type: text/plain" + "\r\n" +
                "Content-Transfer-Encoding: binary" + "\r\n" +
                "\r\n" +
                "File 01" + StringUtil.NEWLINE +
                "\r\n" +
                "--" + multipartMixedBoundary + "\r\n" +
                "Content-Disposition: attachment; filename=\"file-02.txt\"" + "\r\n" +
                "Content-Type: text/plain" + "\r\n" +
                "Content-Transfer-Encoding: binary" + "\r\n" +
                "\r\n" +
                "File 02" + StringUtil.NEWLINE +
                "\r\n" +
                "--" + multipartMixedBoundary + "--" + "\r\n" +
                "--" + multipartDataBoundary + "--" + "\r\n";

        assertEquals(expected, content);
    }

    private static String getRequestBody(HttpPostRequestEncoder encoder) throws Exception {
        encoder.finalizeRequest();

        List<InterfaceHttpData> chunks = encoder.multipartHttpDatas;
        ChannelBuffer[] buffers = new ChannelBuffer[chunks.size()];

        for (int i = 0; i < buffers.length; i++) {
            InterfaceHttpData data = chunks.get(i);
            if (data instanceof InternalAttribute) {
                buffers[i] = ((InternalAttribute) data).toChannelBuffer();
            } else if (data instanceof HttpData) {
                buffers[i] = ((HttpData) data).getChannelBuffer();
            }
        }

        return ChannelBuffers.wrappedBuffer(buffers).toString(CharsetUtil.UTF_8);
    }
}
