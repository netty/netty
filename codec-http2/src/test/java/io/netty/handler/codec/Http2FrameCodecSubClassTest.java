/*
 * Copyright 2025 The Netty Project
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

package io.netty.handler.codec;


import io.netty.handler.codec.http2.DefaultHttp2Connection;
import io.netty.handler.codec.http2.DefaultHttp2ConnectionDecoder;
import io.netty.handler.codec.http2.DefaultHttp2ConnectionEncoder;
import io.netty.handler.codec.http2.DefaultHttp2FrameReader;
import io.netty.handler.codec.http2.DefaultHttp2FrameWriter;
import io.netty.handler.codec.http2.Http2Connection;
import io.netty.handler.codec.http2.Http2ConnectionDecoder;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2FrameCodec;
import io.netty.handler.codec.http2.Http2Settings;
import org.junit.jupiter.api.Test;

public class Http2FrameCodecSubClassTest {

    private static class MyHttp2FrameCodec extends Http2FrameCodec {
        MyHttp2FrameCodec(Http2ConnectionEncoder encoder, Http2ConnectionDecoder decoder,
                                 Http2Settings initialSettings, boolean decoupleCloseAndGoAway, boolean flushPreface) {
            super(encoder, decoder, initialSettings, decoupleCloseAndGoAway, flushPreface);
        }
    }

    @Test
    public void testCompiles() {
        Http2Connection conn = new DefaultHttp2Connection(true);
        Http2ConnectionEncoder enc = new DefaultHttp2ConnectionEncoder(conn, new DefaultHttp2FrameWriter());
        Http2ConnectionDecoder dec = new DefaultHttp2ConnectionDecoder(conn, enc, new DefaultHttp2FrameReader());
        new MyHttp2FrameCodec(enc, dec, new Http2Settings(), false, true);
    }
}
