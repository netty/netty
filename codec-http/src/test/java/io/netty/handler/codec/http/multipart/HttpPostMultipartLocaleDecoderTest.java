/*
 * Copyright 2026 The Netty Project
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
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Decoder-side test that flips {@link Locale#setDefault(Locale)} to Turkish to exercise the
 * {@code Content-Transfer-Encoding} normalization path. Marked {@link Isolated} because the JVM
 * default Locale is process-global state and codec-http runs tests with
 * {@code junit.jupiter.execution.parallel.mode.default = concurrent}.
 */
@Isolated("Mutates Locale.getDefault() which is JVM-global state.")
class HttpPostMultipartLocaleDecoderTest {

    @Test
    void testUppercaseBinaryTransferEncodingUnderTurkishLocale() {
        // Repro: a part declares an uppercase Content-Transfer-Encoding (RFC 2045 mechanism
        // tokens are case-insensitive). On a Turkish-locale JVM the decoder used to call
        // toLowerCase() without a Locale and produced "bınary" (U+0131) which then failed the
        // compare against the lowercase ASCII constants and threw
        // "TransferEncoding Unknown: bınary".
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr", "TR"));
            String content = "\n--861fbeab-cd20-470c-9609-d40a0f704466\r\n" +
                    "content-disposition: form-data; " +
                    "name=\"file\"; filename=\"myfile.ogg\"\r\n" +
                    "content-type: audio/ogg; codecs=opus; charset=UTF8\r\n" +
                    "Content-Transfer-Encoding: BINARY\r\n" +
                    "\r\n\r\n--861fbeab-cd20-470c-9609-d40a0f704466--\r\n";

            FullHttpRequest req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/upload",
                    Unpooled.copiedBuffer(content, CharsetUtil.US_ASCII));
            req.headers().set("content-type", "multipart/form-data; boundary=861fbeab-cd20-470c-9609-d40a0f704466");
            req.headers().set("content-length", content.length());

            HttpPostMultipartRequestDecoder decoder = new HttpPostMultipartRequestDecoder(req);
            FileUpload httpData = (FileUpload) decoder.getBodyHttpDatas("file").get(0);
            assertNotNull(httpData);
            assertEquals("audio/ogg", httpData.getContentType());
            decoder.destroy();
        } finally {
            Locale.setDefault(original);
        }
    }
}
