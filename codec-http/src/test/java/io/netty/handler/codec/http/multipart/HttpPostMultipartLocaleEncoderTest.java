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

import io.netty.handler.codec.http.DefaultFullHttpRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.util.List;
import java.util.Locale;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpMethod.POST;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Encoder-side test that flips {@link Locale#setDefault(Locale)} to Turkish to exercise the
 * {@code Content-Type} de-duplication path inside
 * {@link HttpPostRequestEncoder#finalizeRequest()}. Marked {@link Isolated} because the JVM
 * default Locale is process-global state and codec-http runs tests with
 * {@code junit.jupiter.execution.parallel.mode.default = concurrent}.
 */
@Isolated("Mutates Locale.getDefault() which is JVM-global state.")
class HttpPostMultipartLocaleEncoderTest {

    @Test
    void testFinalizeRemovesPreexistingMultipartContentTypeUnderTurkishLocale() throws Exception {
        // Repro: a caller pre-sets `Content-Type: MULTIPART/form-data` (uppercase, RFC-allowed,
        // case-insensitive). When the JVM default Locale is Turkish, the encoder's
        // contentType.toLowerCase() used to map 'I' -> 'ı' (U+0131), the resulting
        // "multıpart/form-data" missed the prefix check, and the original mixed-case header was
        // left alongside the new multipart Content-Type the encoder is about to set - producing
        // two Content-Type headers on the request.
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr", "TR"));
            DefaultFullHttpRequest request = new DefaultFullHttpRequest(HTTP_1_1, POST, "http://localhost");
            request.headers().add(CONTENT_TYPE, "MULTIPART/form-data; boundary=preexisting");
            HttpPostRequestEncoder encoder = new HttpPostRequestEncoder(request, true);
            encoder.finalizeRequest();
            List<String> contentTypes = request.headers().getAll(CONTENT_TYPE);
            assertEquals(1, contentTypes.size(),
                    "encoder must drop the pre-existing multipart Content-Type regardless of JVM locale");
            assertTrue(contentTypes.get(0).startsWith("multipart/form-data"),
                    "the surviving Content-Type must be the encoder's freshly-built multipart header");
            request.release();
        } finally {
            Locale.setDefault(original);
        }
    }
}
