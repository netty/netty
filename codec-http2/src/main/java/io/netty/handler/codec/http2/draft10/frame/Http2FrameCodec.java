/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.netty.handler.codec.http2.draft10.frame;

import io.netty.channel.ChannelHandlerAppender;
import io.netty.handler.codec.http2.draft10.frame.decoder.Http2FrameDecoder;
import io.netty.handler.codec.http2.draft10.frame.decoder.Http2FrameUnmarshaller;
import io.netty.handler.codec.http2.draft10.frame.encoder.Http2FrameEncoder;
import io.netty.handler.codec.http2.draft10.frame.encoder.Http2FrameMarshaller;

/**
 * A combination of {@link Http2FrameEncoder} and {@link Http2FrameDecoder}.
 */
public class Http2FrameCodec extends ChannelHandlerAppender {

    public Http2FrameCodec(Http2FrameMarshaller frameMarshaller,
                           Http2FrameUnmarshaller frameUnmarshaller) {
        super(new Http2FrameEncoder(frameMarshaller), new Http2FrameDecoder(frameUnmarshaller));
    }

    public Http2FrameCodec() {
        super(new Http2FrameEncoder(), new Http2FrameDecoder());
    }
}
