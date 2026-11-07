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
package io.netty.handler.codec.http2;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class Http2FrameCodecBuilderTest {

    @Test
    public void customHeadersEncoderIsUsed() {
        DefaultHttp2HeadersEncoder headersEncoder = new DefaultHttp2HeadersEncoder(
                Http2HeadersEncoder.NEVER_SENSITIVE, false, 128, Integer.MAX_VALUE);
        Http2FrameCodec codec = Http2FrameCodecBuilder.forClient().headersEncoder(headersEncoder).build();
        try {
            assertSame(headersEncoder, codec.encoder().frameWriter().configuration().headersConfiguration());
        } finally {
            codec.encoder().close();
        }
    }

    @Test
    public void customHeadersEncoderCannotBeCombinedWithDefaultEncoderOptions() {
        DefaultHttp2HeadersEncoder headersEncoder = new DefaultHttp2HeadersEncoder();
        try {
            assertThrows(IllegalStateException.class, () -> Http2FrameCodecBuilder.forClient()
                    .headerSensitivityDetector(Http2HeadersEncoder.NEVER_SENSITIVE).headersEncoder(headersEncoder));
            assertThrows(IllegalStateException.class, () -> Http2FrameCodecBuilder.forClient()
                    .headersEncoder(headersEncoder).encoderIgnoreMaxHeaderListSize(true));
        } finally {
            headersEncoder.close();
        }
    }
}
