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

package io.netty.handler.codec.http2.draft10.frame.encoder;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.http2.draft10.frame.Http2Frame;

/**
 * Encodes {@link Http2Frame} objects and writes them to an output {@link ByteBuf}. The set of frame
 * types that is handled by this encoder is given by the {@link Http2FrameMarshaller}. By default,
 * the {@link Http2StandardFrameMarshaller} is used.
 *
 * @see Http2StandardFrameMarshaller
 */
public class Http2FrameEncoder extends MessageToByteEncoder<Http2Frame> {

    private final Http2FrameMarshaller frameMarshaller;

    public Http2FrameEncoder() {
        this(new Http2StandardFrameMarshaller());
    }

    public Http2FrameEncoder(Http2FrameMarshaller frameMarshaller) {
        this.frameMarshaller = frameMarshaller;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, Http2Frame frame, ByteBuf out) throws Exception {
        try {
            frameMarshaller.marshall(frame, out, ctx.alloc());
        } catch (Throwable t) {
            ctx.fireExceptionCaught(t);
        }
    }
}
