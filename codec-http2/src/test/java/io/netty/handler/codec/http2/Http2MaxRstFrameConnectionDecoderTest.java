/*
 * Copyright 2023 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec.http2;

public class Http2MaxRstFrameConnectionDecoderTest extends AbstractDecoratingHttp2ConnectionDecoderTest {

    @Override
    protected DecoratingHttp2ConnectionDecoder newDecoder(Http2ConnectionDecoder decoder) {
        return new Http2MaxRstFrameDecoder(decoder, 200, 30);
    }

    @Override
    protected Class<? extends Http2FrameListener> delegatingFrameListenerType() {
        return Http2MaxRstFrameListener.class;
    }
}
